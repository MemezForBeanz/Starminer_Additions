package net.memezforbeanz.starminerodyssey.blocks;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.memezforbeanz.starminerodyssey.blocks.client.GravityGeneratorScreenOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Machine counterpart to the Stellar Gravity block: projects gravity in a configurable
 * direction (side + angle) over a configurable radius, powered by FE. Right-click opens
 * the settings screen.
 */
public class GravityGeneratorBlock extends AbstractGravityFieldBlock {

    public GravityGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The AzureLib block-entity renderer draws the model
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof GravityGeneratorBlockEntity blockEntity) {
            // The settings screen has no container, so it opens purely client-side; the
            // EnvExecutor indirection keeps the client class from loading on the server.
            EnvExecutor.runInEnv(Env.CLIENT, () -> () -> GravityGeneratorScreenOpener.open(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravityGeneratorBlockEntity(pos, state);
    }
}
