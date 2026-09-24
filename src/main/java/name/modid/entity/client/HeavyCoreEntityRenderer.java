package name.modid.entity.client;

import name.modid.entity.custom.HeavyCoreEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;

public class HeavyCoreEntityRenderer extends FallingBlockRenderer {

    public HeavyCoreEntityRenderer(
            EntityRendererProvider.Context context
    ) {
        super(context);
    }
}
