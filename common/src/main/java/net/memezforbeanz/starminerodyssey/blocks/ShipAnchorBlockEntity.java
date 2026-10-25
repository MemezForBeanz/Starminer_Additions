package net.memezforbeanz.starminerodyssey.blocks;

import net.memezforbeanz.starminerodyssey.registry.ModBlockEntityTypes;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipSpawnSavedData;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which structure the anchor belongs to: its bounding box, the spawn
 * definition, and the block the anchor replaced (air unless the structure was solid).
 */
public class ShipAnchorBlockEntity extends BlockEntity {

    private static final String DEF_KEY = "Definition";
    private static final String BOX_KEY = "Box";
    private static final String ORIGINAL_KEY = "Original";
    private static final String CELL_KEY = "Cell";

    @Nullable
    private ResourceLocation defId;
    @Nullable
    private BoundingBox box;
    private BlockState originalState = Blocks.AIR.defaultBlockState();
    /** Spawner grid cell, so a recycled ship frees the right one. */
    private long cell = ShipSpawnSavedData.NO_CELL;
    /** Not saved: after a reload the anchor simply asks again. */
    private long nextRequest;

    public ShipAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SHIP_ANCHOR.get(), pos, state);
    }

    public void setup(ResourceLocation defId, BoundingBox box, BlockState originalState, long cell) {
        this.defId = defId;
        this.box = box;
        this.originalState = originalState;
        this.cell = cell;
        setChanged();
    }

    void serverTick(ServerLevel level) {
        if (box == null || level.getGameTime() < nextRequest) {
            return;
        }
        // Long.MAX_VALUE until the spawner answers: either it builds the ship (removing us)
        // or calls retryLater
        nextRequest = Long.MAX_VALUE;
        ShipSpawner.requestAssembly(level, worldPosition);
    }

    /** Ask the spawner again at this game time. */
    public void retryLater(long at) {
        nextRequest = at;
    }

    @Nullable
    public ResourceLocation defId() {
        return defId;
    }

    @Nullable
    public BoundingBox box() {
        return box;
    }

    public BlockState originalState() {
        return originalState;
    }

    public long cell() {
        return cell;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (defId != null) {
            tag.putString(DEF_KEY, defId.toString());
        }
        if (box != null) {
            tag.putIntArray(BOX_KEY, new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        }
        tag.put(ORIGINAL_KEY, NbtUtils.writeBlockState(originalState));
        tag.putLong(CELL_KEY, cell);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        defId = tag.contains(DEF_KEY) ? ResourceLocation.tryParse(tag.getString(DEF_KEY)) : null;
        int[] b = tag.getIntArray(BOX_KEY);
        box = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : null;
        cell = tag.contains(CELL_KEY) ? tag.getLong(CELL_KEY) : ShipSpawnSavedData.NO_CELL;
        if (tag.contains(ORIGINAL_KEY, Tag.TAG_COMPOUND)) {
            originalState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(ORIGINAL_KEY));
        }
    }
}
