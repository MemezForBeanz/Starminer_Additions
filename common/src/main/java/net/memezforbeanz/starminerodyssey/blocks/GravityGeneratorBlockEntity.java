package net.memezforbeanz.starminerodyssey.blocks;

import earth.terrarium.botarium.common.energy.base.BotariumEnergyBlock;
import earth.terrarium.botarium.common.energy.impl.SimpleEnergyContainer;
import earth.terrarium.botarium.common.energy.impl.WrappedBlockEnergyContainer;
import mod.azure.azurelib.animation.AzAnimatorAccessor;
import mod.azure.azurelib.animation.dispatch.command.AzCommand;
import mod.azure.azurelib.animation.play_behavior.AzPlayBehaviors;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.gravity.CardinalRotation;
import net.memezforbeanz.starminerodyssey.gravity.GeneratorFieldMode;
import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.PlanetGravityField;
import net.memezforbeanz.starminerodyssey.item.custom.StellarCoreItem;
import net.memezforbeanz.starminerodyssey.registry.ModBlockEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The configurable gravity field machine; the shape is a {@link GeneratorFieldMode} picked in the GUI:
 * <ul>
 *     <li>{@code DIRECTIONAL}: a cube with one gravity vector, a block side (heading) plus an angle in
 *     that side's vertical plane (90° straight down, 0° toward the side, 270° straight up);</li>
 *     <li>{@code WALL}: Amethyst's field generator, a box projected from a face, pulling toward it;</li>
 *     <li>{@code CYLINDER}: Amethyst's cylinder generator, a ring pulling toward its axis;</li>
 *     <li>{@code PLANET}: a cube pulling toward the block from every side.</li>
 * </ul>
 * Repelling reverses the pull. Every mode sets a target gravity (g, replacing planet gravity).
 * <p>
 * Runs on FE: the buffer matches the Stellar Core's capacity and the drain scales with the field
 * volume^(2/3). When a tick can't be paid, the field switches off until power returns.
 */
