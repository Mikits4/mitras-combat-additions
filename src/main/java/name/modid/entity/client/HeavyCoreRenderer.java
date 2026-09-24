package name.modid.entity.client;

import name.modid.entity.custom.HeavyCoreEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

public class HeavyCoreRenderer
        extends EntityRenderer<
        HeavyCoreEntity,
        HeavyCoreRenderer.HeavyCoreRenderState> {

    private final BlockModelResolver blockModelResolver;

    public HeavyCoreRenderer(EntityRendererProvider.Context context) {
        super(context);

        this.blockModelResolver =
                context.getBlockModelResolver();

        this.shadowRadius = 0.5F;
    }

    @Override
    public HeavyCoreRenderState createRenderState() {
        return new HeavyCoreRenderState();
    }

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

        state.spinAngle = entity.getSpinAngle(partialTick);

        this.blockModelResolver.update(
                state.blockModel,
                Blocks.HEAVY_CORE.defaultBlockState(),
                state.blockDisplayContext
        );
    }

    @Override
    public void submit(
            HeavyCoreRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        poseStack.pushPose();

        /*
         * Move to the center of the block,
         * rotate it, then move back.
         */
        poseStack.translate(0.5D, 0.5D, 0.5D);

        poseStack.mulPose(
                Axis.YP.rotationDegrees(state.spinAngle)
        );

        poseStack.translate(-0.5D, -0.5D, -0.5D);

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

    public static class HeavyCoreRenderState
            extends EntityRenderState {

        public final BlockModelRenderState blockModel =
                new BlockModelRenderState();

        public final BlockDisplayContext blockDisplayContext =
                BlockDisplayContext.create();

        public float spinAngle;
    }
}