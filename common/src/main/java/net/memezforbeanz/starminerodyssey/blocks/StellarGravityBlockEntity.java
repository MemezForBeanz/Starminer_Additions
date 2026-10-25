package net.memezforbeanz.starminerodyssey.blocks;

import mod.azure.azurelib.animation.AzAnimatorAccessor;
import mod.azure.azurelib.animation.dispatch.command.AzCommand;
import mod.azure.azurelib.animation.play_behavior.AzPlayBehaviors;
import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.HeliumFuel;
import net.memezforbeanz.starminerodyssey.gravity.PlanetGravityField;
import net.memezforbeanz.starminerodyssey.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Planet gravity field, after Amethyst Gravity's planet field: entities are pulled toward the
 * block from whichever side they're on, so the planet around it can be walked around.
 * <p>
 * On and near the surface gravity is the nearest face; farther out entities free-fall toward the
 * core. Strength falls off with the inverse square of the distance, 1 g at the surface (the first
 * solid block toward the core).
 * <p>
 * Every {@value #FUEL_CHECK_INTERVAL} ticks the server flood-fills the connected Dense Helium and
 * sets the radius: {@value #MIN_FIELD_RADIUS} plus {@value #RANGE_PER_FUEL_MULTIPLIER}x what the pool
 * buys on Amethyst's fuel curve ({@code floor(volume^(2/3))/4}).
 */
public class StellarGravityBlockEntity extends AbstractGravityFieldBlockEntity {

    public static final String CONTROLLER_NAME = "base";
    public static final String IDLE_ANIMATION = "stellar_gravity.idle";

    public static final double MIN_FIELD_RADIUS = 1.0;

    /** How often (in ticks) the helium pool is rescanned and the radius resized. */
    public static final int FUEL_CHECK_INTERVAL = 10;

    /** Range each helium block adds, relative to amethyst's fuel curve (the base radius is unchanged). */
    public static final double RANGE_PER_FUEL_MULTIPLIER = 6.0;

    /** Surface gravity, and the cap anywhere inside it (1.0 = overworld). */
    public static final double SURFACE_GRAVITY = 1.0;


    private boolean idleAnimationStarted = false;

    public StellarGravityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STELLAR_GRAVITY.get(), pos, state,
                MIN_FIELD_RADIUS, VISIBILITY_WITH_GLASSES);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @Override
    protected void clientTick(Level level, BlockPos pos, BlockState state) {
        startIdleAnimation();
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        if (tickCounter % FUEL_CHECK_INTERVAL != 0) {
            return;
        }
        double sustainable = maxSustainableRadius(HeliumFuel.search(level, pos));
        if (sustainable != radius) {
            radius = sustainable;
            syncToClient();
        }
    }

    // ------------------------------------------------------------------
    // Planet gravity
    // ------------------------------------------------------------------

    @Override
    protected void applyPull(GravityBridge bridge, Entity entity, boolean onShip) {
        Vec3 fieldCenter = Vec3.atCenterOf(getBlockPos());
        AABB body = localBodyBox(bridge, entity, onShip);
        Vec3 localDown = localGravityDown(bridge, entity, onShip);
        if (body == null || localDown == null || localDown.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 down = localDown.normalize();
        Vec3 radial = fieldCenter.subtract(standPoint(entity, body, down));
        if (radial.lengthSqr() < 1.0E-6) {
            return;
        }
        // Amethyst's planet rule: the nearest axis toward the core. A flip moves the reference point off the 45° line, so it can't flip back
        Direction face = Direction.getNearest(radial.x, radial.y, radial.z);
        Vec3 faceDown = Vec3.atLowerCornerOf(face.getNormal());
        // Near the planet: face gravity, switching like upstream gravity api. Far from every surface: free fall
        boolean nearSurface = entity.onGround() || surfaceUnderfoot(bridge, entity, onShip) != null
                || surfaceAhead(bridge, entity, onShip, faceDown) != null;
        applyLocalFieldPull(bridge, entity, radial, faceDown, nearSurface, onShip);
        bridge.applyStrengthOverride(entity, gravityAt(entity, bridge, onShip, fieldCenter), fieldPriority());
    }

    /** The point that picks the face: half a body width above the feet. Walking over an edge it crosses the 45° line as the hitbox leaves the block. */
    private static Vec3 standPoint(Entity entity, AABB body, Vec3 down) {
        double halfLength = (Math.abs(down.x) * body.getXsize() + Math.abs(down.y) * body.getYsize()
                + Math.abs(down.z) * body.getZsize()) / 2;
        Vec3 feet = body.getCenter().add(down.scale(halfLength));
        return feet.subtract(down.scale(entity.getBbWidth() / 2));
    }

    /**
     * Inverse-square gravity, {@code SURFACE_GRAVITY * (rSurface / r)^2}, capped at the surface value:
     * {@code r} from the core to the entity's nearest point, {@code rSurface} to the first solid block
     * on the way.
     */
    private double gravityAt(Entity entity, GravityBridge bridge, boolean onShip, Vec3 core) {
        if (level == null || entity.onGround()) {
            // Standing on the planet is standing on its surface
            return SURFACE_GRAVITY;
        }
        AABB box = localBodyBox(bridge, entity, onShip);
        if (box == null) {
            return SURFACE_GRAVITY;
        }
        Vec3 nearest = new Vec3(Mth.clamp(core.x, box.minX, box.maxX), Mth.clamp(core.y, box.minY, box.maxY),
                Mth.clamp(core.z, box.minZ, box.maxZ));
        return strengthAt(nearest, core);
    }

    /** {@link #gravityAt} for a point (local frame). */
    private double strengthAt(Vec3 point, Vec3 core) {
        double r = point.distanceTo(core);
        if (level == null || r < 0.5) {
            return SURFACE_GRAVITY;
        }
        // Fluids ignored: the helium pool is the planet's insides
        BlockHitResult hit = level.clip(new ClipContext(point, core, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, null));
        double surface = hit.getType() == HitResult.Type.MISS ? 0.5 : Math.max(hit.getLocation().distanceTo(core), 0.5);
        double ratio = Math.min(surface / r, 1.0);
        return SURFACE_GRAVITY * ratio * ratio;
    }

    @Override
    protected LocalPull localPullAt(Vec3 localPos) {
        Vec3 core = Vec3.atCenterOf(getBlockPos());
        Vec3 radial = core.subtract(localPos);
        return radial.lengthSqr() < 1.0E-6 ? null : new LocalPull(radial, strengthAt(localPos, core));
    }

    @Override
    protected Vec3 pullWorldDirection(GravityBridge bridge, Entity entity, boolean onShip) {
        Vec3 entityCenter = localEntityCenter(bridge, entity, onShip);
        if (entityCenter == null) {
            return null;
        }
        Direction pull = PlanetGravityField.getPullDirection(
                Vec3.atCenterOf(getBlockPos()), entityCenter, false);
        if (pull == null) {
            return null;
        }
        return localPullToWorld(bridge, Vec3.atLowerCornerOf(pull.getNormal()), onShip);
    }

    // ------------------------------------------------------------------
    // Helium fuel
    // ------------------------------------------------------------------

    /** Helium needed to sustain a field of the given radius. */
    public static int requiredFuel(double radius) {
        double side = 2 * radius + 1;
        return HeliumFuel.requiredFuel(side * side * side);
    }

    /** The field radius the helium sustains: the range Amethyst's fuel curve buys beyond the base radius, times {@link #RANGE_PER_FUEL_MULTIPLIER}. */
    private static double maxSustainableRadius(int foundFuel) {
        double sustainable = MIN_FIELD_RADIUS;
        while (requiredFuel(sustainable + 1) <= foundFuel) {
            sustainable += 1;
        }
        return MIN_FIELD_RADIUS + (sustainable - MIN_FIELD_RADIUS) * RANGE_PER_FUEL_MULTIPLIER;
    }

    // ------------------------------------------------------------------
    // Client animation
    // ------------------------------------------------------------------

    /** The animator only exists after the first render (dispatching earlier is dropped): retry each client tick. */
    private void startIdleAnimation() {
        if (!idleAnimationStarted && AzAnimatorAccessor.getOrNull(this) != null) {
            AzCommand.create(CONTROLLER_NAME, IDLE_ANIMATION, AzPlayBehaviors.LOOP).sendForBlockEntity(this);
            idleAnimationStarted = true;
        }
    }
}