public class GravityGeneratorBlockEntity extends AbstractGravityFieldBlockEntity
        implements BotariumEnergyBlock<WrappedBlockEnergyContainer> {

    public static final String CONTROLLER_NAME = "base";
    public static final String IDLE_ANIMATION = "generator_idle";

    public static final double DEFAULT_FIELD_RADIUS = 8.0;
    public static final double MIN_FIELD_RADIUS = 1.0;
    public static final double MAX_FIELD_RADIUS = 32.0;

    /** Wall-field extents (height/width/depth) and cylinder width, in blocks. */
    public static final double MIN_EXTENT = 0.1;
    public static final double MAX_EXTENT = 2 * MAX_FIELD_RADIUS + 1;

    /** Target gravity range in g; 1.0 = overworld. */
    public static final double DEFAULT_TARGET_GRAVITY = 1.0;
    public static final double MIN_TARGET_GRAVITY = 0.0;
    public static final double MAX_TARGET_GRAVITY = 3.0;

    /** Standard gravity, for showing the target in m/s². */
    public static final double STANDARD_GRAVITY = 9.807;

    /** Buffer size — equal to the Stellar Core item's capacity. */
    public static final long ENERGY_CAPACITY = StellarCoreItem.CAPACITY;

    /** Max FE accepted per tick from cables/cores. */
    public static final long MAX_ENERGY_INPUT = 10_000_000;

    /** FE per tick = this factor times the field volume to the 2/3 power. */
    public static final long ENERGY_COST_FACTOR = 40;

    private static final String MODE_KEY = "Mode";
    private static final String SIDE_KEY = "Side";
    private static final String ANGLE_KEY = "Angle";
    private static final String FACE_KEY = "Face";
    private static final String AXIS_KEY = "Axis";
    private static final String HEIGHT_KEY = "Height";
    private static final String WIDTH_KEY = "Width";
    private static final String DEPTH_KEY = "Depth";
    private static final String POLARITY_KEY = "Repel";
    private static final String TARGET_GRAVITY_KEY = "TargetGravity";
    private static final String POWERED_KEY = "Powered";

    /** Ticks between opportunistic energy re-syncs so open GUIs stay roughly current. */
    private static final int ENERGY_SYNC_INTERVAL = 20;

    private GeneratorFieldMode mode = GeneratorFieldMode.DIRECTIONAL;
    private Direction side = Direction.NORTH;
    private double angleDegrees = 90.0;
    private Direction face = Direction.UP;
    private Direction.Axis axis = Direction.Axis.Y;
    private double height = 3.0;
    private double width = 3.0;
    private double depth = 3.0;
    private boolean repel = false;
    private double targetGravity = DEFAULT_TARGET_GRAVITY;
    private boolean powered = false;
    private long lastSyncedEnergy = -1;
    private boolean idleAnimationStarted = false;

    private WrappedBlockEnergyContainer energyContainer;

    public GravityGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GRAVITY_GENERATOR.get(), pos, state,
                DEFAULT_FIELD_RADIUS, VISIBILITY_WITH_GLASSES);
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    /** Everything the settings screen edits, in one bundle (also the packet payload). */
    public record Settings(GeneratorFieldMode mode, double radius, double height, double width, double depth,
                           Direction side, double angleDegrees, Direction face, Direction.Axis axis,
                           boolean repel, double targetGravity, int visibility) {
    }

    public Settings getSettings() {
        return new Settings(mode, radius, height, width, depth, side, angleDegrees, face, axis,
                repel, targetGravity, visibility);
    }

    public GeneratorFieldMode getMode() {
        return mode;
    }

    public Direction getSide() {
        return side;
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    /** Wall mode: the face the field projects out of. */
    public Direction getFace() {
        return face;
    }

    /** Cylinder mode: the ring's axis. */
    public Direction.Axis getAxis() {
        return axis;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public double getDepth() {
        return depth;
    }

    public boolean isRepelling() {
        return repel;
    }

    public double getTargetGravity() {
        return targetGravity;
    }

    public boolean isPowered() {
        return powered;
    }

    /** Server-side settings update from the configuration screen. */
    public void updateSettings(ServerPlayer player, Settings settings) {
        this.mode = settings.mode();
        this.radius = clampTenths(settings.radius(), MIN_FIELD_RADIUS, MAX_FIELD_RADIUS);
        this.height = clampTenths(settings.height(), MIN_EXTENT, MAX_EXTENT);
        this.width = clampTenths(settings.width(), MIN_EXTENT, MAX_EXTENT);
        this.depth = clampTenths(settings.depth(), MIN_EXTENT, MAX_EXTENT);
        this.side = settings.side().getAxis().isHorizontal() ? settings.side() : Direction.NORTH;
        this.angleDegrees = Mth.wrapDegrees(settings.angleDegrees() - 180) + 180; // normalize into [0, 360)
        this.face = settings.face();
        this.axis = settings.axis();
        this.repel = settings.repel();
        this.targetGravity = clampTargetGravity(settings.targetGravity());
        this.visibility = Mth.clamp(settings.visibility(), VISIBILITY_WITH_GLASSES, VISIBILITY_NEVER);
        player.displayClientMessage(
                Component.translatable("message." + StarminerAdditions.MOD_ID + ".gravity_generator.cost",
                                energyCostPerTick(getFieldVolume()), getEnergyStorage().getStoredEnergy())
                        .withStyle(powered ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
        syncToClient();
    }

    /** Clamp and snap to the screen's 0.1-block step (NaN-safe: falls back to the minimum). */
    public static double clampTenths(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Mth.clamp(Math.round(value * 10) / 10.0, min, max);
    }

    /** Clamp and snap to the screen's 0.01 g step. */
    public static double clampTargetGravity(double value) {
        if (Double.isNaN(value)) {
            return DEFAULT_TARGET_GRAVITY;
        }
        return Mth.clamp(Math.round(value * 100) / 100.0, MIN_TARGET_GRAVITY, MAX_TARGET_GRAVITY);
    }

    /**
     * The directional-mode gravity in the generator's local frame: the side's heading
     * tilted by the angle in its vertical plane. Always unit length.
     */
    public Vec3 getGravityVector() {
        double theta = Math.toRadians(angleDegrees);
        Vec3 heading = Vec3.atLowerCornerOf(side.getNormal());
        return new Vec3(heading.x * Math.cos(theta), -Math.sin(theta), heading.z * Math.cos(theta));
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    @Override
    public AABB getFieldBox() {
        Vec3 blockCenter = Vec3.atCenterOf(getBlockPos());
        return switch (mode) {
            case DIRECTIONAL, PLANET -> super.getFieldBox();
            case WALL -> {
                // Amethyst's wall box, built in the pull direction's player space
                Direction pull = face.getOpposite();
                Vec3 faceCenter = blockCenter.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
                Vec3 corner1 = CardinalRotation.vecPlayerToWorld(-0.5 * width, 0, -0.5 * depth, pull);
                Vec3 corner2 = CardinalRotation.vecPlayerToWorld(0.5 * width, height, 0.5 * depth, pull);
                yield new AABB(corner1, corner2).move(faceCenter);
            }
            case CYLINDER -> {
                double halfWidth = width / 2.0;
                double reach = radius + 0.5;
                Direction axisDirection = axisDirection(axis);
                Vec3 corner1 = CardinalRotation.vecPlayerToWorld(-reach, -halfWidth, -reach, axisDirection);
                Vec3 corner2 = CardinalRotation.vecPlayerToWorld(reach, halfWidth, reach, axisDirection);
                yield new AABB(corner1, corner2).move(blockCenter);
            }
        };
    }

    /**
     * Field volume for a set of (screen) settings, so the GUI can price a field before
     * it's applied. Matches {@link #getFieldVolume()} for the applied settings.
     */
    public static double fieldVolume(GeneratorFieldMode mode, double radius, double height,
                                     double width, double depth) {
        double side = 2 * radius + 1;
        return switch (mode) {
            case DIRECTIONAL, PLANET -> side * side * side;
            case WALL -> height * width * depth;
            case CYLINDER -> side * side * width;
        };
    }

    /** A representative gravity direction along the axis, matching amethyst's mapping. */
    private static Direction axisDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.WEST;
            case Y -> Direction.DOWN;
            case Z -> Direction.NORTH;
        };
    }

    /** Cylinder mode's four pull candidates: every direction perpendicular to the axis. */
    private List<Direction> ringDirections() {
        return Arrays.stream(Direction.values()).filter(d -> d.getAxis() != axis).toList();
    }

    // ------------------------------------------------------------------
    // Energy
    // ------------------------------------------------------------------

    /** FE drained per tick for a field of the given volume. */
    public static long energyCostPerTick(double volume) {
        return (long) (ENERGY_COST_FACTOR * Math.pow(Math.max(volume, 0), 2.0 / 3.0));
    }

    @Override
    public WrappedBlockEnergyContainer getEnergyStorage() {
        if (energyContainer == null) {
            energyContainer = new WrappedBlockEnergyContainer(this, new SimpleEnergyContainer(ENERGY_CAPACITY) {
                @Override
                public long maxInsert() {
                    return MAX_ENERGY_INPUT;
                }

                @Override
                public long maxExtract() {
                    // Nothing pulls energy out; the generator consumes internally.
                    return 0;
                }
            });
        }
        return energyContainer;
    }

    @Override
    protected void clientTick(Level level, BlockPos pos, BlockState state) {
        startIdleAnimation();
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        long cost = energyCostPerTick(getFieldVolume());
        WrappedBlockEnergyContainer storage = getEnergyStorage();
        boolean nowPowered = storage.internalExtract(cost, true) >= cost;
        if (nowPowered) {
            storage.internalExtract(cost, false);
            setChanged();
        }
        if (nowPowered != powered) {
            powered = nowPowered;
            syncToClient();
        } else if (tickCounter % ENERGY_SYNC_INTERVAL == 0 && storage.getStoredEnergy() != lastSyncedEnergy) {
            // Keep open GUIs roughly current without per-tick packets
            lastSyncedEnergy = storage.getStoredEnergy();
            syncToClient();
        }
    }

    @Override
    public boolean isFieldActive() {
        return super.isFieldActive() && powered;
    }

    /** True while the field projects at a hard size cap — drives the max-range texture. */
    public boolean isRunningAtMaxRange() {
        if (!isFieldActive()) {
            return false;
        }
        return switch (mode) {
            case DIRECTIONAL, PLANET -> radius >= MAX_FIELD_RADIUS;
            case WALL -> Math.max(height, Math.max(width, depth)) >= MAX_EXTENT;
            case CYLINDER -> radius >= MAX_FIELD_RADIUS || width >= MAX_EXTENT;
        };
    }

    // ------------------------------------------------------------------
    // Client animation
    // ------------------------------------------------------------------

    /** The animator only exists after the first render (dispatching earlier is dropped): retry each client tick. Its speed is set per frame by {@code GravityGeneratorAnimator}. */
    private void startIdleAnimation() {
        if (!idleAnimationStarted && AzAnimatorAccessor.getOrNull(this) != null) {
            AzCommand.create(CONTROLLER_NAME, IDLE_ANIMATION, AzPlayBehaviors.LOOP).sendForBlockEntity(this);
            idleAnimationStarted = true;
        }
    }

    // ------------------------------------------------------------------
    // Gravity
    // ------------------------------------------------------------------

    @Override
    protected double targetGravity() {
        return targetGravity;
    }

    @Override
    protected void applyPull(GravityBridge bridge, Entity entity, boolean onShip) {
        if (mode == GeneratorFieldMode.DIRECTIONAL) {
            Vec3 direction = getGravityVector();
            if (onShip && level != null) {
                direction = bridge.shipToWorldDirection(level, getBlockPos(), direction);
                if (direction == null) {
                    return;
                }
                // Ship pulls must never snap to cardinal (world-blind collision)
                bridge.applyShipVectorPull(entity, direction, fieldPriority());
                return;
            }
            bridge.applyVectorPull(entity, direction, fieldPriority());
            return;
        }
        Direction pull = localCardinalPull(bridge, entity, onShip);
        if (pull != null) {
            applyLocalCardinalPull(bridge, entity, pull, onShip);
        }
    }

    @Override
    protected Vec3 pullWorldDirection(GravityBridge bridge, Entity entity, boolean onShip) {
        if (mode == GeneratorFieldMode.DIRECTIONAL) {
            return localPullToWorld(bridge, getGravityVector(), onShip);
        }
        Direction pull = localCardinalPull(bridge, entity, onShip);
        if (pull == null) {
            return null;
        }
        return localPullToWorld(bridge, Vec3.atLowerCornerOf(pull.getNormal()), onShip);
    }

    @Override
    protected LocalPull localPullAt(Vec3 localPos) {
        if (mode == GeneratorFieldMode.DIRECTIONAL) {
            return new LocalPull(getGravityVector(), 1.0);
        }
        Vec3 fieldCenter = Vec3.atCenterOf(getBlockPos());
        Direction pull = switch (mode) {
            case WALL -> repel ? face : face.getOpposite();
            case CYLINDER -> PlanetGravityField.getPullDirection(fieldCenter, localPos, ringDirections(), repel);
            default -> PlanetGravityField.getPullDirection(fieldCenter, localPos, repel);
        };
        return pull == null ? null : new LocalPull(Vec3.atLowerCornerOf(pull.getNormal()), 1.0);
    }

    /** The cardinal-mode pull in the local frame; null when undeterminable. */
    @Nullable
    private Direction localCardinalPull(GravityBridge bridge, Entity entity, boolean onShip) {
        if (mode == GeneratorFieldMode.WALL) {
            return repel ? face : face.getOpposite();
        }
        Vec3 entityCenter = localEntityCenter(bridge, entity, onShip);
        if (entityCenter == null) {
            return null;
        }
        Vec3 fieldCenter = Vec3.atCenterOf(getBlockPos());
        if (mode == GeneratorFieldMode.CYLINDER) {
            return PlanetGravityField.getPullDirection(fieldCenter, entityCenter, ringDirections(), repel);
        }
        return PlanetGravityField.getPullDirection(fieldCenter, entityCenter, repel);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(MODE_KEY, mode.getSerializedName());
        tag.putString(SIDE_KEY, side.getName());
        tag.putDouble(ANGLE_KEY, angleDegrees);
        tag.putString(FACE_KEY, face.getName());
        tag.putString(AXIS_KEY, axis.getName());
        tag.putDouble(HEIGHT_KEY, height);
        tag.putDouble(WIDTH_KEY, width);
        tag.putDouble(DEPTH_KEY, depth);
        tag.putBoolean(POLARITY_KEY, repel);
        tag.putDouble(TARGET_GRAVITY_KEY, targetGravity);
        tag.putBoolean(POWERED_KEY, powered);
        getEnergyStorage().serialize(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = GeneratorFieldMode.byName(tag.getString(MODE_KEY));
        Direction loadedSide = Direction.byName(tag.getString(SIDE_KEY));
        if (loadedSide != null && loadedSide.getAxis().isHorizontal()) {
            side = loadedSide;
        }
        if (tag.contains(ANGLE_KEY)) {
            angleDegrees = tag.getDouble(ANGLE_KEY);
        }
        Direction loadedFace = Direction.byName(tag.getString(FACE_KEY));
        if (loadedFace != null) {
            face = loadedFace;
        }
        Direction.Axis loadedAxis = Direction.Axis.byName(tag.getString(AXIS_KEY));
        if (loadedAxis != null) {
            axis = loadedAxis;
        }
        if (tag.contains(HEIGHT_KEY)) {
            height = tag.getDouble(HEIGHT_KEY);
            width = tag.getDouble(WIDTH_KEY);
            depth = tag.getDouble(DEPTH_KEY);
        }
        repel = tag.getBoolean(POLARITY_KEY);
        // Generators saved before target gravity existed keep normal (1 g) gravity
        targetGravity = tag.contains(TARGET_GRAVITY_KEY) ? tag.getDouble(TARGET_GRAVITY_KEY) : DEFAULT_TARGET_GRAVITY;
        powered = tag.getBoolean(POWERED_KEY);
        getEnergyStorage().deserialize(tag);
    }
}
