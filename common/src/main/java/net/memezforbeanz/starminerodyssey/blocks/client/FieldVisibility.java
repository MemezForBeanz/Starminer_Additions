package net.memezforbeanz.starminerodyssey.blocks.client;

import net.memezforbeanz.starminerodyssey.blocks.AbstractGravityFieldBlockEntity;
import net.memezforbeanz.starminerodyssey.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side visibility decision for gravity field overlays, matching Amethyst
 * Gravity's three modes: with glasses (default), always, never.
 */
public final class FieldVisibility {

    private FieldVisibility() {
    }

    public static boolean shouldShow(AbstractGravityFieldBlockEntity blockEntity) {
        if (!blockEntity.isFieldActive()) {
            return false;
        }
        return switch (blockEntity.getVisibility()) {
            case AbstractGravityFieldBlockEntity.VISIBILITY_ALWAYS -> true;
            case AbstractGravityFieldBlockEntity.VISIBILITY_NEVER -> false;
            default -> isWearingGravityGlasses();
        };
    }

    public static boolean isWearingGravityGlasses() {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera instanceof LivingEntity living
                && living.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GRAVITY_GLASSES.get());
    }
}
