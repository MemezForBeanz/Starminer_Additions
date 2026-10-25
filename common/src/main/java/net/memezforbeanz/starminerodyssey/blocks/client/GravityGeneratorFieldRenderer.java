package net.memezforbeanz.starminerodyssey.blocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.gravity.CardinalRotation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Field overlay for the Gravity Generator, one Amethyst-style visualization per mode (scrolling
 * arrows on the field walls, brackets on the faces). The loader-side AzureLib renderers chain
 * {@link #renderGeneratorOverlay} after the model.
 */
public class GravityGeneratorFieldRenderer extends FieldOverlayRenderer<GravityGeneratorBlockEntity> {

    public GravityGeneratorFieldRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderForceField(GravityGeneratorBlockEntity blockEntity, PoseStack poseStack,
                                    MultiBufferSource bufferSource, float animation) {
        renderGeneratorField(blockEntity, poseStack, bufferSource, animation);
    }

    /**
     * Full generator overlay (visibility check, centering and animation included) — the
     * hook loader-side renderers chain after the block model.
     */
    public static void renderGeneratorOverlay(GravityGeneratorBlockEntity blockEntity, float partialTick,
                                              PoseStack poseStack, MultiBufferSource buffer) {
        if (!FieldVisibility.shouldShow(blockEntity)) {
            return;
        }
        float animation = animationPhase(blockEntity, partialTick);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        renderGeneratorField(blockEntity, poseStack, buffer, animation);
        poseStack.popPose();
    }

    /** Draw the field for the generator's mode with the pose stack at the block's center. */
    public static void renderGeneratorField(GravityGeneratorBlockEntity blockEntity, PoseStack poseStack,
                                            MultiBufferSource bufferSource, float animation) {
        switch (blockEntity.getMode()) {
            case DIRECTIONAL -> renderDirectionalField(blockEntity, poseStack, bufferSource, animation);
            case WALL -> renderWallField(blockEntity, poseStack, bufferSource, animation);
            case CYLINDER -> renderCylinderField(blockEntity, poseStack, bufferSource, animation);
            case PLANET -> renderPlanetField((float) blockEntity.getFieldRadius(), blockEntity.isRepelling(),
                    poseStack, bufferSource, animation);
        }
    }

    /** One shared gravity vector: arrows along its nearest cardinal on the four side walls. */
    private static void renderDirectionalField(GravityGeneratorBlockEntity blockEntity, PoseStack poseStack,
                                               MultiBufferSource bufferSource, float animation) {
        Vec3 gravity = blockEntity.getGravityVector();
        Direction direction = Direction.getNearest(gravity.x, gravity.y, gravity.z);
        float r = (float) blockEntity.getFieldRadius() + .5f - SMIDGE;
        float length = 2 * r;

        // Corners in "player space" (gravity along -Y): p0..p3 far cap, p4..p7 pull cap
        ArrayList<Vector3f> p = new ArrayList<>();
        p.add(new Vector3f(-r, r, -r));
        p.add(new Vector3f(-r, r, r));
        p.add(new Vector3f(r, r, r));
        p.add(new Vector3f(r, r, -r));
        p.add(new Vector3f(-r, -r, -r));
        p.add(new Vector3f(-r, -r, r));
        p.add(new Vector3f(r, -r, r));
        p.add(new Vector3f(r, -r, -r));
        p.replaceAll(vertex -> CardinalRotation.vecPlayerToWorld(vertex, direction));

        VertexConsumer arrowBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(ARROW_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float h0 = -animation;
        float h1 = length - animation;

        addVertex(pose, normal, arrowBuffer, p.get(0), -r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(1), r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(5), r, h1);
        addVertex(pose, normal, arrowBuffer, p.get(4), -r, h1);

        addVertex(pose, normal, arrowBuffer, p.get(1), -r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(2), r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(6), r, h1);
        addVertex(pose, normal, arrowBuffer, p.get(5), -r, h1);

        addVertex(pose, normal, arrowBuffer, p.get(2), r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(3), -r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(7), -r, h1);
        addVertex(pose, normal, arrowBuffer, p.get(6), r, h1);

        addVertex(pose, normal, arrowBuffer, p.get(3), r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(0), -r, h0);
        addVertex(pose, normal, arrowBuffer, p.get(4), -r, h1);
        addVertex(pose, normal, arrowBuffer, p.get(7), r, h1);

        VertexConsumer wallBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(WALL_TEXTURE));
        renderFaceEdges(pose, normal, wallBuffer, p.get(0), p.get(1), p.get(2), p.get(3));
        renderFaceEdges(pose, normal, wallBuffer, p.get(4), p.get(5), p.get(6), p.get(7));
    }

    /**
     * Amethyst's wall-field visualization: arrows scroll down the field's four side walls
     * toward the face (away from it when repelling), with brackets on the near and far faces.
     */
    private static void renderWallField(GravityGeneratorBlockEntity blockEntity, PoseStack poseStack,
                                        MultiBufferSource bufferSource, float animation) {
        Direction direction = blockEntity.getFace().getOpposite();
        float height = (float) blockEntity.getHeight();
        float width = (float) blockEntity.getWidth();
        float depth = (float) blockEntity.getDepth();

        ArrayList<Vector3f> p = new ArrayList<>();
        p.add(new Vector3f(-.5f * width + SMIDGE, height - SMIDGE, -.5f * depth + SMIDGE));
        p.add(new Vector3f(-.5f * width + SMIDGE, height - SMIDGE, .5f * depth - SMIDGE));
        p.add(new Vector3f(.5f * width - SMIDGE, height - SMIDGE, .5f * depth - SMIDGE));
        p.add(new Vector3f(.5f * width - SMIDGE, height - SMIDGE, -.5f * depth + SMIDGE));
        p.add(new Vector3f(-.5f * width + SMIDGE, SMIDGE, -.5f * depth + SMIDGE));
        p.add(new Vector3f(-.5f * width + SMIDGE, SMIDGE, .5f * depth - SMIDGE));
        p.add(new Vector3f(.5f * width - SMIDGE, SMIDGE, .5f * depth - SMIDGE));
        p.add(new Vector3f(.5f * width - SMIDGE, SMIDGE, -.5f * depth + SMIDGE));
        for (Vector3f vertex : p) {
            Vector3f rotated = CardinalRotation.vecPlayerToWorld(vertex, direction);
            Vector3f shift = direction.step().mul(-.5f);
            rotated.add(shift);
            vertex.set(rotated);
        }

        VertexConsumer arrowBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(ARROW_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float h0 = -animation;
        float h1 = -animation;
        if (blockEntity.isRepelling()) {
            h0 += height;
        } else {
            h1 += height;
        }
        addVertex(pose, normal, arrowBuffer, p.get(0), -.5f * depth, h0);
        addVertex(pose, normal, arrowBuffer, p.get(1), .5f * depth, h0);
        addVertex(pose, normal, arrowBuffer, p.get(5), .5f * depth, h1);
        addVertex(pose, normal, arrowBuffer, p.get(4), -.5f * depth, h1);

        addVertex(pose, normal, arrowBuffer, p.get(1), -.5f * width, h0);
        addVertex(pose, normal, arrowBuffer, p.get(2), .5f * width, h0);
        addVertex(pose, normal, arrowBuffer, p.get(6), .5f * width, h1);
        addVertex(pose, normal, arrowBuffer, p.get(5), -.5f * width, h1);

        addVertex(pose, normal, arrowBuffer, p.get(2), .5f * depth, h0);
        addVertex(pose, normal, arrowBuffer, p.get(3), -.5f * depth, h0);
        addVertex(pose, normal, arrowBuffer, p.get(7), -.5f * depth, h1);
        addVertex(pose, normal, arrowBuffer, p.get(6), .5f * depth, h1);

        addVertex(pose, normal, arrowBuffer, p.get(3), .5f * width, h0);
        addVertex(pose, normal, arrowBuffer, p.get(0), -.5f * width, h0);
        addVertex(pose, normal, arrowBuffer, p.get(4), -.5f * width, h1);
        addVertex(pose, normal, arrowBuffer, p.get(7), .5f * width, h1);

        VertexConsumer wallBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(WALL_TEXTURE));
        renderFaceEdges(pose, normal, wallBuffer, p.get(0), p.get(1), p.get(2), p.get(3));
        renderFaceEdges(pose, normal, wallBuffer, p.get(4), p.get(5), p.get(6), p.get(7));
    }

    /**
     * Amethyst's cylinder-field visualization: arrows flow from the outer ring boundary
     * inward to the axis (outward when repelling), with brackets on the four ring faces.
     */
    private static void renderCylinderField(GravityGeneratorBlockEntity blockEntity, PoseStack poseStack,
                                            MultiBufferSource bufferSource, float animation) {
        float radius = (float) blockEntity.getFieldRadius();
        float r = radius + .5f - SMIDGE;
        float w = (float) blockEntity.getWidth() / 2.f - SMIDGE;

        ArrayList<Vector3f> outer = new ArrayList<>();
        switch (blockEntity.getAxis()) {
            case X -> {
                outer.add(new Vector3f(w, r, r));
                outer.add(new Vector3f(w, r, -r));
                outer.add(new Vector3f(w, -r, r));
                outer.add(new Vector3f(w, -r, -r));
                outer.add(new Vector3f(-w, r, r));
                outer.add(new Vector3f(-w, r, -r));
                outer.add(new Vector3f(-w, -r, r));
                outer.add(new Vector3f(-w, -r, -r));
            }
            case Y -> {
                outer.add(new Vector3f(r, w, r));
                outer.add(new Vector3f(r, w, -r));
                outer.add(new Vector3f(r, -w, r));
                outer.add(new Vector3f(r, -w, -r));
                outer.add(new Vector3f(-r, w, r));
                outer.add(new Vector3f(-r, w, -r));
                outer.add(new Vector3f(-r, -w, r));
                outer.add(new Vector3f(-r, -w, -r));
            }
            case Z -> {
                outer.add(new Vector3f(r, r, w));
                outer.add(new Vector3f(r, r, -w));
                outer.add(new Vector3f(r, -r, w));
                outer.add(new Vector3f(r, -r, -w));
                outer.add(new Vector3f(-r, r, w));
                outer.add(new Vector3f(-r, r, -w));
                outer.add(new Vector3f(-r, -r, w));
                outer.add(new Vector3f(-r, -r, -w));
            }
        }

        // The inner ring collapses each vertex onto the axis
        ArrayList<Vector3f> inner = new ArrayList<>();
        for (Vector3f v : outer) {
            inner.add(new Vector3f(v));
        }
        switch (blockEntity.getAxis()) {
            case X -> inner.replaceAll(a -> a.mul(1, 0, 0));
            case Y -> inner.replaceAll(a -> a.mul(0, 1, 0));
            case Z -> inner.replaceAll(a -> a.mul(0, 0, 1));
        }

        List<IntPair> edges = List.of(
                new IntPair(0, 1), new IntPair(3, 2), new IntPair(5, 4), new IntPair(6, 7),
                new IntPair(0, 2), new IntPair(3, 1), new IntPair(6, 4), new IntPair(5, 7),
                new IntPair(0, 4), new IntPair(5, 1), new IntPair(6, 2), new IntPair(3, 7));

        // Only the four ring faces get brackets; the two axis caps are open
        List<IntFour> faces = List.of(
                new IntFour(1, 3, 7, 5), new IntFour(0, 2, 6, 4),
                new IntFour(4, 5, 7, 6), new IntFour(0, 1, 3, 2));

        VertexConsumer arrowBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(ARROW_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float diagonal = (float) Math.sqrt(2) * radius;

        for (IntPair e : edges) {
            Vec3 i1 = new Vec3(inner.get(e.a()));
            Vec3 i2 = new Vec3(inner.get(e.b()));
            float il = (float) i1.distanceTo(i2) / 2.f;
            Vec3 o1 = new Vec3(outer.get(e.a()));
            Vec3 o2 = new Vec3(outer.get(e.b()));
            float ol = (float) o1.distanceTo(o2) / 2.f;
            if (blockEntity.isRepelling()) {
                addVertex(pose, normal, arrowBuffer, outer.get(e.a()), ol, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, outer.get(e.b()), -ol, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.b()), -il, -animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.a()), il, -animation);
            } else {
                addVertex(pose, normal, arrowBuffer, outer.get(e.a()), ol, -animation);
                addVertex(pose, normal, arrowBuffer, outer.get(e.b()), -ol, -animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.b()), -il, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.a()), il, diagonal - animation);
            }
        }
        VertexConsumer wallBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(WALL_TEXTURE));
        for (IntFour f : faces) {
            renderFaceEdges(pose, normal, wallBuffer,
                    outer.get(f.a()), outer.get(f.b()), outer.get(f.c()), outer.get(f.d()));
        }
    }
}
