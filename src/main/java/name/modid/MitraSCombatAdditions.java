package name.modid;

import name.modid.effect.ModEffects;
import name.modid.entity.ModEntities;
import name.modid.entity.client.HeavyCoreEntityRenderer;
import name.modid.entity.client.HeavyCoreRenderer;
import name.modid.item.ModItems;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MitraSCombatAdditions implements ModInitializer {
	public static final String MOD_ID = "mitras-combat-additions";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEffects.registerModEffects();
		ModItems.registerModItems();

		EntityRendererRegistry.register(
				ModEntities.HEAVY_CORE_ENTITY,
				HeavyCoreRenderer::new
		);

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
