package net.memezforbeanz.starminerodyssey.forge.compat;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflective access to Starlance (mod id {@code vsch}), so it isn't a build dependency.
 * <p>
 * Starlance's Ad Astra module treats any loaded ship whose centre is above Ad Astra's
 * {@code atmosphereLeave} (600) as a rocket leaving a planet: it freezes the ship and
 * opens Ad Astra's planet menu for any player near it. It does that in every dimension,
 * so planetoids high in Cosmos space trigger it. It skips ships flagged
 * {@code ShipLandingAttachment.landing}, and only clears the flag once a ship drops below
 * 590, which planetoids never do.
 */
public final class StarlanceCompat {

    @Nullable
    private static final StarlanceCompat INSTANCE = ModList.get().isLoaded("vsch") ? create() : null;

    private final Method getLandingAttachment;
    private final Field landing;

    private StarlanceCompat(Method getLandingAttachment, Field landing) {
        this.getLandingAttachment = getLandingAttachment;
        this.landing = landing;
    }

    @Nullable
    private static StarlanceCompat create() {
        try {
            Class<?> attachment = Class.forName("net.jcm.vsch.ship.ShipLandingAttachment");
            return new StarlanceCompat(attachment.getMethod("get", LoadedServerShip.class), attachment.getField("landing"));
        } catch (ReflectiveOperationException | LinkageError e) {
            StarminerAdditions.LOGGER.warn("Starlance landing attachment not found; spawned ships may open its planet menu", e);
            return null;
        }
    }

    /** Keep Starlance from treating the ship as a rocket leaving orbit. No-op if it isn't loaded. */
    public static void exemptFromPlanetMenu(ServerShip ship) {
        if (INSTANCE == null || !(ship instanceof LoadedServerShip loaded)) {
            return;
        }
        try {
            Object attachment = INSTANCE.getLandingAttachment.invoke(null, loaded);
            if (attachment != null && !INSTANCE.landing.getBoolean(attachment)) {
                INSTANCE.landing.setBoolean(attachment, true);
            }
        } catch (ReflectiveOperationException e) {
            // Leave it; worst case the menu opens
        }
    }
}
