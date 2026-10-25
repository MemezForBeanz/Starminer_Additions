package net.memezforbeanz.starminerodyssey.shipspawn;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.ExplosionEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.ShipAnchorBlockEntity;
import net.memezforbeanz.starminerodyssey.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime structure spawner for space dimensions (Cosmic Horizons' are flat worlds with every
 * structure set disabled). Each grid cell's structure:
 * <ol>
 *     <li>is placed as plain blocks once its chunk loads near a player: blocks render to view
 *     distance, ships only within VS2's {@code shipLoadDistance};</li>
 *     <li>is rebuilt as a static ship from the template once a player is within
 *     {@code assemble_distance} (built in the shipyard: Starlance refuses assembly up high);</li>
 *     <li>is dynamic within simulation distance, static otherwise, with optional kinematic spin or
 *     physics drift;</li>
 *     <li>is turned back into blocks if nobody changed it and no player was within
 *     {@code release_distance} for {@code recycle_after_seconds}.</li>
 * </ol>
 * Every choice comes from a random seeded by the world seed and the cell, so a cell always yields
 * the same structure, place and rotation (one of 24 with {@code random_rotation}).
 */
public final class ShipSpawner {

    private static final int SPAWN_INTERVAL = 40;
    private static final int ASSEMBLE_RETRY_TICKS = 10;
    /** VS2 holds ships still for {@code shipLoadFreezeSeconds} (5) after loading: kick after that. */
    private static final int KICK_DELAY_TICKS = 140;

    /** Anchors that asked to assemble this tick, run after the level tick rather than mid block entity tick. */
    private static final Map<ResourceKey<Level>, ArrayDeque<BlockPos>> PENDING = new ConcurrentHashMap<>();
    /** Ship id -> game time a player was last within release distance. In memory: a restart resets the clock. */
    private static final Long2LongOpenHashMap LAST_NEAR = new Long2LongOpenHashMap();
    /** Ship id -> game time it last went dynamic, for physics-motion kicks. */
    private static final Long2LongOpenHashMap DYNAMIC_SINCE = new Long2LongOpenHashMap();
    /** Physics-motion ships already kicked this session. */
    private static final LongSet KICKED = new LongOpenHashSet();

    private ShipSpawner() {
    }

