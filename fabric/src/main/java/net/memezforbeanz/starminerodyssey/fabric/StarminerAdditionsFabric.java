package net.memezforbeanz.starminerodyssey.fabric;

import dev.architectury.platform.Platform;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.ShipDimensionGravity;
import net.memezforbeanz.starminerodyssey.gravity.VSGravityBridge;
import net.fabricmc.api.ModInitializer;

public final class StarminerAdditionsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Wire the common gravity code to VS-Gravity before anything ticks.
        GravityBridge.set(new VSGravityBridge());
        // Ship gravity follows Ad Astra's planets (Cosmic Horizons is Forge-only)
        if (Platform.isModLoaded("valkyrienskies")) {
            ShipDimensionGravity.register(null);
        }

        // Run our common setup.
        StarminerAdditions.init();
    }
}
