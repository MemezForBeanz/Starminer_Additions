package net.memezforbeanz.starminerodyssey.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * Abstraction over the gravity backend, so field logic doesn't link against it directly. Both loaders
 * wire it to VS-Gravity ({@link VSGravityBridge}); the defaults are no-ops.
 * <p>
 * Pulls are transient: re-applied every tick while an entity is in a field, competing on
 * {@code priority}, lapsing once no field applies them.
 */
public interface GravityBridge {

    /** Whether the backend allows changing this entity's gravity at all. */
    default boolean canChangeGravity(Entity entity) {
        return false;
    }

    /** Apply a one-tick gravity pull along a cardinal direction (full physics). */
    default void applyCardinalPull(Entity entity, Direction pull, double priority) {
    }

    /**
     * Apply a one-tick field pull: face gravity ({@code landingFace}, or {@code pull}'s nearest) near a
     * surface, free fall along {@code pull} away from one. Backends without free fall just use the face.
     *
     * @param nearSurface a surface is close ahead or underfoot
     */
    default void applyFieldPull(Entity entity, Vec3 pull, @Nullable Vec3 landingFace, boolean nearSurface,
                                double priority) {
        Vec3 face = landingFace != null ? landingFace : pull;
        applyCardinalPull(entity, Direction.getNearest(face.x, face.y, face.z), priority);
    }

    /** {@link #applyFieldPull} for a field on a ship (world-space vectors, ship-relative faces). */
    default void applyShipFieldPull(Entity entity, Vec3 pull, @Nullable Vec3 landingFace, boolean nearSurface,
                                    double priority) {
        applyShipVectorPull(entity, landingFace != null ? landingFace : pull, priority);
    }

    /** Apply a one-tick gravity pull along an arbitrary world-space vector. */
    default void applyVectorPull(Entity entity, Vec3 worldPull, double priority) {
    }

    /** Apply a one-tick pull for a field on a ship. Always ship-relative, even on an axis-aligned ship (cardinal physics can't see ship blocks). */
    default void applyShipVectorPull(Entity entity, Vec3 worldPull, double priority) {
    }

    /**
     * Register a field's gravity as a function of position (projectiles, mob aim): {@code sampler}
     * returns the world-space acceleration in g at a world point, or null outside the field.
     */
    default void registerGravitySampler(Level level, Object key, Function<Vec3, Vec3> sampler,
                                        DoubleSupplier priority) {
    }

    default void unregisterGravitySampler(Level level, Object key) {
    }

    /** Take the effects applied to the entity so far this tick as its gravity right now, with no flip (a spawning entity). */
    default void settleGravity(Entity entity) {
    }

    /** The entity's current gravity ("down") as a world-space unit vector. */
    default Vec3 gravityDown(Entity entity) {
        return new Vec3(0, -1, 0);
    }

    /** Override the entity's gravity strength for one tick (1.0 = overworld), replacing Ad Astra / Cosmic Horizons planet gravity; competes on {@code priority}. */
    default void applyStrengthOverride(Entity entity, double strength, double priority) {
    }

    /** The region of the entity a field must touch to claim it: a collider at the feet that stays put through flips. {@code pullWorldDir} enables the opposite-pull (head contact) rule. */
    default AABB gravityEffectCollider(Entity entity, @Nullable Vec3 pullWorldDir, boolean onShip) {
        return entity.getBoundingBox();
    }

    /** World-space box swept into ship space; identity off-ship. Null if the transform failed. */
    @Nullable
    default AABB worldBoxToShipBox(Level level, BlockPos blockOnShip, AABB worldBox) {
        return worldBox;
    }

    /** Whether the block sits on a Valkyrien Skies ship (or equivalent moving structure). */
    default boolean isOnShip(Level level, BlockPos pos) {
        return false;
    }

    /** Ship space to world space for a position; identity off-ship. Null if the transform failed. */
    @Nullable
    default Vec3 shipToWorldPosition(Level level, BlockPos blockOnShip, Vec3 shipPos) {
        return shipPos;
    }

    /** World space to ship space for a position; identity off-ship. Null if the transform failed. */
    @Nullable
    default Vec3 worldToShipPosition(Level level, BlockPos blockOnShip, Vec3 worldPos) {
        return worldPos;
    }

    /** Ship space to world space for a direction vector; identity off-ship. Null if the transform failed. */
    @Nullable
    default Vec3 shipToWorldDirection(Level level, BlockPos blockOnShip, Vec3 shipDir) {
        return shipDir;
    }

    /** World space to ship space for a direction vector; identity off-ship. Null if the transform failed. */
    @Nullable
    default Vec3 worldToShipDirection(Level level, BlockPos blockOnShip, Vec3 worldDir) {
        return worldDir;
    }

    static GravityBridge get() {
        return Holder.instance;
    }

    /** Called once by each loader module during mod construction. */
    static void set(GravityBridge bridge) {
        Holder.instance = bridge;
    }

    class Holder {
        private static GravityBridge instance = new GravityBridge() {
        };
    }
}
