package net.memezforbeanz.starminerodyssey.forge;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipSpawnConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/** {@code config/starmineradditions-common.toml}, mirrored into the common config holders. */
public final class StarminerForgeConfig {

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue SPAWNING_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> DIMENSION_MODE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Planetoids and other structures from data/<namespace>/ship_spawns").push("shipSpawning");
        SPAWNING_ENABLED = builder
                .comment("Spawn new structures at all. Already spawned ones keep working either way.")
                .define("enabled", true);
        DIMENSION_MODE = builder
                .comment("How the dimension list is used:",
                        "  blacklist - spawn in every dimension a ship_spawns definition names, except the listed ones",
                        "  whitelist - spawn only in the listed dimensions (they still need a definition naming them)")
                .define("dimensionMode", "blacklist",
                        value -> value instanceof String s && (s.equals("blacklist") || s.equals("whitelist")));
        DIMENSIONS = builder
                .comment("Dimension ids, e.g. \"cosmos:solar_system\", or \"namespace:*\" for every dimension of a mod.")
                .defineListAllowEmpty(List.of("dimensions"), List::of, value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private StarminerForgeConfig() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, StarminerAdditions.MOD_ID + "-common.toml");
        modEventBus.addListener((ModConfigEvent.Loading event) -> apply(event.getConfig()));
        modEventBus.addListener((ModConfigEvent.Reloading event) -> apply(event.getConfig()));
    }

    private static void apply(ModConfig config) {
        if (config.getSpec() != SPEC) {
            return;
        }
        ShipSpawnConfig.set(SPAWNING_ENABLED.get(), ShipSpawnConfig.Mode.parse(DIMENSION_MODE.get()),
                List.copyOf(DIMENSIONS.get()));
    }
}
