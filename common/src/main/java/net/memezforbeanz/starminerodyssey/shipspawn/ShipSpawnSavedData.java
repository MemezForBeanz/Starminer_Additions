package net.memezforbeanz.starminerodyssey.shipspawn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level record of which grid cells already got their structure, and which ships the
 * spawner created, so neither repeats nor loses track across restarts.
 */
public final class ShipSpawnSavedData extends SavedData {

    private static final String NAME = "starmineradditions_ship_spawns";
    /** Marks a ship whose cell is unknown (saved before cells were tracked): never recycled. */
    public static final long NO_CELL = Long.MIN_VALUE;

    /** Definition id -> placed cells ({@link net.minecraft.world.level.ChunkPos#asLong} of the cell coords). */
    private final Map<ResourceLocation, LongSet> placedCells = new HashMap<>();
    private final Long2ObjectOpenHashMap<SpawnedShip> ships = new Long2ObjectOpenHashMap<>();

    /**
     * @param modified set once a player changed the ship (or it might have changed): it then
     *                 stays forever instead of being recycled
     */
    public record SpawnedShip(ResourceLocation defId, long cell, boolean modified) {
    }

    public static ShipSpawnSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShipSpawnSavedData::load, ShipSpawnSavedData::new, NAME);
    }

    public boolean isPlaced(ResourceLocation defId, long cell) {
        LongSet cells = placedCells.get(defId);
        return cells != null && cells.contains(cell);
    }

    public void markPlaced(ResourceLocation defId, long cell) {
        if (placedCells.computeIfAbsent(defId, k -> new LongOpenHashSet()).add(cell)) {
            setDirty();
        }
    }

    /** Free the cell so the spawner places its structure again. */
    public void unmarkPlaced(ResourceLocation defId, long cell) {
        LongSet cells = placedCells.get(defId);
        if (cells != null && cells.remove(cell)) {
            setDirty();
        }
    }

    public void addShip(long shipId, ResourceLocation defId, long cell) {
        ships.put(shipId, new SpawnedShip(defId, cell, false));
        setDirty();
    }

    @Nullable
    public SpawnedShip ship(long shipId) {
        return ships.get(shipId);
    }

    public void markModified(long shipId) {
        SpawnedShip ship = ships.get(shipId);
        if (ship != null && !ship.modified()) {
            ships.put(shipId, new SpawnedShip(ship.defId(), ship.cell(), true));
            setDirty();
        }
    }

    public void removeShip(long shipId) {
        if (ships.remove(shipId) != null) {
            setDirty();
        }
    }

    public long[] shipIds() {
        return ships.keySet().toLongArray();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag cells = new CompoundTag();
        placedCells.forEach((id, set) -> cells.putLongArray(id.toString(), set.toLongArray()));
        tag.put("placed", cells);
        CompoundTag shipTag = new CompoundTag();
        ships.forEach((id, ship) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("def", ship.defId().toString());
            entry.putLong("cell", ship.cell());
            entry.putBoolean("modified", ship.modified());
            shipTag.put(Long.toString(id), entry);
        });
        tag.put("ships", shipTag);
        return tag;
    }

    private static ShipSpawnSavedData load(CompoundTag tag) {
        ShipSpawnSavedData data = new ShipSpawnSavedData();
        CompoundTag cells = tag.getCompound("placed");
        for (String key : cells.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && cells.get(key) instanceof LongArrayTag array) {
                data.placedCells.put(id, new LongOpenHashSet(array.getAsLongArray()));
            }
        }
        CompoundTag shipTag = tag.getCompound("ships");
        for (String key : shipTag.getAllKeys()) {
            long shipId;
            try {
                shipId = Long.parseLong(key);
            } catch (NumberFormatException e) {
                continue; // Corrupt entry: drop it
            }
            SpawnedShip ship;
            if (shipTag.contains(key, Tag.TAG_COMPOUND)) {
                CompoundTag entry = shipTag.getCompound(key);
                ResourceLocation def = ResourceLocation.tryParse(entry.getString("def"));
                ship = def == null ? null : new SpawnedShip(def, entry.getLong("cell"), entry.getBoolean("modified"));
            } else {
                // Older saves stored only the definition id
                ResourceLocation def = ResourceLocation.tryParse(shipTag.getString(key));
                ship = def == null ? null : new SpawnedShip(def, NO_CELL, true);
            }
            if (ship != null) {
                data.ships.put(shipId, ship);
            }
        }
        return data;
    }
}
