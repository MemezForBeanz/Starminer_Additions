package net.memezforbeanz.starminerodyssey.blocks.client.ui;

import icyllis.modernui.mc.ScreenCallback;

/** The game keeps running while a generator is being configured. */
public class TerminalScreenCallback implements ScreenCallback {

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
