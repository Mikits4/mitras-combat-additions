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

    // =========================================================================
    // TUNING
    // =========================================================================

    private static final double CHAIN_LINK_SPACING = 0.24D;

    private static final float CHAIN_SCALE = 0.42F;

    /*
     * Rendered size of the Heavy Core projectile.
     *
     * The model remains centered on the projectile's actual visual center.
     */
    private static final float PROJECTILE_SCALE = 0.60F;


    // =========================================================================
    // MODEL RESOLVER
    // =========================================================================

    private final BlockModelResolver blockModelResolver;


    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public HeavyCoreRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);

        this.blockModelResolver =
                context.getBlockModelResolver();

        this.shadowRadius = 0.5F;
    }


    // =========================================================================
    // CREATE RENDER STATE
    // =========================================================================

    @Override
    public HeavyCoreRenderState createRenderState() {

        return new HeavyCoreRenderState();
    }


    // =========================================================================
    // EXTRACT RENDER STATE
    // =========================================================================

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
         * ---------------------------------------------------------
         * CORE ROTATION
         * ---------------------------------------------------------
         */

        state.spinAngle =
                entity.getSpinAngle(
                        partialTick
                );


        /*
         * ---------------------------------------------------------
         * PHYSICS WOBBLE
         * ---------------------------------------------------------
         */

        state.wobblePitch =
                entity.getWobblePitch();

        state.wobbleRoll =
                entity.getWobbleRoll();


        /*
         * ---------------------------------------------------------
         * OWNER
         * ---------------------------------------------------------
         */

        Player owner =
                getOwner(entity);


        /*
         * ---------------------------------------------------------
         * HEAVY CORE MODEL
         * ---------------------------------------------------------
         */

        blockModelResolver.update(
                state.blockModel,
                Blocks.HEAVY_CORE.defaultBlockState(),
                state.blockDisplayContext
        );


        /*
         * ---------------------------------------------------------
         * CHAIN MODEL
         * ---------------------------------------------------------
         */

        blockModelResolver.update(
                state.chainModel,
                Blocks.IRON_CHAIN.defaultBlockState(),
                state.chainDisplayContext
        );


        /*
         * ---------------------------------------------------------
         * CHAIN END
         * ---------------------------------------------------------
         *
         * Calculate the player's hand position in WORLD coordinates,
         * then convert it into coordinates relative to the rendered
         * Heavy Core origin.
         */

        if (owner != null) {

            Vec3 handPosition =
                    getHandPosition(
                            owner,
                            entity
                    );


            /*
             * IMPORTANT:
             *
             * EntityRenderer's render state already contains the
             * interpolated entity position used for this frame.
             *
             * Use state.x/y/z rather than entity.position() so the
             * chain does not visually slide when interpolation occurs.
             */
            Vec3 renderOrigin =
                    new Vec3(
                            state.x,
                            state.y,
                            state.z
                    );


            Vec3 relative =
                    handPosition.subtract(
                            renderOrigin
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


    // =========================================================================
    // RENDER
    // =========================================================================

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
         * Render the chain first so the Heavy Core naturally sits
         * on top of the final chain link.
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
         * ---------------------------------------------------------
         * HITBOX / VISUAL ALIGNMENT
         * ---------------------------------------------------------
         *
         * HeavyCoreEntity uses:
         *
         *     width  = 0.5
         *     height = 0.5
         *
         * Minecraft entity coordinates represent the bottom center
         * of that hitbox.
         *
         * Therefore the actual hitbox center is:
         *
         *     X = entity origin X
         *     Y = entity origin Y + 0.25
         *     Z = entity origin Z
         *
         * The block model occupies a 1x1x1 space and must be translated
         * so that its center is placed at that exact same point.
         *
         * This produces:
         *
         *     visual center == hitbox center
         */

        poseStack.translate(
                0.0D,
                0.25D,
                0.0D
        );


        /*
         * ---------------------------------------------------------
         * PHYSICS WOBBLE
         * ---------------------------------------------------------
         *
         * The rotations happen around the actual projectile center,
         * rather than around the bottom of the entity hitbox.
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
         * ---------------------------------------------------------
         * PROJECTILE SCALE
         * ---------------------------------------------------------
         *
         * Scale around the projectile's center.
         */

        poseStack.scale(
                PROJECTILE_SCALE,
                PROJECTILE_SCALE,
                PROJECTILE_SCALE
        );


        /*
         * ---------------------------------------------------------
         * MAIN SPIN
         * ---------------------------------------------------------
         */

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        state.spinAngle
                )
        );


        /*
         * ---------------------------------------------------------
         * MOVE BLOCK MODEL CENTER TO ORIGIN
         * ---------------------------------------------------------
         *
         * Block models use the [0, 1] block coordinate space.
         *
         * Moving by -0.5 on all axes puts the model center
         * exactly at the current pose origin.
         */

        poseStack.translate(
                -0.5D,
                -0.5D,
                -0.5D
        );


        /*
         * ---------------------------------------------------------
         * RENDER HEAVY CORE
         * ---------------------------------------------------------
         */

        state.blockModel.submit(
                poseStack,
                submitNodeCollector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );


        poseStack.popPose();


        /*
         * Allow the normal entity renderer machinery to submit
         * any additional renderer information.
         */

        super.submit(
                state,
                poseStack,
                submitNodeCollector,
                camera
        );
    }


    // =========================================================================
    // CHAIN RENDERING
    // =========================================================================

    private void renderChain(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector
    ) {

        /*
         * ---------------------------------------------------------
         * CORE CENTER
         * ---------------------------------------------------------
         *
         * The HeavyCoreEntity hitbox is:
         *
         *     width  = 0.5
         *     height = 0.5
         *
         * Therefore its center relative to entity position is:
         *
         *     (0, 0.25, 0)
         *
         * This MUST match the visual Heavy Core center above.
         */

        Vec3 core =
                new Vec3(
                        0.0D,
                        0.25D,
                        0.0D
                );


        /*
         * ---------------------------------------------------------
         * PLAYER HAND
         * ---------------------------------------------------------
         */

        Vec3 hand =
                new Vec3(
                        state.chainEndX,
                        state.chainEndY,
                        state.chainEndZ
                );


        /*
         * ---------------------------------------------------------
         * CHAIN VECTOR
         * ---------------------------------------------------------
         */

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
         * ---------------------------------------------------------
         * CHAIN LINK COUNT
         * ---------------------------------------------------------
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
         * ---------------------------------------------------------
         * CHAIN ROTATION
         * ---------------------------------------------------------
         *
         * The Minecraft chain model is naturally vertical,
         * so rotate its +Y axis onto the direction from
         * Heavy Core -> player's hand.
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


        /*
         * ---------------------------------------------------------
         * RENDER EACH LINK
         * ---------------------------------------------------------
         */

        for (int i = 0; i < links; i++) {

            /*
             * Put each link in the center of its segment.
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


            /*
             * Position the link.
             */

            poseStack.translate(
                    position.x,
                    position.y,
                    position.z
            );


            /*
             * Rotate the vertical chain model to follow
             * the actual chain direction.
             */

            poseStack.mulPose(
                    rotation
            );


            /*
             * Alternate successive chain links by 90 degrees.
             *
             * This makes them look interlocked instead of stacked.
             */

            if ((i & 1) == 1) {

                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                90.0F
                        )
                );
            }


            /*
             * Scale chain model.
             */

            poseStack.scale(
                    CHAIN_SCALE,
                    CHAIN_SCALE,
                    CHAIN_SCALE
            );


            /*
             * Center the 1x1x1 block model on this link's position.
             */

            poseStack.translate(
                    -0.5D,
                    -0.5D,
                    -0.5D
            );


            /*
             * Submit chain model.
             */

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


    // =========================================================================
    // OWNER
    // =========================================================================

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


    // =========================================================================
    // HAND POSITION
    // =========================================================================

    private Vec3 getHandPosition(
            Player player,
            HeavyCoreEntity entity
    ) {

        float yaw =
                player.getYRot();


        double radians =
                Math.toRadians(yaw);


        /*
         * Player's horizontal right direction.
         */

        Vec3 right =
                new Vec3(
                        Math.cos(radians),
                        0.0D,
                        Math.sin(radians)
                );


        /*
         * Determine which hand holds the flail.
         */

        boolean left =
                entity.isLeftHand();


        /*
         * Position outside the player's body.
         */

        double sideOffset =
                left
                        ? -0.36D
                        : 0.36D;


        /*
         * Small amount forward from the player.
         */

        double forwardOffset =
                0.04D;


        /*
         * Approximate hand height.
         */

        double handHeight =
                player.isCrouching()
                        ? 1.02D
                        : 1.22D;


        /*
         * Player's horizontal forward direction.
         */

        Vec3 forward =
                new Vec3(
                        -Math.sin(radians),
                        0.0D,
                        Math.cos(radians)
                );


        return player.position()
                .add(
                        right.scale(
                                sideOffset
                        )
                )
                .add(
                        forward.scale(
                                forwardOffset
                        )
                )
                .add(
                        0.0D,
                        handHeight,
                        0.0D
                );
    }


    // =========================================================================
    // RENDER STATE
    // =========================================================================

    public static class HeavyCoreRenderState
            extends EntityRenderState {

        /*
         * ---------------------------------------------------------
         * HEAVY CORE MODEL
         * ---------------------------------------------------------
         */

        public final BlockModelRenderState blockModel =
                new BlockModelRenderState();


        public final BlockDisplayContext blockDisplayContext =
                BlockDisplayContext.create();


        /*
         * ---------------------------------------------------------
         * CHAIN MODEL
         * ---------------------------------------------------------
         */

        public final BlockModelRenderState chainModel =
                new BlockModelRenderState();


        public final BlockDisplayContext chainDisplayContext =
                BlockDisplayContext.create();


        /*
         * ---------------------------------------------------------
         * ROTATION
         * ---------------------------------------------------------
         */

        public float spinAngle;


        /*
         * ---------------------------------------------------------
         * PHYSICS WOBBLE
         * ---------------------------------------------------------
         */

        public float wobblePitch;

        public float wobbleRoll;


        /*
         * ---------------------------------------------------------
         * CHAIN END
         * ---------------------------------------------------------
         *
         * Position of player's hand relative to the entity render origin.
         */

        public double chainEndX;

        public double chainEndY;

        public double chainEndZ;


        public boolean hasChain;
    }
}