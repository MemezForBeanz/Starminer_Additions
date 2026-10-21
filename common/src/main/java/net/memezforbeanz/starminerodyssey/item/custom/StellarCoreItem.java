package net.memezforbeanz.starminerodyssey.item.custom;

import earth.terrarium.botarium.common.energy.EnergyApi;
import earth.terrarium.botarium.common.energy.base.BotariumEnergyItem;
import earth.terrarium.botarium.common.energy.base.EnergyContainer;
import earth.terrarium.botarium.common.energy.impl.SimpleEnergyContainer;
import earth.terrarium.botarium.common.energy.impl.WrappedItemEnergyContainer;
import earth.terrarium.botarium.common.item.ItemStackHolder;


import net.memezforbeanz.starminerodyssey.constants.ConstantComponents;
import net.memezforbeanz.starminerodyssey.utils.DistributionMode;
import net.memezforbeanz.starminerodyssey.utils.TooltipUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StellarCoreItem extends Item implements BotariumEnergyItem<WrappedItemEnergyContainer> {
    public static final String ACTIVE_TAG = "Active";
    public static final String MODE_TAG = "Mode";

    /** The Stellar Core's energy capacity; the Gravity Generator's buffer matches it. */
    public static final long CAPACITY = 890_909_000L;
    public static final long MAX_TRANSFER = 10_000_000L;

    private final long capacity;
    private final long maxInput;
    private final long maxOutput;

    public StellarCoreItem(Properties properties, long capacity, long maxInput, long maxOutput) {
        super(properties);
        this.capacity = capacity;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
    }

    public static boolean active(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return !tag.contains("Active") || tag.getBoolean("Active");
    }

    public static boolean toggleActive(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean active = active(stack);
        tag.putBoolean("Active", !active);
        return !active;
    }

    public static DistributionMode mode(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains("Mode") ? DistributionMode.values()[tag.getByte("Mode")] : DistributionMode.SEQUENTIAL;
    }

    public static DistributionMode toggleMode(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        DistributionMode mode = mode(stack);
        DistributionMode toggled = mode == DistributionMode.SEQUENTIAL ? DistributionMode.ROUND_ROBIN : DistributionMode.SEQUENTIAL;
        tag.putByte("Mode", (byte)toggled.ordinal());
        return toggled;
    }

    public WrappedItemEnergyContainer getEnergyStorage(ItemStack holder) {
        return new WrappedItemEnergyContainer(holder, new SimpleEnergyContainer(this.capacity,this.maxOutput, this.maxInput) {
            public long maxInsert() {
                return maxInput;
            }

            public long maxExtract() {
                return maxOutput;
            }
        });
    }

    public boolean isBarVisible(@NotNull ItemStack stack) {
        return this.getEnergyStorage(stack).getStoredEnergy() > 0L;
    }

    public int getBarWidth(@NotNull ItemStack stack) {
        WrappedItemEnergyContainer energyStorage = this.getEnergyStorage(stack);
        return (int)((double)energyStorage.getStoredEnergy() / (double)energyStorage.getMaxCapacity() * (double)13.0F);
    }

    public int getBarColor(@NotNull ItemStack stack) {
        return 6544578;
    }

    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        WrappedItemEnergyContainer energy = this.getEnergyStorage(stack);
        tooltipComponents.add(TooltipUtils.getEnergyComponent(energy.getStoredEnergy(), energy.getMaxCapacity()));
        tooltipComponents.add(TooltipUtils.getActiveInactiveComponent(active(stack)));
        tooltipComponents.add(TooltipUtils.getDistributionModeComponent(mode(stack)));
        tooltipComponents.add(TooltipUtils.getMaxEnergyInComponent(energy.maxInsert()));
        tooltipComponents.add(TooltipUtils.getMaxEnergyOutComponent(energy.maxExtract()));
        TooltipUtils.addDescriptionComponent(tooltipComponents, net.memezforbeanz.starminerodyssey.constants.ConstantComponents.STELLAR_CORE_INFO);
    }

    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        } else {
            ItemStack stack = player.getItemInHand(usedHand);
            if (player.isShiftKeyDown()) {
                DistributionMode mode = toggleMode(stack);
                player.displayClientMessage(mode == DistributionMode.SEQUENTIAL ? ConstantComponents.SEQUENTIAL : ConstantComponents.ROUND_ROBIN, true);
            } else {
                boolean active = toggleActive(stack);
                player.displayClientMessage(active ? ConstantComponents.CAPACITOR_ENABLED : ConstantComponents.CAPACITOR_DISABLED, true);
            }

            return InteractionResultHolder.pass(stack);
        }
    }

    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide()) {
            if (entity.tickCount % 5 == 0) {
                if (active(stack)) {
                    if (entity instanceof Player player) {
                        Inventory inventory = player.getInventory();
                        WrappedItemEnergyContainer container = this.getEnergyStorage(stack);
                        if (container.getStoredEnergy() != 0L) {
                            ItemStackHolder from = new ItemStackHolder(stack);
                            switch (mode(stack)) {
                                case SEQUENTIAL -> this.distributeSequential(from, container.maxExtract() * 5L, inventory);
                                case ROUND_ROBIN -> this.distributeRoundRobin(from, container.maxExtract() * 5L, inventory);
                            }

                            inventory.setItem(slotId, from.getStack());
                        }
                    }
                }
            }
        }
    }

    public void distributeSequential(ItemStackHolder from, long maxExtract, Inventory inventory) {
        for(int i = inventory.getContainerSize() - 1; i >= 0; --i) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() != this && EnergyContainer.holdsEnergy(stack)) {
                ItemStackHolder to = new ItemStackHolder(stack);
                EnergyApi.moveEnergy(from, to, maxExtract, false);
                inventory.setItem(i, to.getStack());
            }
        }

    }

    public void distributeRoundRobin(ItemStackHolder from, long maxExtract, Inventory inventory) {
        int energyItems = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && !(stack.getItem() instanceof StellarCoreItem) && EnergyContainer.holdsEnergy(stack)) {
                energyItems++;
            }
        }

        if (energyItems > 0) {
            long energyPerItem = maxExtract / energyItems;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && !(stack.getItem() instanceof StellarCoreItem) && EnergyContainer.holdsEnergy(stack)) {
                    ItemStackHolder to = new ItemStackHolder(stack);
                    EnergyApi.moveEnergy(from, to, energyPerItem, false);
                    inventory.setItem(i, to.getStack());
                }
            }
        }
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos targetPos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        Player player = context.getPlayer();

        // A player and block entity must exist
        if (player == null || blockEntity == null) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        ItemStackHolder from = new ItemStackHolder(stack);
        EnergyContainer itemContainer = this.getEnergyStorage(stack);

        // Check if the item has energy to transfer
        if (itemContainer.getStoredEnergy() <= 0) {
            player.displayClientMessage(Component.translatable("info.starmineradditions.star_core.no_energy"), true);
            return InteractionResult.FAIL;
        }

        // --- FIX IS HERE ---
        // Get the energy container directly from the BlockEntity at the target position.
        // We use the direction to hint which side we are trying to insert from.
        Direction side = context.getClickedFace();
        EnergyContainer targetContainer = EnergyContainer.of(blockEntity, side);
        // Check if the block has an energy container and can accept energy
        if (targetContainer == null || !targetContainer.allowsInsertion()) {
            player.displayClientMessage(Component.translatable("info.starmineradditions.star_core.no_target"), true);
            return InteractionResult.FAIL;
        }

        // Attempt the energy transfer
        long moved = EnergyApi.moveEnergy(itemContainer, targetContainer, this.maxOutput, false);

        if (moved > 0) {
            spawnEnergyTransferEffects(level, targetPos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }


    private void spawnEnergyTransferEffects(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.3f, 1.0f);
        }
    }

}
