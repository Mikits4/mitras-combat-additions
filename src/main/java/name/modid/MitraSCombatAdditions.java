package name.modid;

import name.modid.effect.ModEffects;
import name.modid.entity.ModEntities;
import name.modid.entity.client.HeavyCoreRenderer;
import name.modid.item.ModCreativeModeTabs;
import name.modid.item.ModItems;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MitraSCombatAdditions implements ModInitializer {
	public static final String MOD_ID = "mitras-combat-additions";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEffects.registerModEffects();
		ModItems.registerModItems();
		ModCreativeModeTabs.registerCreativeModeTabs();


		EntityRendererRegistry.register(
				ModEntities.HEAVY_CORE_ENTITY,
				HeavyCoreRenderer::new
		);

		LOGGER.info("Hello Fabric world!");
	}
}
