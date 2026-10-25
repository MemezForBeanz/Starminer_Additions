package net.memezforbeanz.starminerodyssey.shipspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;

/**
 * Loader-facing abstraction over the ship backend, mirroring
 * {@link net.memezforbeanz.starminerodyssey.gravity.GravityBridge}. The Forge module wires
 * this to Valkyrien Skies; without a backend spawned structures simply stay as blocks.
 */
public interface ShipBridge {

    /**
     * Whether a structure may be placed in this box. Lets the backend keep spawns clear of
     * things that would immediately grab the ship, like Cosmos planets under Starlance.
     * A refused cell is asked again on later passes.
     */
    default boolean canPlace(ServerLevel level, BoundingBox box) {
        return true;
    }

    /** Whether {@link #spawnAsShip} works; without it ship-mode spawns fall back to blocks. */
    default boolean supportsShips() {
        return false;
    }

    /**
     * Build the template upright, directly as a new static ship, without placing anything in
     * the world first, then pose it so template point {@code x} sits at
     * {@code templateZero + rotation * x} in the world (exactly over a block copy laid out
     * with the same rotation). A non-null {@code tilt} then turns the ship further about its
     * centre of mass. {@code nearBlock} is a world block in the structure, where the ship is
     * created before posing. Entities are left to the caller (the settings ignore them).
     * Returns the ship id, or -1.
     */
    default long spawnAsShip(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings,
                             BlockPos nearBlock, Vec3 templateZero, Quaterniondc rotation,
                             @Nullable Quaterniondc tilt, RandomSource random) {
        return -1;
    }

    /** The ship's world position, or null once it no longer exists in this level. */
    @Nullable
    default Vec3 shipPosition(ServerLevel level, long shipId) {
        return null;
    }

    /** Freeze or release the ship. Returns whether that changed anything. */
    default boolean setStatic(ServerLevel level, long shipId, boolean isStatic) {
        return false;
    }

    /** Set a dynamic ship's linear (blocks/s) and angular (rad/s, world axis) velocity. */
    default void kick(ServerLevel level, long shipId, Vec3 velocity, Vec3 omega) {
    }

    /**
     * Spin the ship about a world axis at {@code radiansPerSecond} by posing it directly
     * each physics tick rather than simulating it. Idempotent; not saved, so callers
     * reapply it after a restart.
     */
    default void spin(ServerLevel level, long shipId, Vec3 axis, double radiansPerSecond) {
    }

    /** Per-pass upkeep for a tracked ship, e.g. keeping other mods from claiming it. */
    default void maintain(ServerLevel level, long shipId) {
    }

    /** Delete the ship and its blocks, without drops. */
    default void deleteShip(ServerLevel level, long shipId) {
    }

    /** The id of the ship whose blocks include this position, or -1. */
    default long shipIdAt(ServerLevel level, BlockPos pos) {
        return -1;
    }

    static ShipBridge get() {
        return Holder.instance;
    }

    /** Called once by each loader module during mod construction. */
    static void set(ShipBridge bridge) {
        Holder.instance = bridge;
    }

    class Holder {
        private static ShipBridge instance = new ShipBridge() {
        };
    }
}
