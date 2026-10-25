package net.memezforbeanz.starminerodyssey.blocks.client;

import mod.azure.azurelib.render.block.AzBlockEntityRenderer;
import mod.azure.azurelib.render.block.AzBlockEntityRendererConfig;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.StellarGravityBlockEntity;
import net.minecraft.resources.ResourceLocation;

/** Abstract because common compiles against the unremapped azurelib-common jar, where {@code render} has its obfuscated name: the concrete subclass lives in the loader module. */
public abstract class StellarGravityRenderer extends AzBlockEntityRenderer<StellarGravityBlockEntity> {

    private static final ResourceLocation GEO =
            new ResourceLocation(StarminerAdditions.MOD_ID, "geo/stellar_gravity.geo.json");
    private static final ResourceLocation TEX =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/block/stellar_gravity.png");

    public StellarGravityRenderer() {
        super(AzBlockEntityRendererConfig.<StellarGravityBlockEntity>builder(GEO, TEX)
                .setAnimatorProvider(StellarGravityAnimator::new)
                .build());
    }

    @Override
    public boolean shouldRenderOffScreen(StellarGravityBlockEntity blockEntity) {
        // The field box extends far outside the block itself
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
