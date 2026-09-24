package name.modid.entity.client;

import name.modid.entity.custom.HeavyCoreEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class HeavyCoreRenderer
        extends EntityRenderer<
        HeavyCoreEntity,
        HeavyCoreRenderer.HeavyCoreRenderState> {

    private static final double CHAIN_LINK_SPACING = 0.24D;
    private static final float CHAIN_SCALE = 0.42F;

    private final BlockModelResolver blockModelResolver;

    public HeavyCoreRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);

        this.blockModelResolver =
                context.getBlockModelResolver();

        this.shadowRadius = 0.5F;
    }

    // -------------------------------------------------------------------------
    // CREATE RENDER STATE
    // -------------------------------------------------------------------------

    @Override
    public HeavyCoreRenderState createRenderState() {
        return new HeavyCoreRenderState();
    }

    // -------------------------------------------------------------------------
    // EXTRACT RENDER STATE
    // -------------------------------------------------------------------------

    @Override
    public void extractRenderState(
            HeavyCoreEntity entity,
            HeavyCoreRenderState state,
            float partialTick
    ) {
        super.extractRenderState(
                entity,
                state,
                partialTick
        );

        /*
         * Core rotation.
         */
        state.spinAngle =
                entity.getSpinAngle(partialTick);

        /*
         * Physics wobble.
         */
        state.wobblePitch =
                entity.getWobblePitch();

        state.wobbleRoll =
                entity.getWobbleRoll();

        /*
         * Owner.
         */
        Player owner =
                getOwner(entity);

        /*
         * Resolve the Heavy Core model.
         */
        blockModelResolver.update(
                state.blockModel,
                Blocks.HEAVY_CORE.defaultBlockState(),
                state.blockDisplayContext
        );

        /*
         * Resolve the Minecraft Chain model too.
         */
        blockModelResolver.update(
                state.chainModel,
                Blocks.IRON_CHAIN.defaultBlockState(),
                state.chainDisplayContext
        );

        /*
         * Calculate the hand position in WORLD coordinates.
         */
        if (owner != null) {
            Vec3 handPosition =
                    getHandPosition(
                            owner,
                            entity
                    );

            /*
             * The entity renderer's origin is the Heavy Core.
             *
             * Store the hand relative to the core.
             */
            Vec3 relative =
                    handPosition.subtract(
                            entity.position()
                    );

            state.chainEndX =
                    relative.x;

            state.chainEndY =
                    relative.y;

            state.chainEndZ =
                    relative.z;

            state.hasChain =
                    true;
        } else {
            state.hasChain =
                    false;
        }
    }

    // -------------------------------------------------------------------------
    // RENDER
    // -------------------------------------------------------------------------

    @Override
    public void submit(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        /*
         * ---------------------------------------------------------
         * CHAIN
         * ---------------------------------------------------------
         *
         * Render the chain FIRST so the Heavy Core sits naturally
         * on top of its final link.
         */
        if (state.hasChain) {
            renderChain(
                    state,
                    poseStack,
                    submitNodeCollector
            );
        }

        /*
         * ---------------------------------------------------------
         * HEAVY CORE
         * ---------------------------------------------------------
         */
        poseStack.pushPose();

        /*
         * Move to the center of the 1x1x1 block.
         */
        poseStack.translate(
                0.5D,
                0.5D,
                0.5D
        );

        /*
         * Physics wobble.
         */
        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        state.wobblePitch
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        state.wobbleRoll
                )
        );

        /*
         * Main Heavy Core spin.
         */
        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        state.spinAngle
                )
        );

        /*
         * Move back to normal block origin.
         */
        poseStack.translate(
                -0.5D,
                -0.5D,
                -0.5D
        );

        /*
         * Render the Heavy Core itself.
         */
        state.blockModel.submit(
                poseStack,
                submitNodeCollector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );

        poseStack.popPose();

        super.submit(
                state,
                poseStack,
                submitNodeCollector,
                camera
        );
    }

    // -------------------------------------------------------------------------
    // CHAIN RENDERING
    // -------------------------------------------------------------------------

    private void renderChain(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector
    ) {
        /*
         * Heavy Core is the origin.
         */
        Vec3 core =
                new Vec3(
                        0.5D,
                        0.5D,
                        0.5D
                );

        /*
         * Player's hand relative to the core.
         */
        Vec3 hand =
                new Vec3(
                        state.chainEndX,
                        state.chainEndY,
                        state.chainEndZ
                );

        Vec3 difference =
                hand.subtract(core);

        double length =
                difference.length();

        if (length < 0.05D) {
            return;
        }

        Vec3 direction =
                difference.normalize();

        /*
         * Number of chain links.
         */
        int links =
                Math.max(
                        1,
                        (int) Math.ceil(
                                length
                                        / CHAIN_LINK_SPACING
                        )
                );

        /*
         * Quaternion which turns the chain's default vertical
         * Y axis into the direction from core → hand.
         */
        Quaternionf rotation =
                new Quaternionf();

        rotation.rotationTo(
                new Vector3f(
                        0.0F,
                        1.0F,
                        0.0F
                ),
                new Vector3f(
                        (float) direction.x,
                        (float) direction.y,
                        (float) direction.z
                )
        );

        for (int i = 0; i < links; i++) {

            /*
             * Put the link at the center of its segment.
             */
            double t =
                    (i + 0.5D)
                            / links;

            Vec3 position =
                    core.lerp(
                            hand,
                            t
                    );

            poseStack.pushPose();

            poseStack.translate(
                    position.x,
                    position.y,
                    position.z
            );

            /*
             * Point the vertical chain model toward
             * the other endpoint.
             */
            poseStack.mulPose(rotation);

            /*
             * Alternate the orientation of successive links.
             *
             * This makes the chain look like actual interlocking
             * chain links instead of a stack of identical rings.
             */
            if ((i & 1) == 1) {
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(90.0F)
                );
            }

            /*
             * Scale the full block-sized Chain model down.
             */
            poseStack.scale(
                    CHAIN_SCALE,
                    CHAIN_SCALE,
                    CHAIN_SCALE
            );

            /*
             * Center the block model on the link position.
             */
            poseStack.translate(
                    -0.5D,
                    -0.5D,
                    -0.5D
            );

            state.chainModel.submit(
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );

            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------------------
    // OWNER
    // -------------------------------------------------------------------------

    private Player getOwner(
            HeavyCoreEntity entity
    ) {
        UUID ownerUuid =
                entity.getOwnerUuid();

        if (ownerUuid == null) {
            return null;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {
            return null;
        }

        return minecraft.level.getPlayerByUUID(
                ownerUuid
        );
    }

    // -------------------------------------------------------------------------
    // HAND POSITION
    // -------------------------------------------------------------------------

    private Vec3 getHandPosition(
            Player player,
            HeavyCoreEntity entity
    ) {
        float yaw = player.getYRot();
        double radians = Math.toRadians(yaw);

        /*
         * Player's horizontal right direction.
         */
        Vec3 right = new Vec3(
                Math.cos(radians),
                0.0D,
                Math.sin(radians)
        );

        /*
         * Which hand is holding the flail.
         */
        boolean left = entity.isLeftHand();

        /*
         * Put the point on the outside of the player's arm.
         */
        double sideOffset = left
                ? -0.36D
                : 0.36D;

        /*
         * Only a tiny amount forward.
         *
         * The old value of 0.28 made the chain visibly float
         * in front of the hand.
         */
        double forwardOffset = 0.04D;

        /*
         * Approximate hand height.
         */
        double handHeight = player.isCrouching()
                ? 1.02D
                : 1.22D;

        /*
         * Small forward direction based on the player's body yaw.
         */
        Vec3 forward = new Vec3(
                -Math.sin(radians),
                0.0D,
                Math.cos(radians)
        );

        return player.position()
                .add(
                        right.scale(sideOffset)
                )
                .add(
                        forward.scale(forwardOffset)
                )
                .add(
                        0.0D,
                        handHeight,
                        0.0D
                );
    }

    // -------------------------------------------------------------------------
    // RENDER STATE
    // -------------------------------------------------------------------------

    public static class HeavyCoreRenderState
            extends EntityRenderState {

        /*
         * Heavy Core block model.
         */
        public final BlockModelRenderState blockModel =
                new BlockModelRenderState();

        public final BlockDisplayContext blockDisplayContext =
                BlockDisplayContext.create();

        /*
         * Chain block model.
         */
        public final BlockModelRenderState chainModel =
                new BlockModelRenderState();

        public final BlockDisplayContext chainDisplayContext =
                BlockDisplayContext.create();

        /*
         * Heavy Core rotation.
         */
        public float spinAngle;

        /*
         * Physics wobble.
         */
        public float wobblePitch;
        public float wobbleRoll;

        /*
         * Hand position relative to the Heavy Core.
         */
        public double chainEndX;
        public double chainEndY;
        public double chainEndZ;

        public boolean hasChain;
    }
}