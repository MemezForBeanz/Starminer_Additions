package net.memezforbeanz.starminerodyssey.forge;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.forge.gravity.ForgeGravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(StarminerAdditions.MOD_ID)
public final class StarminerAdditionsForge {
    public StarminerAdditionsForge() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(StarminerAdditions.MOD_ID, modEventBus);

        // Wire the common gravity code to VS-Gravity before anything ticks.
        GravityBridge.set(new ForgeGravityBridge());

        // Initialize registries in the constructor before Architectury processes them
        // This triggers static initialization of all registry classes
        StarminerAdditions.init();

        // Client setup is handled by ForgeClientEvents with @Mod.EventBusSubscriber
    }
}
