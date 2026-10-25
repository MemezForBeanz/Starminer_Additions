package net.memezforbeanz.starminerodyssey.shipspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every block the template would place, after the processors before it, and
 * drops it so nothing is placed. Used with the template at {@link BlockPos#ZERO} and no
 * rotation, it yields the template-local block list that vanilla keeps private, so the
 * spawner can lay it out under rotations vanilla doesn't support. Never serialized.
 */
final class BlockCaptureProcessor extends StructureProcessor {

    final List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        blocks.add(current);
        return null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        // Never serialized; any registered type satisfies the contract
        return StructureProcessorType.NOP;
    }
}
