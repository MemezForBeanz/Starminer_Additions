package net.memezforbeanz.starminerodyssey.blocks.client;

import mod.azure.azurelib.animation.AzAnimatorConfig;
import mod.azure.azurelib.animation.controller.AzAnimationController;
import mod.azure.azurelib.animation.controller.AzAnimationControllerContainer;
import mod.azure.azurelib.animation.impl.AzBlockAnimator;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class GravityGeneratorAnimator extends AzBlockAnimator<GravityGeneratorBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(StarminerAdditions.MOD_ID, "animations/gravity_generator.animation.json");

    /** Animation speed while the machine idles (switched off or out of power). */
    private static final double IDLE_SPEED = 1.0;

    /** Speed while projecting at minimum range; ramps to {@link #RUNNING_SPEED_MAX} at the cap. */
    private static final double RUNNING_SPEED_MIN = 1.5;
    private static final double RUNNING_SPEED_MAX = 4.0;

    private AzAnimationController<GravityGeneratorBlockEntity> controller;

    public GravityGeneratorAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerControllers(AzAnimationControllerContainer<GravityGeneratorBlockEntity> container) {
        controller = AzAnimationController.builder(this, GravityGeneratorBlockEntity.CONTROLLER_NAME).build();
        container.add(controller);
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(GravityGeneratorBlockEntity animatable) {
        return ANIMATIONS;
    }

    /**
     * One idle loop plays for the model's whole lifetime; while the field is projecting
     * the same loop runs faster, scaling with the configured range. The controller timer
     * re-reads its properties every frame, so updating the speed here is enough.
     */
    @Override
    protected void preAnimationSetup(GravityGeneratorBlockEntity animatable, double animTime, float partialTicks) {
        super.preAnimationSetup(animatable, animTime, partialTicks);
        controller.setAnimationProperties(
                controller.animationProperties().withAnimationSpeed(animationSpeed(animatable)));
    }

    private static double animationSpeed(GravityGeneratorBlockEntity blockEntity) {
        if (!blockEntity.isFieldActive()) {
            return IDLE_SPEED;
        }
        // Radius of the cube with the field's volume, so every field mode spins up alike
        double equivalentRadius = (Math.cbrt(blockEntity.getFieldVolume()) - 1) / 2;
        double progress = (equivalentRadius - GravityGeneratorBlockEntity.MIN_FIELD_RADIUS)
                / (GravityGeneratorBlockEntity.MAX_FIELD_RADIUS - GravityGeneratorBlockEntity.MIN_FIELD_RADIUS);
        return Mth.lerp(Mth.clamp(progress, 0.0, 1.0), RUNNING_SPEED_MIN, RUNNING_SPEED_MAX);
    }
}
