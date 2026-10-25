package net.memezforbeanz.starminerodyssey.shipspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix3d;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the 24 rotations of a cube: the orientations a block structure can take on any
 * axis while staying on the block grid. Vanilla templates only rotate about Y; this lets a
 * spawn's block copy and its ship share any 90-degree orientation, so swapping one for the
 * other is seamless.
 */
public final class CubeRotation {

    /** All 24 proper rotations; index 0 is the identity. */
    private static final List<CubeRotation> ALL = generate();
    public static final CubeRotation IDENTITY = ALL.get(0);

    /** Row-major integer matrix: entry (row, col) at {@code row * 3 + col}. */
    private final int[] m;

    private CubeRotation(int[] m) {
        this.m = m;
    }

    private static List<CubeRotation> generate() {
        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        List<CubeRotation> result = new ArrayList<>(24);
        for (int[] p : permutations) {
            for (int signs = 0; signs < 8; signs++) {
                int[] m = new int[9];
                for (int row = 0; row < 3; row++) {
                    m[row * 3 + p[row]] = (signs >> row & 1) == 0 ? 1 : -1;
                }
                if (determinant(m) == 1) {
                    result.add(new CubeRotation(m));
                }
            }
        }
        return result; // permutation {0,1,2} with all-positive signs comes first: identity
    }

    private static int determinant(int[] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
                + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    public static CubeRotation random(RandomSource random) {
        return ALL.get(random.nextInt(ALL.size()));
    }

    public boolean isIdentity() {
        return this == IDENTITY;
    }

    public BlockPos apply(Vec3i v) {
        return new BlockPos(
                m[0] * v.getX() + m[1] * v.getY() + m[2] * v.getZ(),
                m[3] * v.getX() + m[4] * v.getY() + m[5] * v.getZ(),
                m[6] * v.getX() + m[7] * v.getY() + m[8] * v.getZ());
    }

    public Direction apply(Direction direction) {
        BlockPos n = apply(direction.getNormal());
        Direction rotated = Direction.fromDelta(n.getX(), n.getY(), n.getZ());
        return rotated != null ? rotated : direction;
    }

    /**
     * Where the unit block cell's minimum corner lands relative to the rotated cell origin:
     * a block at {@code t} occupies {@code [t, t+1]}, whose rotation spans
     * {@code [R*t + cellOffset, R*t + cellOffset + 1]}.
     */
    public BlockPos cellOffset() {
        return new BlockPos(Math.min(0, m[0]) + Math.min(0, m[1]) + Math.min(0, m[2]),
                Math.min(0, m[3]) + Math.min(0, m[4]) + Math.min(0, m[5]),
                Math.min(0, m[6]) + Math.min(0, m[7]) + Math.min(0, m[8]));
    }

    /** Minimum corner of the box {@code [0, size]} after rotation (each component is 0 or -size). */
    public BlockPos minCorner(Vec3i size) {
        return new BlockPos(
                Math.min(0, m[0] * size.getX()) + Math.min(0, m[1] * size.getY()) + Math.min(0, m[2] * size.getZ()),
                Math.min(0, m[3] * size.getX()) + Math.min(0, m[4] * size.getY()) + Math.min(0, m[5] * size.getZ()),
                Math.min(0, m[6] * size.getX()) + Math.min(0, m[7] * size.getY()) + Math.min(0, m[8] * size.getZ()));
    }

    /** The size of a {@code size} box after rotation. */
    public Vec3i rotatedSize(Vec3i size) {
        BlockPos r = apply(size);
        return new Vec3i(Math.abs(r.getX()), Math.abs(r.getY()), Math.abs(r.getZ()));
    }

    public Quaterniond quaternion() {
        // JOML's constructor takes columns: (m00, m01, m02) is the first column
        return new Matrix3d(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
                .getNormalizedRotation(new Quaterniond());
    }

    /**
     * Best-effort state rotation: every direction property and block axis property is mapped
     * through the rotation when the block allows the result. A horizontal-only facing that
     * would point up or down keeps its value; shapes that depend on up/down (stairs and slab
     * halves) aren't flipped.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockState apply(BlockState state) {
        if (isIdentity()) {
            return state;
        }
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty facing) {
                Direction rotated = apply(state.getValue(facing));
                if (facing.getPossibleValues().contains(rotated)) {
                    result = result.setValue(facing, rotated);
                }
            } else if (property instanceof EnumProperty enumProperty && enumProperty.getValueClass() == Direction.Axis.class) {
                Direction.Axis axis = (Direction.Axis) state.getValue(enumProperty);
                Direction.Axis rotated = apply(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)).getAxis();
                if (enumProperty.getPossibleValues().contains(rotated)) {
                    result = result.setValue(enumProperty, rotated);
                }
            }
        }
        return result;
    }
}
