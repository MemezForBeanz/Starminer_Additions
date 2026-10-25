package net.memezforbeanz.starminerodyssey.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.memezforbeanz.starminerodyssey.blocks.StellarGravityBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.client.FieldOverlayRenderer;
import net.memezforbeanz.starminerodyssey.blocks.client.StellarGravityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Loader-side render hook only: chains AzureLib's model render (which needs the
 * loader-remapped AzureLib jar to compile) with the common planet-field overlay.
 */
public class FabricStellarGravityRenderer extends StellarGravityRenderer {

    @Override
    public void render(StellarGravityBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        super.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
        FieldOverlayRenderer.renderPlanetOverlay(blockEntity, partialTick, poseStack, buffer);
    }
}
