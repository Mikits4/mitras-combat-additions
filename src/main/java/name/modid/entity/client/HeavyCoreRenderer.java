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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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

    private static final float PROJECTILE_SCALE = 0.60F;

    private static final double CORE_VISUAL_OFFSET = 0.25D;

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


        this.shadowRadius =
                0.5F;
    }


    // =========================================================================
    // RENDER STATE
    // =========================================================================

    @Override
    public HeavyCoreRenderState createRenderState() {

        return new HeavyCoreRenderState();
    }


    // =========================================================================
    // EXTRACT
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
         * IMPORTANT:
         *
         * The EntityRenderState normally receives the real entity position.
         *
         * Replace it with our purely visual client position.
         *
         * The actual entity/hitbox remains at the server position.
         */

        Vec3 renderPosition =
                entity.getRenderPosition(
                        partialTick
                );


        state.x =
                renderPosition.x;


        state.y =
                renderPosition.y;


        state.z =
                renderPosition.z;


        state.spinAngle =
                entity.getSpinAngle(
                        partialTick
                );


        state.wobblePitch =
                entity.getWobblePitch();


        state.wobbleRoll =
                entity.getWobbleRoll();


        Player owner =
                getOwner(
                        entity
                );


        blockModelResolver.update(
                state.blockModel,
                Blocks.HEAVY_CORE.defaultBlockState(),
                state.blockDisplayContext
        );


        blockModelResolver.update(
                state.chainModel,
                Blocks.IRON_CHAIN.defaultBlockState(),
                state.chainDisplayContext
        );


        if (owner != null) {

            Vec3 handPosition =
                    getHandPosition(
                            owner,
                            entity
                    );


            Vec3 renderOrigin =
                    renderPosition;


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
    // SUBMIT
    // =========================================================================

    @Override
    public void submit(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {

        if (state.hasChain) {

            renderChain(
                    state,
                    poseStack,
                    submitNodeCollector
            );
        }


        poseStack.pushPose();


        /*
         * Entity position represents the bottom of the 0.5-high hitbox.
         *
         * Therefore the actual hitbox center is 0.25 above the entity
         * position.
         */

        Vec3 pivot =
                new Vec3(
                        0.0D,
                        0.25D,
                        0.0D
                );


        Vec3 hand =
                new Vec3(
                        state.chainEndX,
                        state.chainEndY,
                        state.chainEndZ
                );


        Vec3 awayFromPlayer =
                pivot.subtract(
                        hand
                );


        if (
                awayFromPlayer.lengthSqr()
                        < 1.0E-6D
        ) {

            awayFromPlayer =
                    new Vec3(
                            0.0D,
                            1.0D,
                            0.0D
                    );

        } else {

            awayFromPlayer =
                    awayFromPlayer.normalize();
        }


        Quaternionf facingRotation =
                new Quaternionf();


        facingRotation.rotationTo(
                new Vector3f(
                        0.0F,
                        1.0F,
                        0.0F
                ),
                new Vector3f(
                        (float) awayFromPlayer.x,
                        (float) awayFromPlayer.y,
                        (float) awayFromPlayer.z
                )
        );


        poseStack.translate(
                pivot.x,
                pivot.y,
                pivot.z
        );


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


        poseStack.mulPose(
                facingRotation
        );


        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        state.spinAngle
                )
        );


        poseStack.translate(
                0.0D,
                CORE_VISUAL_OFFSET,
                0.0D
        );


        poseStack.scale(
                PROJECTILE_SCALE,
                PROJECTILE_SCALE,
                PROJECTILE_SCALE
        );


        poseStack.translate(
                -0.5D,
                -0.5D,
                -0.5D
        );


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


    // =========================================================================
    // CHAIN
    // =========================================================================

    private void renderChain(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector
    ) {

        /*
         * Start chain at the same visual hitbox center used by the core.
         */

        Vec3 core =
                new Vec3(
                        0.0D,
                        0.25D,
                        0.0D
                );


        Vec3 hand =
                new Vec3(
                        state.chainEndX,
                        state.chainEndY,
                        state.chainEndZ
                );


        Vec3 difference =
                hand.subtract(
                        core
                );


        double length =
                difference.length();


        if (
                length
                        < 0.05D
        ) {

            return;
        }


        Vec3 direction =
                difference.normalize();


        int links =
                Math.max(
                        1,
                        (int) Math.ceil(
                                length
                                        / CHAIN_LINK_SPACING
                        )
                );


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


        for (
                int i = 0;
                i < links;
                i++
        ) {

            double t =
                    (
                            i
                                    + 0.5D
                    )
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


            poseStack.mulPose(
                    rotation
            );


            if (
                    (i & 1)
                            == 1
            ) {

                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                90.0F
                        )
                );
            }


            poseStack.scale(
                    CHAIN_SCALE,
                    CHAIN_SCALE,
                    CHAIN_SCALE
            );


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


    // =========================================================================
    // OWNER
    // =========================================================================

    private Player getOwner(
            HeavyCoreEntity entity
    ) {

        UUID ownerUuid =
                entity.getOwnerUuid();


        if (
                ownerUuid == null
        ) {

            return null;
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        if (
                minecraft.level == null
        ) {

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
                Math.toRadians(
                        yaw
                );


        Vec3 right =
                new Vec3(
                        Math.cos(radians),
                        0.0D,
                        Math.sin(radians)
                );


        boolean left =
                entity.isLeftHand();


        double sideOffset =
                left
                        ? -0.36D
                        : 0.36D;


        double forwardOffset =
                0.04D;


        double handHeight =
                player.isCrouching()
                        ? 1.02D
                        : 1.22D;


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
    // RENDER STATE CLASS
    // =========================================================================

    public static class HeavyCoreRenderState
            extends EntityRenderState {

        public final BlockModelRenderState blockModel =
                new BlockModelRenderState();


        public final BlockDisplayContext blockDisplayContext =
                BlockDisplayContext.create();


        public final BlockModelRenderState chainModel =
                new BlockModelRenderState();


        public final BlockDisplayContext chainDisplayContext =
                BlockDisplayContext.create();


        public float spinAngle;

        public float wobblePitch;

        public float wobbleRoll;


        public double chainEndX;

        public double chainEndY;

        public double chainEndZ;


        public boolean hasChain;
    }
}