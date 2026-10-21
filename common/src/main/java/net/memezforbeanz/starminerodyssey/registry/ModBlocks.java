package net.memezforbeanz.starminerodyssey.registry;

import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistries;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;

import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlock;
import net.memezforbeanz.starminerodyssey.blocks.PlatingBlock;
import net.memezforbeanz.starminerodyssey.blocks.StellarGravityBlock;
import net.memezforbeanz.starminerodyssey.blocks.fluid.HeliumLiquidBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {

    public static final ResourcefulRegistry<Block> BLOCKS;
    public static final ResourcefulRegistry<Block> FLUIDS;
    public static final RegistryEntry<Block> HELIUM;
    public static final RegistryEntry<Block> STELLAR_GRAVITY;
    public static final RegistryEntry<Block> GRAVITY_GENERATOR;
    public static final RegistryEntry<Block> PLATING;
    public static final RegistryEntry<Block> DENSE_PLATING;

    /** Amethyst Gravity's plating field reaches: normal lets you walk, dense lets you jump. */
    public static final double PLATING_FIELD_HEIGHT = 0.6;
    public static final double DENSE_PLATING_FIELD_HEIGHT = 1.3;



    public ModBlocks() {
    }

    static {
        BLOCKS = ResourcefulRegistries.create(BuiltInRegistries.BLOCK, StarminerAdditions.MOD_ID);
        FLUIDS = ResourcefulRegistries.create(BLOCKS);
        HELIUM = FLUIDS.register("star_helium", () -> new HeliumLiquidBlock(ModFluidProperties.HELIUM, BlockBehaviour.Properties.copy(Blocks.WATER).mapColor(MapColor.COLOR_CYAN)));
        STELLAR_GRAVITY = BLOCKS.register("stellar_gravity", () -> new StellarGravityBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                .lightLevel(state -> 8)
                .noOcclusion()));
        GRAVITY_GENERATOR = BLOCKS.register("gravity_generator", () -> new GravityGeneratorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                .lightLevel(state -> 4)
                // Like stellar gravity: without this, neighbors cull their touching faces
                // and the world turns see-through where the geo model has gaps
                .noOcclusion()));
        PLATING = BLOCKS.register("plating", () -> new PlatingBlock(PLATING_FIELD_HEIGHT,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noOcclusion().noCollission().instabreak()));
        DENSE_PLATING = BLOCKS.register("dense_plating", () -> new PlatingBlock(DENSE_PLATING_FIELD_HEIGHT,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noOcclusion().noCollission().instabreak()));

    }
}