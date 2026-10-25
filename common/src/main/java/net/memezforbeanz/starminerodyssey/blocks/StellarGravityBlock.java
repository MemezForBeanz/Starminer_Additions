package net.memezforbeanz.starminerodyssey.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Natural planet-style gravity source. No GUI: the field sizes itself from the Dense
 * Helium connected to it (see {@link StellarGravityBlockEntity}).
 */
public class StellarGravityBlock extends AbstractGravityFieldBlock {

    public StellarGravityBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Rendered by the AzureLib block entity renderer, not a baked model
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StellarGravityBlockEntity(pos, state);
    }
}
