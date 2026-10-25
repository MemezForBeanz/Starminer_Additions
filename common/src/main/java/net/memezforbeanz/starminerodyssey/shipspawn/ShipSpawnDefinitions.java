package net.memezforbeanz.starminerodyssey.shipspawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<ns>/ship_spawns/*.json} and the remapped templates they reference.
 */
public final class ShipSpawnDefinitions extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    public static final ShipSpawnDefinitions INSTANCE = new ShipSpawnDefinitions();

    private static volatile Map<ResourceLocation, ShipSpawnDefinition> definitions = Map.of();
    /** Per definition, since the block remap is per definition. Empty = failed to load. */
    private static final Map<ResourceLocation, Map<ResourceLocation, Optional<StructureTemplate>>> TEMPLATES =
            new ConcurrentHashMap<>();

    private ShipSpawnDefinitions() {
        super(GSON, "ship_spawns");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ShipSpawnDefinition> loaded = new HashMap<>();
        jsons.forEach((id, json) -> ShipSpawnDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> StarminerAdditions.LOGGER.error("Invalid ship spawn {}: {}", id, error))
                .ifPresent(def -> loaded.putAll(def.expandLayers(id))));
        definitions = Map.copyOf(loaded);
        TEMPLATES.clear();
        StarminerAdditions.LOGGER.info("Loaded {} ship spawn definitions", loaded.size());
    }

    @Nullable
    public static ShipSpawnDefinition get(ResourceLocation id) {
        return definitions.get(id);
    }

    /** Definitions that spawn in the given dimension, keyed by id. */
    public static List<Map.Entry<ResourceLocation, ShipSpawnDefinition>> forDimension(ResourceKey<Level> dimension) {
        List<Map.Entry<ResourceLocation, ShipSpawnDefinition>> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ShipSpawnDefinition> e : definitions.entrySet()) {
            if (e.getValue().dimensions().contains(dimension)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * The template with the definition's block remap applied. The remap rewrites the raw
     * palette before the template is parsed: vanilla turns unknown block ids into air while
     * loading, so a structure processor would only ever see air.
     */
    @Nullable
    public static StructureTemplate template(ServerLevel level, ResourceLocation defId, ShipSpawnDefinition def,
                                             ResourceLocation templateId) {
        return TEMPLATES.computeIfAbsent(defId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(templateId, k -> Optional.ofNullable(loadTemplate(level, def, templateId)))
                .orElse(null);
    }

    @Nullable
    private static StructureTemplate loadTemplate(ServerLevel level, ShipSpawnDefinition def, ResourceLocation templateId) {
        ResourceLocation file = templateId.withPath(p -> "structures/" + p + ".nbt");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            StarminerAdditions.LOGGER.error("Ship spawn template {} not found", file);
            return null;
        }
        try (InputStream in = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(in);
            tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag,
                    NbtUtils.getDataVersion(tag, 500));
            remapPalettes(tag, def.blockRemap());
            remapEntities(tag, def.entityRemap());
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), tag);
            return template;
        } catch (Exception e) {
            StarminerAdditions.LOGGER.error("Failed to load ship spawn template {}", file, e);
            return null;
        }
    }

    private static void remapPalettes(CompoundTag tag, Map<ResourceLocation, ResourceLocation> remap) {
        if (remap.isEmpty()) {
            return;
        }
        // Block entries point into the palette by index; every palette variant shares them
        IntSet remapped = new IntOpenHashSet();
        if (tag.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            for (int i = 0; i < palettes.size(); i++) {
                remapPalette(palettes.getList(i), remap, remapped);
            }
        } else {
            remapPalette(tag.getList("palette", Tag.TAG_COMPOUND), remap, remapped);
        }
        // The old mod's block entity data means nothing to the new block (and still names
        // the old block entity type): let the new block start from its defaults
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (remapped.contains(block.getInt("state"))) {
                block.remove("nbt");
            }
        }
    }

    private static void remapPalette(ListTag palette, Map<ResourceLocation, ResourceLocation> remap, IntSet remapped) {
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            ResourceLocation name = ResourceLocation.tryParse(entry.getString("Name"));
            ResourceLocation target = name == null ? null : remap.get(name);
            if (target != null) {
                entry.putString("Name", target.toString());
                // The old block's properties mean nothing to the new one: use its defaults
                entry.remove("Properties");
                remapped.add(i);
            }
        }
    }

    /** Rewrites entity ids in the template's entities and in block entities (spawners). */
    private static void remapEntities(CompoundTag tag, Map<ResourceLocation, ResourceLocation> remap) {
        if (remap.isEmpty()) {
            return;
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            remapIds(blocks.getCompound(i).getCompound("nbt"), remap);
        }
        ListTag entities = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            remapIds(entities.getCompound(i).getCompound("nbt"), remap);
        }
    }

    /** Every {@code id} string anywhere below the tag: spawners nest them in SpawnData / SpawnPotentials. */
    private static void remapIds(Tag tag, Map<ResourceLocation, ResourceLocation> remap) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if ("id".equals(key) && child instanceof StringTag id) {
                    ResourceLocation from = ResourceLocation.tryParse(id.getAsString());
                    ResourceLocation to = from == null ? null : remap.get(from);
                    if (to != null) {
                        compound.putString(key, to.toString());
                    }
                } else {
                    remapIds(child, remap);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                remapIds(child, remap);
            }
        }
    }
}
