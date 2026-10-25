package net.memezforbeanz.starminerodyssey.shipspawn;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Player-facing switches for the ship spawner, filled in by each loader's config system
 * (Forge: {@code config/starmineradditions-common.toml}). Defaults spawn wherever a
 * {@code ship_spawns} definition says.
 */
public final class ShipSpawnConfig {

    public enum Mode {
        /** Spawn everywhere except the listed dimensions. */
        BLACKLIST,
        /** Spawn only in the listed dimensions. */
        WHITELIST;

        public static Mode parse(String value) {
            return "whitelist".equals(value.toLowerCase(Locale.ROOT)) ? WHITELIST : BLACKLIST;
        }
    }

    private static volatile boolean enabled = true;
    private static volatile Mode mode = Mode.BLACKLIST;
    private static volatile List<String> dimensions = List.of();

    private ShipSpawnConfig() {
    }

    public static void set(boolean enabled, Mode mode, List<String> dimensions) {
        ShipSpawnConfig.enabled = enabled;
        ShipSpawnConfig.mode = mode;
        ShipSpawnConfig.dimensions = List.copyOf(dimensions);
    }

    /** Whether new structures may spawn in this dimension. Existing ships are managed regardless. */
    public static boolean allowsSpawning(ResourceKey<Level> dimension) {
        if (!enabled) {
            return false;
        }
        boolean listed = isListed(dimension.location());
        return mode == Mode.WHITELIST ? listed : !listed;
    }

    /** Entries are dimension ids, or {@code namespace:*} for every dimension of a mod. */
    private static boolean isListed(ResourceLocation dimension) {
        for (String entry : dimensions) {
            String e = entry.trim();
            if (e.endsWith(":*")) {
                if (dimension.getNamespace().equals(e.substring(0, e.length() - 2))) {
                    return true;
                }
            } else if (dimension.equals(ResourceLocation.tryParse(e))) {
                return true;
            }
        }
        return false;
    }
}
