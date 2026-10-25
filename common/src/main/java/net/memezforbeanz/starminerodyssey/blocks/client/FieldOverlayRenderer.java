package net.memezforbeanz.starminerodyssey.blocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.AbstractGravityFieldBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for gravity field visualizations, ported from Amethyst Gravity's field generator
 * renderer: translucent purple force-field quads with scrolling arrow lines showing the
 * gravity direction, plus corner brackets on the field's outer faces. Uses only vanilla
 * rendering, so all of it lives in common.
 */
public abstract class FieldOverlayRenderer<BE extends AbstractGravityFieldBlockEntity>
        implements BlockEntityRenderer<BE> {

    public static final ResourceLocation ARROW_TEXTURE =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/misc/arrow_forcefield.png");
    public static final ResourceLocation WALL_TEXTURE =
            new ResourceLocation(StarminerAdditions.MOD_ID, "textures/misc/wall_forcefield.png");

    /** Inset that keeps field faces from z-fighting with the blocks they touch. */
    public static final float SMIDGE = 0.01f;

    protected FieldOverlayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BE blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!FieldVisibility.shouldShow(blockEntity)) {
            return;
        }
        float animation = animationPhase(blockEntity, partialTick);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        renderForceField(blockEntity, poseStack, buffer, animation);
        poseStack.popPose();
    }

    /** Draw the field with the pose stack at the block's center. */
    protected abstract void renderForceField(BE blockEntity, PoseStack poseStack,
                                             MultiBufferSource buffer, float animation);

    @Override
    public boolean shouldRenderOffScreen(BE blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    // ------------------------------------------------------------------
    // Shared drawing helpers (static so loader-side renderers can reuse them)
    // ------------------------------------------------------------------

    /** One-second scroll loop driving the arrow animation. */
    public static float animationPhase(BlockEntity blockEntity, float partialTick) {
        double time = partialTick;
        if (blockEntity.getLevel() != null) {
            time += blockEntity.getLevel().getGameTime();
            time /= 20;
        }
        return (float) (time % 1);
    }

    public static void addVertex(Matrix4f pose, Matrix3f normal, VertexConsumer buffer,
                                 Vector3f vec, float u, float v) {
        addVertex(pose, normal, buffer, vec.x(), vec.y(), vec.z(), u, v);
    }

    public static void addVertex(Matrix4f pose, Matrix3f normal, VertexConsumer buffer,
                                 float x, float y, float z, float u, float v) {
        buffer.vertex(pose, x, y, z)
                .color(.7f, .5f, .9f, .4f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0, 1, 0)
                .endVertex();
    }

    /**
     * Corner brackets along a quad's edges (each point adjacent to the previous going
     * around the quad), amethyst's face-edge effect.
     */
    public static void renderFaceEdges(Matrix4f pose, Matrix3f normal, VertexConsumer buffer,
                                       Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        Vector3f faceCenter = new Vector3f(p0).lerp(p2, .5f);
        List<VecPair> faceEdges = List.of(
                new VecPair(p0, p1), new VecPair(p1, p2), new VecPair(p2, p3), new VecPair(p3, p0));
        for (VecPair edge : faceEdges) {
            Vector3f edgeCenter = new Vector3f(edge.a()).lerp(edge.b(), 0.5f);
            Vector3f towardsFaceCenter = new Vector3f(faceCenter).sub(edgeCenter).normalize().mul(.25f);
            Vector3f towardsEdgeCenter = new Vector3f(edgeCenter).sub(edge.b()).normalize().mul(.25f);
            Vector3f i0 = new Vector3f(edge.b()).add(towardsFaceCenter).add(towardsEdgeCenter);
            Vector3f i1 = new Vector3f(edge.a()).add(towardsFaceCenter).sub(towardsEdgeCenter);
            addVertex(pose, normal, buffer, edge.a(), 0, 0);
            addVertex(pose, normal, buffer, edge.b(), 0, 0);
            addVertex(pose, normal, buffer, i0, 0, 0);
            addVertex(pose, normal, buffer, i1, 0, 0);
        }
    }

    /**
     * Amethyst's planet field visualization: arrows flowing along the cube's diagonals
     * between the inner block and the outer field boundary, with brackets on the outer
     * faces. Pose stack must be at the block center.
     */
    public static void renderPlanetField(float radius, boolean repel, PoseStack poseStack,
                                         MultiBufferSource bufferSource, float animation) {
        ArrayList<Vector3f> inner = new ArrayList<>();
        inner.add(new Vector3f(.5f, .5f, .5f));
        inner.add(new Vector3f(.5f, .5f, -.5f));
        inner.add(new Vector3f(.5f, -.5f, .5f));
        inner.add(new Vector3f(.5f, -.5f, -.5f));
        inner.add(new Vector3f(-.5f, .5f, .5f));
        inner.add(new Vector3f(-.5f, .5f, -.5f));
        inner.add(new Vector3f(-.5f, -.5f, .5f));
        inner.add(new Vector3f(-.5f, -.5f, -.5f));

        float r = radius + .5f - SMIDGE;
        ArrayList<Vector3f> outer = new ArrayList<>();
        outer.add(new Vector3f(r, r, r));
        outer.add(new Vector3f(r, r, -r));
        outer.add(new Vector3f(r, -r, r));
        outer.add(new Vector3f(r, -r, -r));
        outer.add(new Vector3f(-r, r, r));
        outer.add(new Vector3f(-r, r, -r));
        outer.add(new Vector3f(-r, -r, r));
        outer.add(new Vector3f(-r, -r, -r));

        List<IntPair> edges = List.of(
                new IntPair(0, 1), new IntPair(3, 2), new IntPair(5, 4), new IntPair(6, 7),
                new IntPair(0, 2), new IntPair(3, 1), new IntPair(6, 4), new IntPair(5, 7),
                new IntPair(0, 4), new IntPair(5, 1), new IntPair(6, 2), new IntPair(3, 7));

        List<IntFour> faces = List.of(
                new IntFour(2, 3, 7, 6), new IntFour(0, 1, 5, 4), new IntFour(1, 3, 7, 5),
                new IntFour(0, 2, 6, 4), new IntFour(4, 5, 7, 6), new IntFour(0, 1, 3, 2));

        VertexConsumer arrowBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(ARROW_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float diagonal = (float) Math.sqrt(2) * radius;

        for (IntPair e : edges) {
            if (repel) {
                addVertex(pose, normal, arrowBuffer, outer.get(e.a()), r, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, outer.get(e.b()), -r, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.b()), -.5f, -animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.a()), .5f, -animation);
            } else {
                addVertex(pose, normal, arrowBuffer, outer.get(e.a()), r, -animation);
                addVertex(pose, normal, arrowBuffer, outer.get(e.b()), -r, -animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.b()), -.5f, diagonal - animation);
                addVertex(pose, normal, arrowBuffer, inner.get(e.a()), .5f, diagonal - animation);
            }
        }
        VertexConsumer wallBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(WALL_TEXTURE));
        for (IntFour f : faces) {
            renderFaceEdges(pose, normal, wallBuffer,
                    outer.get(f.a()), outer.get(f.b()), outer.get(f.c()), outer.get(f.d()));
        }
    }

    /**
     * Full planet-field overlay for a cubic field block (visibility check, centering and
     * animation included) — the hook loader-side renderers chain after the block model.
     */
    public static void renderPlanetOverlay(AbstractGravityFieldBlockEntity blockEntity, float partialTick,
                                           PoseStack poseStack, MultiBufferSource buffer) {
        if (!FieldVisibility.shouldShow(blockEntity)) {
            return;
        }
        float animation = animationPhase(blockEntity, partialTick);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        renderPlanetField((float) blockEntity.getFieldRadius(), false, poseStack, buffer, animation);
        poseStack.popPose();
    }

    public record VecPair(Vector3f a, Vector3f b) {
    }

    public record IntPair(int a, int b) {
    }

    public record IntFour(int a, int b, int c, int d) {
    }
}
