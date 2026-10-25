package net.memezforbeanz.starminerodyssey.item.custom;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Gravity anchor ported from Amethyst Gravity: while held (either hand), the holder's
 * gravity locks to the anchor's direction, overriding every field.
 */
public class GravityAnchorItem extends Item {

    public final Direction direction;

    public GravityAnchorItem(Direction direction, Properties properties) {
        super(properties);
        this.direction = direction;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("info." + StarminerAdditions.MOD_ID + ".gravity_anchor")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
