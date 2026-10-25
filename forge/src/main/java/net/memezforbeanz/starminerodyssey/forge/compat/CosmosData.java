package net.memezforbeanz.starminerodyssey.forge.compat;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflective read access to Cosmic Horizons' world variables (gravity per dimension, and
 * the planets placed in its space dimensions), so Cosmos isn't a build dependency.
 */
public final class CosmosData {

    @Nullable
    private static final CosmosData INSTANCE = ModList.get().isLoaded("cosmos") ? create() : null;

    private final Method getWorldVariables;
    private final Field gravityData;
    private final Field collisionData;

    private CosmosData(Method getWorldVariables, Field gravityData, Field collisionData) {
        this.getWorldVariables = getWorldVariables;
        this.gravityData = gravityData;
        this.collisionData = collisionData;
    }

    @Nullable
    public static CosmosData get() {
        return INSTANCE;
    }

    @Nullable
    private static CosmosData create() {
        try {
            Class<?> vars = Class.forName("net.lointain.cosmos.network.CosmosModVariables$WorldVariables");
            return new CosmosData(vars.getMethod("get", LevelAccessor.class),
                    vars.getField("gravity_data"), vars.getField("collision_data_map"));
        } catch (ReflectiveOperationException | LinkageError e) {
            StarminerAdditions.LOGGER.warn("Cosmic Horizons world data not found; its gravity and planets are ignored", e);
            return null;
        }
    }

    /** The dimension's gravity relative to Earth, or null if Cosmos doesn't define it (yet). */
    @Nullable
    public Double gravity(ServerLevel level) {
        CompoundTag data = tag(level, gravityData);
        String key = level.dimension().location().toString();
        return data != null && data.contains(key) ? data.getDouble(key) : null;
    }

    /**
     * Whether the box comes within {@code margin} blocks of a planet in this dimension
     * (or might: true for a Cosmos dimension whose planet data isn't there yet).
     * Planets are cubes of side {@code scale}, slightly rotated; the bounding sphere of the
     * cube covers any rotation. Starlance sends ships near a planet down to it.
     */
    public boolean isNearPlanet(ServerLevel level, AABB box, double margin) {
        CompoundTag data = tag(level, collisionData);
        String key = level.dimension().location().toString();
        if (data == null || !data.contains(key, Tag.TAG_LIST)) {
            // On a fresh world Cosmos fills this in a moment after load: until then its own
            // dimensions count as crowded, so nothing spawns inside a planet in the meantime
            return "cosmos".equals(level.dimension().location().getNamespace());
        }
        ListTag planets = data.getList(key, Tag.TAG_COMPOUND);
        double boxRadius = box.getSize() * Math.sqrt(3) / 2;
        for (int i = 0; i < planets.size(); i++) {
            CompoundTag planet = planets.getCompound(i);
            double reach = planet.getDouble("scale") / 2 * Math.sqrt(3) + boxRadius + margin;
            double dx = box.getCenter().x - planet.getDouble("x");
            double dy = box.getCenter().y - planet.getDouble("y");
            double dz = box.getCenter().z - planet.getDouble("z");
            if (dx * dx + dy * dy + dz * dz < reach * reach) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private CompoundTag tag(ServerLevel level, Field field) {
        try {
            Object vars = getWorldVariables.invoke(null, level);
            return vars != null && field.get(vars) instanceof CompoundTag tag ? tag : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