    public static void init() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, ShipSpawnDefinitions.INSTANCE,
                new ResourceLocation(StarminerAdditions.MOD_ID, "ship_spawns"));
        TickEvent.SERVER_LEVEL_POST.register(ShipSpawner::tick);
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            LAST_NEAR.clear();
            DYNAMIC_SINCE.clear();
            KICKED.clear();
        });
        // A ship a player has changed (or opened a chest on) is theirs now: never recycle it
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            markModified(level, pos);
            return EventResult.pass();
        });
        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            markModified(level, pos);
            return EventResult.pass();
        });
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            markModified(player.level(), pos);
            return EventResult.pass();
        });
        ExplosionEvent.DETONATE.register((level, explosion, affected) -> {
            for (BlockPos pos : explosion.getToBlow()) {
                if (markModified(level, pos)) {
                    break;
                }
            }
        });
    }

    /** Returns whether the position was on a tracked ship. */
    private static boolean markModified(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        long shipId = ShipBridge.get().shipIdAt(serverLevel, pos);
        if (shipId < 0) {
            return false;
        }
        ShipSpawnSavedData data = ShipSpawnSavedData.get(serverLevel);
        if (data.ship(shipId) == null) {
            return false;
        }
        data.markModified(shipId);
        return true;
    }

    /** Called by an anchor while it ticks (block entities only tick within simulation distance). */
    public static void requestAssembly(ServerLevel level, BlockPos anchor) {
        PENDING.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).add(anchor.immutable());
    }

    private static void tick(ServerLevel level) {
        processPending(level);
        if (level.getGameTime() % SPAWN_INTERVAL != 0) {
            return;
        }
        List<Map.Entry<ResourceLocation, ShipSpawnDefinition>> defs = ShipSpawnDefinitions.forDimension(level.dimension());
        if (defs.isEmpty()) {
            return;
        }
        ShipSpawnSavedData data = ShipSpawnSavedData.get(level);
        // Ships first: a ship released this pass gets its block version back right away.
        // Runs even where spawning is switched off, so nothing is left half-converted
        updateShips(level, data);
        if (!level.players().isEmpty() && ShipSpawnConfig.allowsSpawning(level.dimension())) {
            for (Map.Entry<ResourceLocation, ShipSpawnDefinition> def : defs) {
                spawnAroundPlayers(level, data, def.getKey(), def.getValue());
            }
        }
    }

    private static void updateShips(ServerLevel level, ShipSpawnSavedData data) {
        double activeRange = level.getServer().getPlayerList().getSimulationDistance() * 16.0;
        long now = level.getGameTime();
        ShipBridge bridge = ShipBridge.get();
        for (long shipId : data.shipIds()) {
            Vec3 pos = bridge.shipPosition(level, shipId);
            if (pos == null) {
                forget(data, shipId);
                continue;
            }
            bridge.maintain(level, shipId);
            boolean dynamic = nearestHorizontalSq(level, pos) <= activeRange * activeRange;
            if (bridge.setStatic(level, shipId, !dynamic) && dynamic) {
                DYNAMIC_SINCE.put(shipId, now);
            }

            ShipSpawnSavedData.SpawnedShip ship = data.ship(shipId);
            ShipSpawnDefinition def = ship == null ? null : ShipSpawnDefinitions.get(ship.defId());
            if (def == null || ship.cell() == ShipSpawnSavedData.NO_CELL) {
                continue;
            }
            applyMotion(level, shipId, ship, motionFor(level, ship.defId(), def, ship.cell()), dynamic, now);
            if (def.recycleAfterSeconds() <= 0 || ship.modified()) {
                continue;
            }
            double release = def.releaseDistance();
            if (nearestSq(level, pos) <= release * release || !LAST_NEAR.containsKey(shipId)) {
                LAST_NEAR.put(shipId, now);
            } else if (now - LAST_NEAR.get(shipId) > def.recycleAfterSeconds() * 20L) {
                bridge.deleteShip(level, shipId);
                forget(data, shipId);
                data.unmarkPlaced(ship.defId(), ship.cell());
            }
        }
    }

    private static void forget(ShipSpawnSavedData data, long shipId) {
        data.removeShip(shipId);
        LAST_NEAR.remove(shipId);
        DYNAMIC_SINCE.remove(shipId);
        KICKED.remove(shipId);
    }

    /** A ship's motion, rolled per cell: the same planetoid always moves the same way. */
    private record MotionPlan(ShipSpawnDefinition.MotionType type, Vec3 axis, double radiansPerSecond,
                              Vec3 velocity, @Nullable Quaterniond tilt) {
        static final MotionPlan NONE = new MotionPlan(ShipSpawnDefinition.MotionType.NONE, Vec3.ZERO, 0, Vec3.ZERO, null);
    }

    private static MotionPlan motionFor(ServerLevel level, ResourceLocation defId, ShipSpawnDefinition def, long cell) {
        ShipSpawnDefinition.Motion motion = def.motion();
        if (motion.type() == ShipSpawnDefinition.MotionType.NONE) {
            return MotionPlan.NONE;
        }
        // Its own stream, so adding motion never changes which structure a cell gets
        RandomSource random = RandomSource.create(level.getSeed() ^ cell * 0x9E3779B97F4A7C15L
                ^ (long) def.placement().salt() << 17 ^ defId.hashCode());
        if (random.nextFloat() >= motion.chance()) {
            return MotionPlan.NONE;
        }
        Vec3 axis = randomDirection(random);
        double radians = Math.toRadians(motion.angularSpeed().sample(random));
        Vec3 velocity = randomDirection(random).scale(motion.linearSpeed().sample(random));
        Quaterniond tilt = null;
        if (motion.randomTilt()) {
            Vec3 tiltAxis = randomDirection(random);
            tilt = new Quaterniond().fromAxisAngleRad(tiltAxis.x, tiltAxis.y, tiltAxis.z,
                    random.nextDouble() * Math.PI * 2);
        }
        return new MotionPlan(motion.type(), axis, radians, velocity, tilt);
    }

    private static Vec3 randomDirection(RandomSource random) {
        Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return v.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : v.normalize();
    }

    private static void applyMotion(ServerLevel level, long shipId, ShipSpawnSavedData.SpawnedShip ship,
                                    MotionPlan plan, boolean dynamic, long now) {
        ShipBridge bridge = ShipBridge.get();
        switch (plan.type()) {
            // Reapplied every pass: transform providers aren't saved with the ship
            case KINEMATIC -> bridge.spin(level, shipId, plan.axis(), plan.radiansPerSecond());
            case PHYSICS -> {
                // A ship someone has built on is theirs to steer: no more kicks
                if (!dynamic || ship.modified() || KICKED.contains(shipId)) {
                    return;
                }
                if (!DYNAMIC_SINCE.containsKey(shipId)) {
                    DYNAMIC_SINCE.put(shipId, now); // Already dynamic when we started watching
                } else if (now - DYNAMIC_SINCE.get(shipId) >= KICK_DELAY_TICKS) {
                    bridge.kick(level, shipId, plan.velocity(), plan.axis().scale(plan.radiansPerSecond()));
                    KICKED.add(shipId);
                }
            }
            case NONE -> {
            }
        }
    }

    /** Horizontal, like vanilla simulation distance. */
    private static double nearestHorizontalSq(ServerLevel level, Vec3 pos) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - pos.x;
            double dz = player.getZ() - pos.z;
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return nearest;
    }

    /** Full 3D, like VS2's ship load distance. */
    private static double nearestSq(ServerLevel level, Vec3 pos) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            nearest = Math.min(nearest, player.distanceToSqr(pos));
        }
        return nearest;
    }

    private static void spawnAroundPlayers(ServerLevel level, ShipSpawnSavedData data, ResourceLocation defId,
                                           ShipSpawnDefinition def) {
        int view = level.getServer().getPlayerList().getViewDistance();
        int spacing = def.placement().spacing();
        LongSet visited = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            int minCellX = Math.floorDiv(center.x - view, spacing);
            int maxCellX = Math.floorDiv(center.x + view, spacing);
            int minCellZ = Math.floorDiv(center.z - view, spacing);
            int maxCellZ = Math.floorDiv(center.z + view, spacing);
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    long cell = ChunkPos.asLong(cx, cz);
                    if (visited.add(cell) && !data.isPlaced(defId, cell)) {
                        tryPlace(level, data, defId, def, cell);
                    }
                }
            }
        }
    }

    /**
     * Everything about a cell's structure, re-derivable at any time from the world seed and
     * the cell. {@code random} is left positioned for the placement itself.
     * <p>
     * {@code settings} place the template upright (as in the shipyard); {@code rotation}
     * turns it so that template point {@code x} lands at {@code templateZero + R*x} in the
     * world, which puts the rotated box's minimum corner on {@code origin}.
     */
    private record CellPlan(ResourceLocation templateId, StructureTemplate template, StructurePlaceSettings settings,
                            CubeRotation rotation, BlockPos origin, BlockPos templateZero, BoundingBox box,
                            WorldgenRandom random) {
    }

    /** Null if the cell's chunk isn't loaded or its template is missing. */
    @Nullable
    private static CellPlan plan(ServerLevel level, ResourceLocation defId, ShipSpawnDefinition def, long cell,
                                 boolean requireChunk) {
        ShipSpawnDefinition.Placement placement = def.placement();
        // Same per-cell roll as vanilla RandomSpreadStructurePlacement
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(level.getSeed(), ChunkPos.getX(cell), ChunkPos.getZ(cell), placement.salt());
        int range = placement.spacing() - placement.separation();
        int chunkX = ChunkPos.getX(cell) * placement.spacing() + placement.spreadType().evaluate(random, range);
        int chunkZ = ChunkPos.getZ(cell) * placement.spacing() + placement.spreadType().evaluate(random, range);
        if (requireChunk && !level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        ResourceLocation templateId = pickTemplate(def, random);
        StructureTemplate template = ShipSpawnDefinitions.template(level, defId, def, templateId);
        if (template == null) {
            return null;
        }
        CubeRotation rotation = def.randomRotation() ? CubeRotation.random(random) : CubeRotation.IDENTITY;
        ShipSpawnDefinition.YRange y = def.y();
        BlockPos origin = new BlockPos(
                (chunkX << 4) + random.nextInt(16),
                y.min() + random.nextInt(y.max() - y.min() + 1),
                (chunkZ << 4) + random.nextInt(16));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                // Don't carve the template's air into whatever is already there
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        Vec3i size = template.getSize();
        BlockPos templateZero = origin.subtract(rotation.minCorner(size));
        Vec3i rotatedSize = rotation.rotatedSize(size);
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + rotatedSize.getX() - 1, origin.getY() + rotatedSize.getY() - 1,
                origin.getZ() + rotatedSize.getZ() - 1);
        return new CellPlan(templateId, template, settings, rotation, origin, templateZero, box, random);
    }

    /**
     * Lay the template out as world blocks under the plan's rotation. Vanilla templates only
     * turn about Y, so the processed block list is captured and placed by hand, mirroring
     * vanilla's placement: loot containers get a seed, block entities their saved data.
     */
    private static void placeBlocks(ServerLevel level, CellPlan plan) {
        BlockCaptureProcessor capture = new BlockCaptureProcessor();
        plan.template().placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings()
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).addProcessor(capture).setIgnoreEntities(true),
                plan.random(), Block.UPDATE_CLIENTS);
        CubeRotation rotation = plan.rotation();
        BlockPos base = plan.templateZero().offset(rotation.cellOffset());
        for (StructureTemplate.StructureBlockInfo info : capture.blocks) {
            BlockPos pos = base.offset(rotation.apply(info.pos()));
            level.setBlock(pos, rotation.apply(info.state()), Block.UPDATE_CLIENTS);
            if (info.nbt() != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    CompoundTag tag = info.nbt().copy();
                    if (blockEntity instanceof RandomizableContainerBlockEntity) {
                        tag.putLong("LootTableSeed", plan.random().nextLong());
                    }
                    blockEntity.load(tag);
                    blockEntity.setChanged();
                }
            }
        }
    }

    /** Place the cell's structure if its chunks are loaded; otherwise leave it for a later pass. */
    private static void tryPlace(ServerLevel level, ShipSpawnSavedData data, ResourceLocation defId,
                                 ShipSpawnDefinition def, long cell) {
        CellPlan plan = plan(level, defId, def, cell, true);
        if (plan == null) {
            return;
        }
        BoundingBox box = plan.box();
        if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) {
            StarminerAdditions.LOGGER.warn("Ship spawn {} template {} doesn't fit the build height at {}; skipping",
                    defId, plan.templateId(), plan.origin());
            data.markPlaced(defId, cell);
            return;
        }
        if (!ShipBridge.get().canPlace(level, box)) {
            // Not marked placed: the veto may depend on data that isn't loaded yet
            return;
        }
        if (!chunksLoaded(level, box)) {
            return;
        }

        data.markPlaced(defId, cell);
        if (def.spawnAs() == ShipSpawnDefinition.SpawnMode.SHIP && ShipBridge.get().supportsShips()) {
            spawnShip(level, data, defId, cell, plan, motionFor(level, defId, def, cell).tilt());
            placeEntities(level, plan);
            return;
        }
        placeBlocks(level, plan);
        placeEntities(level, plan);
        placeAnchor(level, box, defId, cell);
    }

    private static void spawnShip(ServerLevel level, ShipSpawnSavedData data, ResourceLocation defId, long cell,
                                  CellPlan plan, @Nullable Quaterniond tilt) {
        long shipId = ShipBridge.get().spawnAsShip(level, plan.template(), plan.settings().setIgnoreEntities(true),
                plan.origin(), Vec3.atLowerCornerOf(plan.templateZero()), plan.rotation().quaternion(), tilt,
                plan.random());
        if (shipId >= 0) {
            data.addShip(shipId, defId, cell);
            LAST_NEAR.put(shipId, level.getGameTime());
        }
    }

    /**
     * Place the template's entities where its blocks are (ship or block copy alike). Vanilla
     * can't turn entities through an arbitrary cube rotation, so only unrotated cells get
     * them: give templates with entities {@code "random_rotation": false}.
     */
    private static void placeEntities(ServerLevel level, CellPlan plan) {
        if (!plan.rotation().isIdentity()) {
            return;
        }
        plan.template().placeInWorld(level, plan.origin(), plan.origin(), new StructurePlaceSettings()
                .addProcessor(EntitiesOnlyProcessor.INSTANCE), plan.random(), Block.UPDATE_CLIENTS);
    }

    private static ResourceLocation pickTemplate(ShipSpawnDefinition def, WorldgenRandom random) {
        int roll = random.nextInt(def.totalWeight());
        for (ShipSpawnDefinition.WeightedTemplate t : def.templates()) {
            roll -= t.weight();
            if (roll < 0) {
                return t.id();
            }
        }
        return def.templates().get(def.templates().size() - 1).id();
    }

    /** Prefer an air pocket inside the box so the structure shows no hole. */
    private static void placeAnchor(ServerLevel level, BoundingBox box, ResourceLocation defId, long cell) {
        BlockPos anchor = null;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).isAir()) {
                anchor = pos.immutable();
                break;
            }
        }
        if (anchor == null) {
            anchor = new BlockPos(box.minX(), box.minY(), box.minZ());
        }
        BlockState original = level.getBlockState(anchor);
        // Drop any block entity first so the anchor doesn't inherit it
        level.removeBlockEntity(anchor);
        level.setBlock(anchor, ModBlocks.SHIP_ANCHOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(anchor) instanceof ShipAnchorBlockEntity be) {
            be.setup(defId, box, original, cell);
        }
    }

    private static void processPending(ServerLevel level) {
        ArrayDeque<BlockPos> queue = PENDING.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        ShipSpawnSavedData data = ShipSpawnSavedData.get(level);
        for (int n = queue.size(); n > 0; n--) {
            BlockPos pos = queue.poll();
            if (level.getBlockEntity(pos) instanceof ShipAnchorBlockEntity anchor) {
                tryConvert(level, data, pos, anchor);
            }
        }
    }

    /** Swap the block version for the ship once a player is close enough for VS2 to show it. */
    private static void tryConvert(ServerLevel level, ShipSpawnSavedData data, BlockPos anchorPos,
                                   ShipAnchorBlockEntity anchor) {
        BoundingBox box = anchor.box();
        ResourceLocation defId = anchor.defId();
        ShipSpawnDefinition def = defId == null ? null : ShipSpawnDefinitions.get(defId);
        ShipBridge bridge = ShipBridge.get();
        if (box == null || def == null || !bridge.supportsShips() || anchor.cell() == ShipSpawnSavedData.NO_CELL) {
            // Nothing to become (definition removed, or no ship backend): stay plain blocks
            level.setBlock(anchorPos, anchor.originalState(), Block.UPDATE_CLIENTS);
            return;
        }
        double assemble = def.assembleDistance();
        if (nearestSq(level, Vec3.atCenterOf(anchorPos)) > assemble * assemble || !chunksLoaded(level, box)) {
            anchor.retryLater(level.getGameTime() + ASSEMBLE_RETRY_TICKS);
            return;
        }
        CellPlan plan = plan(level, defId, def, anchor.cell(), false);
        if (plan == null || !plan.box().equals(box)) {
            // The definition changed since placement; the blocks can't be rebuilt faithfully
            level.setBlock(anchorPos, anchor.originalState(), Block.UPDATE_CLIENTS);
            return;
        }
        clearBlocks(level, box);
        // Entities placed with the blocks are still in the world: only the blocks move.
        // The plan's cube rotation matches the block copy; no extra tilt on top of it
        spawnShip(level, data, defId, anchor.cell(), plan, null);
    }

    /** Remove the block version without drops, contents spilling, or neighbour updates. */
    private static void clearBlocks(ServerLevel level, BoundingBox box) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static boolean chunksLoaded(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
