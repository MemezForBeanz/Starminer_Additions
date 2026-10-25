package net.memezforbeanz.starminerodyssey.blocks;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Base for gravity field blocks: wires up the {@code ACTIVE} state and a both-sides
 * ticker (the server drives gravity for all entities, the client applies it to players
 * so the local player's gravity is predicted smoothly).
 */
public abstract class AbstractGravityFieldBlock extends BaseEntityBlock {

    /** Whether the block's field is enabled. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected AbstractGravityFieldBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof AbstractGravityFieldBlockEntity field) {
                field.tick(lvl, pos, st);
            }
        };
    }
}
