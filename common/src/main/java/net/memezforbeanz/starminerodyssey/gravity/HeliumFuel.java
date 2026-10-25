package net.memezforbeanz.starminerodyssey.gravity;

import net.memezforbeanz.starminerodyssey.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Dense Helium fuel for gravity field blocks, replacing Amethyst Gravity's amethyst
 * block scan: a six-connected flood fill through helium source blocks counts the pool,
 * and amethyst's fuel curve ({@code floor(volume^(2/3)) / 4}) prices a field's upkeep.
 */
public final class HeliumFuel {

    /** Safety valve for the flood fill; effectively unbounded for real builds. */
    public static final int SEARCH_BLOCK_CAP = 65_536;

    private HeliumFuel() {
    }

    /** Helium blocks needed to sustain a field box of the given volume. */
    public static int requiredFuel(double fieldVolume) {
        return Math.max((int) Math.floor(Math.pow(fieldVolume, 2.0 / 3.0)) / 4, 0);
    }

    /**
     * Flood fill from {@code origin} through connected Dense Helium source blocks
     * (six-connected, amethyst-style), traversing the whole pool.
     */
    public static int search(Level level, BlockPos origin) {
        Block heliumBlock = ModBlocks.HELIUM.get();
        Set<BlockPos> connected = new HashSet<>();
        Set<BlockPos> frontier = new HashSet<>();
        frontier.add(origin);
        while (!frontier.isEmpty() && connected.size() < SEARCH_BLOCK_CAP) {
            Set<BlockPos> discovered = new HashSet<>();
            for (BlockPos blockPos : frontier) {
                for (Direction direction : Direction.values()) {
                    BlockPos offset = blockPos.relative(direction);
                    if (connected.contains(offset)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(offset);
                    if (state.is(heliumBlock) && state.getFluidState().isSource()) {
                        discovered.add(offset);
                    }
                }
            }
            connected.addAll(discovered);
            frontier = discovered;
        }
        return connected.size();
    }
}
