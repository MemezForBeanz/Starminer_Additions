package net.memezforbeanz.starminerodyssey.blocks;

import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.PlanetGravityField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shared base for gravity field blocks: entity gathering, ship-aware containment, per-tick
 * application through the {@link GravityBridge}, radius/visibility persistence and client sync.
 * Subclasses shape the field ({@link #getFieldBox}), pick each entity's pull ({@link #applyPull})
 * and add upkeep through {@link #serverTick} and {@link #isFieldActive}.
 */
public abstract class AbstractGravityFieldBlockEntity extends BlockEntity {

    // Amethyst Gravity's visibility modes, in its order.
    public static final int VISIBILITY_WITH_GLASSES = 0;
    public static final int VISIBILITY_ALWAYS = 1;
    public static final int VISIBILITY_NEVER = 2;
    public static final int VISIBILITY_MODES = 3;

    private static final String RADIUS_KEY = "Radius";
    private static final String VISIBILITY_KEY = "Visibility";

    /** A rotated (on-ship) field box fits in a world box sqrt(3) times its local half-extent. */
    private static final double SHIP_SEARCH_FACTOR = Math.sqrt(3.0);

    /** Loaded fields per level, for aligning entities as they spawn. */
    private static final Map<Level, Set<AbstractGravityFieldBlockEntity>> LOADED =
            Collections.synchronizedMap(new WeakHashMap<>());

    protected double radius;
    protected int visibility;
    protected int tickCounter = 0;
    private boolean samplerRegistered = false;
    /** Whether the block is on a ship, refreshed each tick for the gravity sampler. */
    private boolean samplerOnShip = false;

    protected AbstractGravityFieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                              double defaultRadius, int defaultVisibility) {
        super(type, pos, state);
        this.radius = defaultRadius;
        this.visibility = defaultVisibility;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public double getFieldRadius() {
        return radius;
    }

    public int getVisibility() {
        return visibility;
    }

    /** The field box in the generator's local (block/ship) frame. */
    public AABB getFieldBox() {
        return PlanetGravityField.getFieldBox(Vec3.atCenterOf(getBlockPos()), radius);
    }

    /** The field box's volume — drives fuel cost and field-vs-field competition. */
    public double getFieldVolume() {
        AABB box = getFieldBox();
        return box.getXsize() * box.getYsize() * box.getZsize();
    }

    /** Priority of this field's pull: Amethyst Gravity's smallest field wins. */
    protected double fieldPriority() {
        return PlanetGravityField.fieldPriority(getFieldVolume());
    }

    /** Whether the field projects gravity: the {@code ACTIVE} state, which subclasses AND with their upkeep. */
    public boolean isFieldActive() {
        BlockState state = getBlockState();
        return !state.hasProperty(AbstractGravityFieldBlock.ACTIVE)
                || state.getValue(AbstractGravityFieldBlock.ACTIVE);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public final void tick(Level level, BlockPos pos, BlockState state) {
        tickCounter++;
        samplerOnShip = GravityBridge.get().isOnShip(level, pos);
        if (!samplerRegistered) {
            GravityBridge.get().registerGravitySampler(level, this, this::accelerationAt, this::fieldPriority);
            LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            samplerRegistered = true;
        }
        if (level.isClientSide()) {
            clientTick(level, pos, state);
            if (isFieldActive()) {
                // The client only needs players (the local player predicts its own gravity)
                applyFieldGravity(level, Player.class);
            }
        } else {
            serverTick(level, pos, state);
            if (isFieldActive()) {
                applyFieldGravity(level, Entity.class);
            }
        }
    }

    /** Per-tick client hook (animations etc.). Runs before the field applies. */
    protected void clientTick(Level level, BlockPos pos, BlockState state) {
    }

    /** Per-tick server hook (fuel, energy). Runs before the field applies. */
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
    }

    // ------------------------------------------------------------------
    // Field application
    // ------------------------------------------------------------------

    private void applyFieldGravity(Level level, Class<? extends Entity> type) {
        GravityBridge bridge = GravityBridge.get();
        AABB searchBox = getWorldSearchBox(bridge);
        if (searchBox == null) {
            return;
        }
        boolean onShip = bridge.isOnShip(level, getBlockPos());
        double targetGravity = targetGravity();
        for (Entity entity : level.getEntitiesOfClass(type, searchBox)) {
            if (!bridge.canChangeGravity(entity) || !isInField(bridge, entity, onShip)) {
                continue;
            }
            if (followsGravityField(entity)) {
                // Projectiles have no gravity frame: they fly through the field's sampler
                continue;
            }
            applyPull(bridge, entity, onShip);
            if (!Double.isNaN(targetGravity)) {
                bridge.applyStrengthOverride(entity, targetGravity, fieldPriority());
            }
        }
    }

    /**
     * The absolute gravity strength (1.0 = overworld) entities in the field feel, replacing
     * planet gravity; {@code NaN} leaves strength alone (the default).
     */
    protected double targetGravity() {
        return Double.NaN;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && samplerRegistered) {
            GravityBridge.get().unregisterGravitySampler(level, this);
            Set<AbstractGravityFieldBlockEntity> loaded = LOADED.get(level);
            if (loaded != null) {
                loaded.remove(this);
            }
        }
        samplerRegistered = false;
    }

    /**
     * Put a just-spawned entity straight into the gravity of the fields around it, so it doesn't
     * spawn upright inside a planet's side and suffocate before the first field tick turns it:
     * the fields apply now, the frame is taken with no flip, and the feet are moved onto the face
     * the spawn position was resting against.
     */
    public static void alignSpawnedEntity(Level level, Entity entity) {
        Set<AbstractGravityFieldBlockEntity> loaded = LOADED.get(level);
        GravityBridge bridge = GravityBridge.get();
        if (loaded == null || level.isClientSide() || !bridge.canChangeGravity(entity) || followsGravityField(entity)) {
            return;
        }
        // Spawned in a ship's shipyard (an egg used on a ship): to world space now, as VS2 would later
        BlockPos at = entity.blockPosition();
        if (bridge.isOnShip(level, at)) {
            Vec3 world = bridge.shipToWorldPosition(level, at, entity.position());
            if (world == null) {
                return;
            }
            entity.moveTo(world.x, world.y, world.z, entity.getYRot(), entity.getXRot());
        }
        AbstractGravityFieldBlockEntity winner = null;
        List<AbstractGravityFieldBlockEntity> fields;
        synchronized (LOADED) {
            fields = new ArrayList<>(loaded);
        }
        for (AbstractGravityFieldBlockEntity field : fields) {
            if (field.isRemoved() || field.level != level || !field.isFieldActive()) {
                continue;
            }
            boolean onShip = bridge.isOnShip(level, field.getBlockPos());
            if (!field.isInField(bridge, entity, onShip)) {
                continue;
            }
            field.applyPull(bridge, entity, onShip);
            double target = field.targetGravity();
            if (!Double.isNaN(target)) {
                bridge.applyStrengthOverride(entity, target, field.fieldPriority());
            }
            if (winner == null || field.fieldPriority() > winner.fieldPriority()) {
                winner = field;
            }
        }
        if (winner == null) {
            return;
        }
        Vec3 spawnDown = bridge.gravityDown(entity);
        bridge.settleGravity(entity);
        Vec3 down = bridge.gravityDown(entity);
        if (spawnDown.dot(down) < 0.99) {
            winner.snapFeetToFace(bridge, entity, spawnDown, down);
        }
    }

    /**
     * Spawns stand the feet on the bottom of a cell; in the new frame move them to the cell's face
     * toward the new down, when that face is solid. Run in the field's frame (shipyard on a ship).
     */
    private void snapFeetToFace(GravityBridge bridge, Entity entity, Vec3 spawnDown, Vec3 down) {
        if (level == null) {
            return;
        }
        boolean onShip = bridge.isOnShip(level, getBlockPos());
        Vec3 feet = onShip ? bridge.worldToShipPosition(level, getBlockPos(), entity.position()) : entity.position();
        Vec3 oldDown = onShip ? bridge.worldToShipDirection(level, getBlockPos(), spawnDown) : spawnDown;
        Vec3 newDown = onShip ? bridge.worldToShipDirection(level, getBlockPos(), down) : down;
        if (feet == null || oldDown == null || newDown == null) {
            return;
        }
        BlockPos cell = BlockPos.containing(feet.subtract(oldDown.normalize().scale(0.01)));
        Direction face = Direction.getNearest(newDown.x, newDown.y, newDown.z);
        BlockPos below = cell.relative(face);
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return;
        }
        // The whole body must fit in front of the face, not just the feet cell
        int bodyBlocks = Math.max(1, Mth.ceil(entity.getBbHeight()));
        for (int i = 0; i < bodyBlocks; i++) {
            BlockPos body = cell.relative(face.getOpposite(), i);
            if (!level.getBlockState(body).getCollisionShape(level, body).isEmpty()) {
                return;
            }
        }
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 snapped = Vec3.atCenterOf(cell).add(normal.scale(0.5 - 0.01));
        Vec3 world = onShip ? bridge.shipToWorldPosition(level, getBlockPos(), snapped) : snapped;
        if (world != null) {
            entity.moveTo(world.x, world.y, world.z, entity.getYRot(), entity.getXRot());
        }
    }

    /** Projectiles that accelerate along the field at their position instead of standing on its faces. */
    private static boolean followsGravityField(Entity entity) {
        return entity instanceof AbstractArrow || entity instanceof ThrowableProjectile
                || entity instanceof LlamaSpit || entity instanceof AbstractHurtingProjectile;
    }

    /** A local-frame pull at a point: its direction and strength in g. */
    protected record LocalPull(Vec3 direction, double strength) {
    }

    /** The field's pull at a point of its local frame (inside the field box), or null for none. */
    @Nullable
    protected LocalPull localPullAt(Vec3 localPos) {
        return null;
    }

    /**
     * The field's gravitational acceleration at a world point, in g (target gravity, when set,
     * replaces the field's own strength), or null outside the field or while it's off.
     */
    @Nullable
    public Vec3 accelerationAt(Vec3 worldPos) {
        if (level == null || isRemoved() || !isFieldActive()) {
            return null;
        }
        GravityBridge bridge = GravityBridge.get();
        Vec3 local = samplerOnShip ? bridge.worldToShipPosition(level, getBlockPos(), worldPos) : worldPos;
        if (local == null || !getFieldBox().contains(local)) {
            return null;
        }
        LocalPull pull = localPullAt(local);
        if (pull == null || pull.direction().lengthSqr() < 1.0E-12) {
            return null;
        }
        Vec3 world = localPullToWorld(bridge, pull.direction(), samplerOnShip);
        if (world == null) {
            return null;
        }
        double target = targetGravity();
        return world.normalize().scale(Double.isNaN(target) ? pull.strength() : target);
    }

    /** Apply this field's gravity to one entity known to be inside the field. */
    protected abstract void applyPull(GravityBridge bridge, Entity entity, boolean onShip);

    /** The world-frame direction this field would pull the entity, for the detection collider; null when undeterminable. */
    @Nullable
    protected abstract Vec3 pullWorldDirection(GravityBridge bridge, Entity entity, boolean onShip);

    /** A local-frame pull direction rotated to world space; identity off-ship. */
    @Nullable
    protected Vec3 localPullToWorld(GravityBridge bridge, Vec3 localPull, boolean onShip) {
        if (onShip && level != null) {
            return bridge.shipToWorldDirection(level, getBlockPos(), localPull);
        }
        return localPull;
    }

    /** The entity's centre in the field's local frame (ship space on a ship); null if the ship transform fails. */
    @Nullable
    protected Vec3 localEntityCenter(GravityBridge bridge, Entity entity, boolean onShip) {
        AABB body = localBodyBox(bridge, entity, onShip);
        return body != null ? body.getCenter() : null;
    }

    /**
     * The entity's body in the field's local frame. Off-ship that's the hitbox. On a ship the hitbox
     * stays world-upright, so the body is rebuilt from the feet along the entity's up, snapped to a
     * ship axis, like VS-Gravity's ship collision box.
     */
    @Nullable
    protected AABB localBodyBox(GravityBridge bridge, Entity entity, boolean onShip) {
        if (!onShip || level == null) {
            return entity.getBoundingBox();
        }
        Vec3 feet = bridge.worldToShipPosition(level, getBlockPos(), entity.position());
        Vec3 down = localGravityDown(bridge, entity, true);
        if (feet == null || down == null || down.lengthSqr() < 1.0E-8) {
            return null;
        }
        Direction up = Direction.getNearest(-down.x, -down.y, -down.z);
        double half = entity.getBbWidth() / 2;
        Vec3 head = feet.add(Vec3.atLowerCornerOf(up.getNormal()).scale(entity.getBbHeight()));
        return new AABB(feet, head).inflate(up.getAxis() == Direction.Axis.X ? 0 : half,
                up.getAxis() == Direction.Axis.Y ? 0 : half, up.getAxis() == Direction.Axis.Z ? 0 : half);
    }

    /** How close (blocks from the hitbox) a surface must be to count as near; farther out the entity free-falls. */
    public static final double SURFACE_DISTANCE = 2.0;

    /** The probe also reaches this many ticks of travel ahead: the field's verdict applies a tick later. */
    private static final double SURFACE_LOOKAHEAD_TICKS = 2.0;
    /** Most speed counted toward the lookahead (blocks/tick). */
    private static final double MAX_LOOKAHEAD_SPEED = 4.0;

    /** The face a ray from the entity along {@code localDir} hits, and its distance from the hitbox. */
    protected record SurfaceHit(Direction face, double gap) {
        /** The direction to stand on that face, in the field's local frame. */
        public Direction standFace() {
            return face.getOpposite();
        }
    }

    /** Cast from the hitbox centre along {@code localDir} (ship space on a ship) for a solid block within reach; fluids ignored. Null if none. */
    @Nullable
    protected SurfaceHit surfaceAhead(GravityBridge bridge, Entity entity, boolean onShip, Vec3 localDir) {
        if (level == null || localDir.lengthSqr() < 1.0E-8) {
            return null;
        }
        Vec3 dir = localDir.normalize();
        AABB box = localBodyBox(bridge, entity, onShip);
        if (box == null) {
            return null;
        }
        Vec3 start = box.getCenter();
        double extent = Math.abs(dir.x) * box.getXsize() / 2 + Math.abs(dir.y) * box.getYsize() / 2
                + Math.abs(dir.z) * box.getZsize() / 2;
        double speed = Math.max(entity.getDeltaMovement().length(),
                entity.position().distanceTo(new Vec3(entity.xOld, entity.yOld, entity.zOld)));
        double reach = SURFACE_DISTANCE + SURFACE_LOOKAHEAD_TICKS * Math.min(speed, MAX_LOOKAHEAD_SPEED);
        BlockHitResult hit = level.clip(new ClipContext(start, start.add(dir.scale(extent + reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new SurfaceHit(hit.getDirection(), Math.max(hit.getLocation().distanceTo(start) - extent, 0));
    }

    /** The entity's gravity (down) in the field's local frame; null if the ship transform fails. */
    @Nullable
    protected Vec3 localGravityDown(GravityBridge bridge, Entity entity, boolean onShip) {
        Vec3 down = bridge.gravityDown(entity);
        if (onShip && level != null) {
            return bridge.worldToShipDirection(level, getBlockPos(), down);
        }
        return down;
    }

    /** The surface under the entity's feet in its current frame, within the same reach as {@link #surfaceAhead}. */
    @Nullable
    protected SurfaceHit surfaceUnderfoot(GravityBridge bridge, Entity entity, boolean onShip) {
        Vec3 down = localGravityDown(bridge, entity, onShip);
        return down != null ? surfaceAhead(bridge, entity, onShip, down) : null;
    }

    /**
     * Apply a field pull in the field's local frame, standing entities on {@code localFace} when a
     * surface is near. On a ship the vectors are rotated to world space and the face stays
     * ship-relative.
     */
    protected void applyLocalFieldPull(GravityBridge bridge, Entity entity, Vec3 localPull, Vec3 localFace,
                                       boolean nearSurface, boolean onShip) {
        double priority = fieldPriority();
        if (onShip && level != null) {
            Vec3 worldPull = bridge.shipToWorldDirection(level, getBlockPos(), localPull);
            Vec3 worldFace = bridge.shipToWorldDirection(level, getBlockPos(), localFace);
            if (worldPull != null && worldFace != null) {
                bridge.applyShipFieldPull(entity, worldPull, worldFace, nearSurface, priority);
            }
            return;
        }
        bridge.applyFieldPull(entity, localPull, localFace, nearSurface, priority);
    }

    /** Pull along a local cardinal: stand on it near a surface (ahead or underfoot), free-fall along it otherwise. */
    protected void applyLocalCardinalPull(GravityBridge bridge, Entity entity, Direction localPull, boolean onShip) {
        Vec3 pull = Vec3.atLowerCornerOf(localPull.getNormal());
        boolean near = entity.onGround() || surfaceAhead(bridge, entity, onShip, pull) != null
                || surfaceUnderfoot(bridge, entity, onShip) != null;
        applyLocalFieldPull(bridge, entity, pull, pull, near, onShip);
    }

    /**
     * Box field check (Amethyst Gravity style) against the backend's detection collider, which stays
     * at the feet through flips (the raw hitbox would swing out of the field). On a ship the check runs
     * in ship space.
     */
    protected boolean isInField(GravityBridge bridge, Entity entity, boolean onShip) {
        if (level == null) {
            return false;
        }
        AABB fieldBox = getFieldBox();
        AABB collider = bridge.gravityEffectCollider(entity, pullWorldDirection(bridge, entity, onShip), onShip);
        if (onShip) {
            collider = bridge.worldBoxToShipBox(level, getBlockPos(), collider);
            if (collider == null) {
                return false;
            }
        }
        return PlanetGravityField.intersectsField(fieldBox, collider);
    }

    /** World-space box to gather candidates; {@link #isInField} does the exact check. */
    @Nullable
    private AABB getWorldSearchBox(GravityBridge bridge) {
        if (level == null) {
            return null;
        }
        AABB fieldBox = getFieldBox();
        Vec3 localCenter = fieldBox.getCenter();
        Vec3 worldCenter = localCenter;
        boolean onShip = bridge.isOnShip(level, getBlockPos());
        if (onShip) {
            worldCenter = bridge.shipToWorldPosition(level, getBlockPos(), localCenter);
            if (worldCenter == null) {
                return null;
            }
        }
        double halfExtent = Math.max(fieldBox.getXsize(), Math.max(fieldBox.getYsize(), fieldBox.getZsize())) / 2.0;
        double reach = (onShip ? halfExtent * SHIP_SEARCH_FACTOR : halfExtent) + PlanetGravityField.FIELD_MARGIN + 1;
        return AABB.ofSize(worldCenter, 0, 0, 0).inflate(reach);
    }

    // ------------------------------------------------------------------
    // Sync + NBT
    // ------------------------------------------------------------------

    protected void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble(RADIUS_KEY, radius);
        tag.putInt(VISIBILITY_KEY, visibility);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(RADIUS_KEY)) {
            radius = tag.getDouble(RADIUS_KEY);
        }
        if (tag.contains(VISIBILITY_KEY)) {
            visibility = tag.getInt(VISIBILITY_KEY);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}
