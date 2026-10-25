package net.memezforbeanz.starminerodyssey.forge;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.forge.compat.CosmosData;
import net.memezforbeanz.starminerodyssey.forge.shipspawn.ForgeShipBridge;
import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.ShipDimensionGravity;
import net.memezforbeanz.starminerodyssey.gravity.VSGravityBridge;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipBridge;
import net.minecraftforge.fml.ModList;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(StarminerAdditions.MOD_ID)
public final class StarminerAdditionsForge {
    public StarminerAdditionsForge() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(StarminerAdditions.MOD_ID, modEventBus);

        StarminerForgeConfig.register(modEventBus);

        // Wire the common gravity code to VS-Gravity before anything ticks.
        GravityBridge.set(new VSGravityBridge());
        // Spawned structures stay plain blocks without Valkyrien Skies
        if (ModList.get().isLoaded("valkyrienskies")) {
            ShipBridge.set(new ForgeShipBridge());
            CosmosData cosmos = CosmosData.get();
            ShipDimensionGravity.register(cosmos != null ? cosmos::gravity : null);
        }

        // Initialize registries in the constructor before Architectury processes them
        // This triggers static initialization of all registry classes
        StarminerAdditions.init();

        // Client setup is handled by ForgeClientEvents with @Mod.EventBusSubscriber
    }
}
