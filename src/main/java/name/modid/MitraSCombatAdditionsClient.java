package name.modid;

import name.modid.entity.ModEntities;
import name.modid.entity.client.HeavyCoreRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MitraSCombatAdditionsClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        EntityRendererRegistry.register(
                ModEntities.HEAVY_CORE_ENTITY,
                HeavyCoreRenderer::new
        );
    }
}