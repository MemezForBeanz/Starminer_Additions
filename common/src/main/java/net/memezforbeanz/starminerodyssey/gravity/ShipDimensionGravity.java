package net.memezforbeanz.starminerodyssey.gravity;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Keeps VS2's per-dimension ship gravity in line with the planet mods, so ships in space float.
 * VS2 defaults to -10; Starlance copies Cosmic Horizons' gravity only at level load (before Cosmos
 * writes it on a fresh world) and never covers Ad Astra. Re-checked periodically, pushed on change.
 * Ad Astra is read reflectively; Cosmic Horizons (Forge-only) is supplied by the Forge module.
 * Only call {@link #register} with Valkyrien Skies loaded.
 */
public final class ShipDimensionGravity {

    private static final int CHECK_INTERVAL = 100;
    /** VS2's gravity for 1.0 (Earth-normal), matching its default and Starlance. */
    private static final double EARTH_SHIP_GRAVITY = -10.0;

    private static final Map<ResourceKey<Level>, Double> APPLIED = new HashMap<>();

    @Nullable
    private static AdAstra adAstra;
    /** Extra planet gravity source (Cosmic Horizons on Forge): Earth-relative gravity, or null. */
    @Nullable
    private static Function<ServerLevel, Double> extraSource;

    private ShipDimensionGravity() {
    }

    /** @param planetSource another planet mod's gravity for a level (null if none or not loaded) */
    public static void register(@Nullable Function<ServerLevel, Double> planetSource) {
        adAstra = Platform.isModLoaded("ad_astra") ? AdAstra.create() : null;
        extraSource = planetSource;
        if (adAstra == null && extraSource == null) {
            return;
        }
        TickEvent.SERVER_LEVEL_POST.register(ShipDimensionGravity::onLevelTick);
        LifecycleEvent.SERVER_STOPPED.register(server -> APPLIED.clear());
    }

    private static void onLevelTick(ServerLevel level) {
        // Offset by dimension so all levels don't check on the same tick
        if ((level.getGameTime() + level.dimension().hashCode()) % CHECK_INTERVAL != 0
                && APPLIED.containsKey(level.dimension())) {
            return;
        }
        Double gravity = planetGravity(level);
        if (gravity == null || gravity.equals(APPLIED.get(level.dimension()))) {
            return;
        }
        try {
            // Null sea level / atmosphere: VS leaves the aerodynamics (e.g. Starlance's) as they are
            VSGameUtilsKt.getShipObjectWorld(level).updateDimension(VSGameUtilsKt.getDimensionId(level),
                    new Vector3d(0, EARTH_SHIP_GRAVITY * gravity, 0), null, null);
            APPLIED.put(level.dimension(), gravity);
            StarminerAdditions.LOGGER.info("Ship gravity for {} set to {} g", level.dimension().location(), gravity);
        } catch (Exception e) {
            // Dimension not registered with VS yet; try again next check
            StarminerAdditions.LOGGER.debug("Couldn't set ship gravity for {}", level.dimension().location(), e);
        }
    }

    /** Gravity relative to Earth, or null if no planet mod defines this dimension. */
    @Nullable
    private static Double planetGravity(ServerLevel level) {
        if (adAstra != null) {
            Double g = adAstra.gravity(level.dimension());
            if (g != null) {
                return g;
            }
        }
        return extraSource != null ? extraSource.apply(level) : null;
    }

    private record AdAstra(Object planetApi, Method getPlanet, Object gravityApi, Method getGravity) {
        @Nullable
        static AdAstra create() {
            try {
                Class<?> planets = Class.forName("earth.terrarium.adastra.api.planets.PlanetApi");
                Class<?> gravity = Class.forName("earth.terrarium.adastra.api.systems.GravityApi");
                return new AdAstra(planets.getField("API").get(null), planets.getMethod("getPlanet", ResourceKey.class),
                        gravity.getField("API").get(null), gravity.getMethod("getGravity", ResourceKey.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                StarminerAdditions.LOGGER.warn("Ad Astra API not found; its ship gravity won't be synced", e);
                return null;
            }
        }

        @Nullable
        Double gravity(ResourceKey<Level> dimension) {
            try {
                if (getPlanet.invoke(planetApi, dimension) == null) {
                    return null;
                }
                return (double) (float) getGravity.invoke(gravityApi, dimension);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
