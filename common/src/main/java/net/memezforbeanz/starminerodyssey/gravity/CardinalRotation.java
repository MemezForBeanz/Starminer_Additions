package net.memezforbeanz.starminerodyssey.gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Player-space to world-space rotation for cardinal gravity directions, mirroring
 * GravityChanger's RotationUtil switch tables (player space: gravity pulls along -Y).
 * Lives in common so field boxes and renderers don't need the gravity backend.
 */
public final class CardinalRotation {

    private CardinalRotation() {
    }

    public static Vec3 vecPlayerToWorld(double x, double y, double z, Direction gravityDirection) {
        return switch (gravityDirection) {
            case DOWN -> new Vec3(x, y, z);
            case UP -> new Vec3(-x, -y, z);
            case NORTH -> new Vec3(x, -z, y);
            case SOUTH -> new Vec3(-x, -z, -y);
            case WEST -> new Vec3(y, -z, -x);
            case EAST -> new Vec3(-y, -z, x);
        };
    }

    public static Vector3f vecPlayerToWorld(Vector3f vec, Direction gravityDirection) {
        return switch (gravityDirection) {
            case DOWN -> new Vector3f(vec.x, vec.y, vec.z);
            case UP -> new Vector3f(-vec.x, -vec.y, vec.z);
            case NORTH -> new Vector3f(vec.x, -vec.z, vec.y);
            case SOUTH -> new Vector3f(-vec.x, -vec.z, -vec.y);
            case WEST -> new Vector3f(vec.y, -vec.z, -vec.x);
            case EAST -> new Vector3f(-vec.y, -vec.z, vec.x);
        };
    }
}
