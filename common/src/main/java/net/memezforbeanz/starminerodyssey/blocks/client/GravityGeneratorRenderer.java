package net.memezforbeanz.starminerodyssey.blocks.client;

import mod.azure.azurelib.render.block.AzBlockEntityRenderer;
import mod.azure.azurelib.render.block.AzBlockEntityRendererConfig;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * Abstract because common compiles against the unremapped azurelib-common jar, where
 * {@code render} has its obfuscated name: concrete subclasses live in the loader modules.
 * <p>
 * At the hard range cap the texture swaps to the "overdrive" one.
 */
public abstract class GravityGeneratorRenderer extends AzBlockEntityRenderer<GravityGeneratorBlockEntity> {

    private static final ResourceLocation GEO =
            new ResourceLocation(StarminerAdditions.MOD_ID, "geo/gravity_generator.geo.json");
    private static final ResourceLocation TEX =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/block/gravity_generator.png");
    private static final ResourceLocation TEX_MAX =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/block/gravity_generator_max.png");

    public GravityGeneratorRenderer() {
        super(AzBlockEntityRendererConfig.<GravityGeneratorBlockEntity>builder(
                        blockEntity -> GEO,
                        blockEntity -> blockEntity.isRunningAtMaxRange() ? TEX_MAX : TEX)
                .setAnimatorProvider(GravityGeneratorAnimator::new)
                .build());
    }

    @Override
    public boolean shouldRenderOffScreen(GravityGeneratorBlockEntity blockEntity) {
        // The field box extends far outside the block itself
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
