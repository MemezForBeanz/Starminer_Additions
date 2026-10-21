package net.memezforbeanz.starminerodyssey.fabric.client;

import mod.azure.azurelib.render.item.AzItemRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.client.GravityGeneratorItemRenderer;
import net.memezforbeanz.starminerodyssey.item.client.StellarCoreRenderer;
import net.memezforbeanz.starminerodyssey.registry.ModBlockEntityTypes;
import net.memezforbeanz.starminerodyssey.registry.ModItems;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class StarminerAdditionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        StarminerAdditions.initClient();

        // Register AzureLib item renderers (Fabric-specific, safe to call .get() here)
        AzItemRendererRegistry.register(StellarCoreRenderer::new, ModItems.STELLAR_CORE.get());
        AzItemRendererRegistry.register(GravityGeneratorItemRenderer::new, ModItems.GRAVITY_GENERATOR.get());

        // The gravity generator uses AzureLib for its animated model, chained with the
        // common field overlay.
        // (The AzureLib stellar-gravity renderer stays Forge-only for now.)
        BlockEntityRenderers.register(ModBlockEntityTypes.GRAVITY_GENERATOR.get(),
                context -> new FabricGravityGeneratorRenderer());
    }
}
