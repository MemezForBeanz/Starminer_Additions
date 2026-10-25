package net.memezforbeanz.starminerodyssey.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.client.GravityGeneratorFieldRenderer;
import net.memezforbeanz.starminerodyssey.blocks.client.GravityGeneratorRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Loader-side render hook only: chains AzureLib's model render (which needs the
 * loader-remapped AzureLib jar to compile) with the common field overlay.
 */
public class ForgeGravityGeneratorRenderer extends GravityGeneratorRenderer {

    @Override
    public void render(GravityGeneratorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        super.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
        GravityGeneratorFieldRenderer.renderGeneratorOverlay(blockEntity, partialTick, poseStack, buffer);
    }
}
