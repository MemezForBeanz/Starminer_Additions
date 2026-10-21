package net.memezforbeanz.starminerodyssey.registry;


import com.mojang.datafixers.types.Type;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistries;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.PlatingBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.StellarGravityBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;

public class ModBlockEntityTypes {
    public static final ResourcefulRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES;
    public static final RegistryEntry<BlockEntityType<StellarGravityBlockEntity>> STELLAR_GRAVITY;
    public static final RegistryEntry<BlockEntityType<GravityGeneratorBlockEntity>> GRAVITY_GENERATOR;
    public static final RegistryEntry<BlockEntityType<PlatingBlockEntity>> PLATING;

    public ModBlockEntityTypes() {
    }

    public static <E extends BlockEntity> BlockEntityType<E> createBlockEntityType(BlockEntityType.BlockEntitySupplier<E> factory, ResourcefulRegistry<Block> registry) {
        return Builder.of(factory, registry.stream().map(RegistryEntry::get).toArray(Block[]::new)).build(null);
    }

    static {
        BLOCK_ENTITY_TYPES = ResourcefulRegistries.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, StarminerAdditions.MOD_ID);
        STELLAR_GRAVITY = BLOCK_ENTITY_TYPES.register("stellar_gravity",
                () -> Builder.of(StellarGravityBlockEntity::new, ModBlocks.STELLAR_GRAVITY.get()).build(null));
        GRAVITY_GENERATOR = BLOCK_ENTITY_TYPES.register("gravity_generator",
                () -> Builder.of(GravityGeneratorBlockEntity::new, ModBlocks.GRAVITY_GENERATOR.get()).build(null));
        PLATING = BLOCK_ENTITY_TYPES.register("plating",
                () -> Builder.of(PlatingBlockEntity::new,
                        ModBlocks.PLATING.get(), ModBlocks.DENSE_PLATING.get()).build(null));
    }
}
