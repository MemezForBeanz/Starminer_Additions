package net.memezforbeanz.starminerodyssey.shipspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Drops every block, so placing a template with it places only the template's entities.
 * Ship-mode spawns build the blocks in the ship's shipyard and the entities in the world
 * where the ship now sits. Only used in code, never serialized.
 */
final class EntitiesOnlyProcessor extends StructureProcessor {

    static final EntitiesOnlyProcessor INSTANCE = new EntitiesOnlyProcessor();

    private EntitiesOnlyProcessor() {
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        return null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        // Never serialized; any registered type satisfies the contract
        return StructureProcessorType.NOP;
    }
}
