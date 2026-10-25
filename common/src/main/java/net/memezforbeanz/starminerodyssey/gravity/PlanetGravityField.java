package net.memezforbeanz.starminerodyssey.gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Planet-style field maths (after Amethyst Gravity's planet field): a cubic field pulling entities toward its source block from every side. Vanilla types only, so it lives in common. */
public final class PlanetGravityField {

    /** Padding in blocks added around the field box when testing entity intersection. */
    public static final double FIELD_MARGIN = 0.5;

    /** Gravity anchors trump every field, like Amethyst Gravity's anchor priority. */
    public static final double ANCHOR_PRIORITY = 300.0;

    private PlanetGravityField() {
    }

    /**
     * The field box: the source block's cube expanded by {@code radius} on every axis.
     */
    public static AABB getFieldBox(Vec3 blockCenter, double radius) {
        return AABB.ofSize(blockCenter, 1, 1, 1).inflate(radius);
    }

    /**
     * Whether an entity's bounding box overlaps the field (with {@link #FIELD_MARGIN}).
     */
    public static boolean intersectsField(AABB fieldBox, AABB entityBox) {
        return fieldBox.inflate(FIELD_MARGIN).intersects(entityBox);
    }

    /**
     * The cardinal pull for an entity: the dominant axis of its offset toward the field centre (Amethyst's farthest-face rule reduces to this).
     *
     * @return the pull direction, or {@code null} at the exact centre
     */
    @Nullable
    public static Direction getPullDirection(Vec3 fieldCenter, Vec3 entityCenter, boolean repel) {
        Vec3 pull = fieldCenter.subtract(entityCenter);
        if (repel) {
            pull = pull.reverse();
        }
        if (pull.lengthSqr() < 1.0E-6) {
            return null;
        }
        return Direction.getNearest(pull.x, pull.y, pull.z);
    }

    /**
     * Amethyst Gravity's rule for fields with a subset of pull directions (the cylinder field's four): the candidate whose outward face offset lies farthest from the entity.
     *
     * @return the pull direction, or {@code null} with no candidates
     */
    @Nullable
    public static Direction getPullDirection(Vec3 fieldCenter, Vec3 entityCenter,
                                             List<Direction> candidates, boolean repel) {
        Direction effective = candidates.stream()
                .max(Comparator.comparingDouble(
                        d -> fieldCenter.add(Vec3.atLowerCornerOf(d.getNormal())).distanceTo(entityCenter)))
                .orElse(null);
        if (effective == null) {
            return null;
        }
        return repel ? effective.getOpposite() : effective;
    }

    /** Priority for a field of the given volume: Amethyst's smallest-field-wins on a highest-priority-wins backend. Strictly decreasing in volume, in (100, 200), below {@link #ANCHOR_PRIORITY}. */
    public static double fieldPriority(double volume) {
        return 100.0 + 100.0 / (1.0 + Math.max(volume, 0.0));
    }
}
