package net.memezforbeanz.starminerodyssey.blocks.client;

import mod.azure.azurelib.animation.AzAnimatorConfig;
import mod.azure.azurelib.animation.controller.AzAnimationController;
import mod.azure.azurelib.animation.controller.AzAnimationControllerContainer;
import mod.azure.azurelib.animation.impl.AzBlockAnimator;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.StellarGravityBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class StellarGravityAnimator extends AzBlockAnimator<StellarGravityBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(StarminerAdditions.MOD_ID, "animations/stellar_gravity.animation.json");

    public StellarGravityAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerControllers(AzAnimationControllerContainer<StellarGravityBlockEntity> container) {
        container.add(AzAnimationController.builder(this, StellarGravityBlockEntity.CONTROLLER_NAME).build());
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(StellarGravityBlockEntity animatable) {
        return ANIMATIONS;
    }
}
