package net.memezforbeanz.starminerodyssey.gravity;

import dev.architectury.event.events.common.TickEvent;
import net.memezforbeanz.starminerodyssey.item.custom.GravityAnchorItem;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Applies gravity anchors each player tick (both sides, like the field blocks): a held
 * anchor locks the holder's gravity to its direction at {@link PlanetGravityField#ANCHOR_PRIORITY},
 * above every field. Offhand wins over mainhand, matching Amethyst Gravity.
 */
public final class GravityAnchors {

    private GravityAnchors() {
    }

    public static void init() {
        TickEvent.PLAYER_POST.register(player -> {
            if (player.isSpectator()) {
                return;
            }
            GravityBridge bridge = GravityBridge.get();
            if (!bridge.canChangeGravity(player)) {
                return;
            }
            Direction direction = heldAnchorDirection(player);
            if (direction != null) {
                bridge.applyCardinalPull(player, direction, PlanetGravityField.ANCHOR_PRIORITY);
            }
        });
    }

    @Nullable
    private static Direction heldAnchorDirection(Player player) {
        if (player.getOffhandItem().getItem() instanceof GravityAnchorItem anchor) {
            return anchor.direction;
        }
        if (player.getMainHandItem().getItem() instanceof GravityAnchorItem anchor) {
            return anchor.direction;
        }
        return null;
    }
}
