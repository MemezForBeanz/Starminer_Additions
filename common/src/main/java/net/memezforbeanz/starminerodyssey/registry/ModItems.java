package net.memezforbeanz.starminerodyssey.registry;


import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistries;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;
import earth.terrarium.botarium.common.registry.fluid.FluidBucketItem;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.item.Item;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.item.custom.GravityAnchorItem;
import net.memezforbeanz.starminerodyssey.item.custom.GravityGlassesArmorMaterial;
import net.memezforbeanz.starminerodyssey.item.custom.StellarCoreItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModItems {
    public static final ResourcefulRegistry<Item> ITEMS;
    public static final RegistryEntry<Item> STELLAR_CORE;
    public static final RegistryEntry<Item> HELIUM_BUCKET;
    public static final RegistryEntry<Item> STELLAR_GRAVITY;
    public static final RegistryEntry<Item> GRAVITY_GENERATOR;
    public static final RegistryEntry<Item> PLATING;
    public static final RegistryEntry<Item> DENSE_PLATING;
    public static final RegistryEntry<Item> GRAVITY_GLASSES;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_NORTH;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_SOUTH;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_EAST;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_WEST;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_UP;
    public static final RegistryEntry<Item> GRAVITY_ANCHOR_DOWN;


    public ModItems() {
    }

    static {
        // --- FIX IS HERE ---
        // By simply referencing the ModFluids class here, we ensure that
        // it gets loaded and initialized by Java *before* the rest of this code runs.
        // This guarantees that ModFluidProperties.HELIUM is ready to be used.
        var initFluids = ModFluids.FLUIDS;

        ITEMS = ResourcefulRegistries.create(BuiltInRegistries.ITEM, StarminerAdditions.MOD_ID);

        // This is the original code, which will now work correctly.
        // I've also renamed the item to "helium_bucket" to prevent any potential
        // conflicts with your fluid's ID.
        HELIUM_BUCKET = ITEMS.register("star_helium_bucket", () -> new FluidBucketItem(
                ModFluidProperties.HELIUM,
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
        );
        STELLAR_CORE = ITEMS.register("star_core", () -> new StellarCoreItem(new Item.Properties().rarity(Rarity.EPIC),
                StellarCoreItem.CAPACITY, StellarCoreItem.MAX_TRANSFER, StellarCoreItem.MAX_TRANSFER));
        STELLAR_GRAVITY = ITEMS.register("stellar_gravity", () -> new BlockItem(ModBlocks.STELLAR_GRAVITY.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("info." + StarminerAdditions.MOD_ID + ".stellar_gravity")
                        .withStyle(ChatFormatting.GRAY));
                super.appendHoverText(stack, level, tooltip, flag);
            }
        });
        GRAVITY_GENERATOR = ITEMS.register("gravity_generator", () -> new BlockItem(ModBlocks.GRAVITY_GENERATOR.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("info." + StarminerAdditions.MOD_ID + ".gravity_generator")
                        .withStyle(ChatFormatting.GRAY));
                super.appendHoverText(stack, level, tooltip, flag);
            }
        });
        PLATING = ITEMS.register("plating",
                () -> new BlockItem(ModBlocks.PLATING.get(), new Item.Properties()));
        DENSE_PLATING = ITEMS.register("dense_plating",
                () -> new BlockItem(ModBlocks.DENSE_PLATING.get(), new Item.Properties()));
        GRAVITY_GLASSES = ITEMS.register("gravity_glasses",
                () -> new ArmorItem(new GravityGlassesArmorMaterial(), ArmorItem.Type.HELMET, new Item.Properties()));
        GRAVITY_ANCHOR_NORTH = ITEMS.register("gravity_anchor_north",
                () -> new GravityAnchorItem(Direction.NORTH, new Item.Properties()));
        GRAVITY_ANCHOR_SOUTH = ITEMS.register("gravity_anchor_south",
                () -> new GravityAnchorItem(Direction.SOUTH, new Item.Properties()));
        GRAVITY_ANCHOR_EAST = ITEMS.register("gravity_anchor_east",
                () -> new GravityAnchorItem(Direction.EAST, new Item.Properties()));
        GRAVITY_ANCHOR_WEST = ITEMS.register("gravity_anchor_west",
                () -> new GravityAnchorItem(Direction.WEST, new Item.Properties()));
        GRAVITY_ANCHOR_UP = ITEMS.register("gravity_anchor_up",
                () -> new GravityAnchorItem(Direction.UP, new Item.Properties()));
        GRAVITY_ANCHOR_DOWN = ITEMS.register("gravity_anchor_down",
                () -> new GravityAnchorItem(Direction.DOWN, new Item.Properties()));
    }
}