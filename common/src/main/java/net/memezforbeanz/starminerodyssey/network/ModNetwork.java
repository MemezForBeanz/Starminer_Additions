package net.memezforbeanz.starminerodyssey.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.gravity.GeneratorFieldMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ModNetwork {

    /** C2S: apply gravity generator settings (mode, dimensions, direction, target gravity...) from the screen. */
    public static final ResourceLocation UPDATE_GRAVITY_GENERATOR =
            new ResourceLocation(StarminerAdditions.MOD_ID, "update_gravity_generator");

    /** Players further than this from the generator can't reconfigure it. */
    private static final double MAX_EDIT_DISTANCE_SQ = 8 * 8;

    private ModNetwork() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPDATE_GRAVITY_GENERATOR, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            GravityGeneratorBlockEntity.Settings settings = readSettings(buf);
            context.queue(() -> {
                ServerPlayer serverPlayer = editingPlayer(context.getPlayer(), pos);
                if (serverPlayer == null
                        || !(serverPlayer.level().getBlockEntity(pos) instanceof GravityGeneratorBlockEntity blockEntity)) {
                    return;
                }
                if (!serverPlayer.mayBuild()) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message." + StarminerAdditions.MOD_ID + ".gravity_generator.modify")
                                    .withStyle(ChatFormatting.RED), true);
                    return;
                }
                blockEntity.updateSettings(serverPlayer, settings);
            });
        });
    }

    /** Shared packet validation: a server player within editing range of the block. */
    private static ServerPlayer editingPlayer(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        if (serverPlayer.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_EDIT_DISTANCE_SQ) {
            return null;
        }
        return serverPlayer;
    }

    public static void sendUpdateGravityGenerator(BlockPos pos, GravityGeneratorBlockEntity.Settings settings) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        writeSettings(buf, settings);
        NetworkManager.sendToServer(UPDATE_GRAVITY_GENERATOR, buf);
    }

    private static void writeSettings(FriendlyByteBuf buf, GravityGeneratorBlockEntity.Settings settings) {
        buf.writeVarInt(settings.mode().ordinal());
        buf.writeDouble(settings.radius());
        buf.writeDouble(settings.height());
        buf.writeDouble(settings.width());
        buf.writeDouble(settings.depth());
        buf.writeEnum(settings.side());
        buf.writeDouble(settings.angleDegrees());
        buf.writeEnum(settings.face());
        buf.writeEnum(settings.axis());
        buf.writeBoolean(settings.repel());
        buf.writeDouble(settings.targetGravity());
        buf.writeVarInt(settings.visibility());
    }

    private static GravityGeneratorBlockEntity.Settings readSettings(FriendlyByteBuf buf) {
        return new GravityGeneratorBlockEntity.Settings(
                GeneratorFieldMode.byOrdinal(buf.readVarInt()),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readEnum(Direction.class),
                buf.readDouble(),
                buf.readEnum(Direction.class),
                buf.readEnum(Direction.Axis.class),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readVarInt());
    }
}
