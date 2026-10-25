package net.memezforbeanz.starminerodyssey.blocks.client;

import icyllis.modernui.mc.MuiModApi;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.client.ui.GravityGeneratorFragment;
import net.memezforbeanz.starminerodyssey.blocks.client.ui.TerminalScreenCallback;
import net.minecraft.client.Minecraft;

/**
 * Client-only indirection for opening the settings screen, so
 * {@code GravityGeneratorBlock} (loaded on both sides) never references client classes
 * directly.
 */
public final class GravityGeneratorScreenOpener {

    private GravityGeneratorScreenOpener() {
    }

    public static void open(GravityGeneratorBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(MuiModApi.get().createScreen(
                new GravityGeneratorFragment(blockEntity), new TerminalScreenCallback(), null,
                blockEntity.getBlockState().getBlock().getName().getString()));
    }
}
