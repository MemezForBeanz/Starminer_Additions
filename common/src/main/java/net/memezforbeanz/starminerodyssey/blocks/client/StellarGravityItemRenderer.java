package net.memezforbeanz.starminerodyssey.blocks.client;

import mod.azure.azurelib.render.item.AzItemRenderer;
import mod.azure.azurelib.render.item.AzItemRendererConfig;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.resources.ResourceLocation;

public class StellarGravityItemRenderer extends AzItemRenderer {

    private static final ResourceLocation GEO =
            new ResourceLocation(StarminerAdditions.MOD_ID, "geo/stellar_gravity.geo.json");
    private static final ResourceLocation TEX =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/block/stellar_gravity.png");

    public StellarGravityItemRenderer() {
        super(AzItemRendererConfig.builder(GEO, TEX).build());
    }
}
