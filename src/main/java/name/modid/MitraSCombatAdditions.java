package name.modid;

import name.modid.effect.ModEffects;
import name.modid.creative.ModCreativeModeTabs;
import name.modid.item.ModItems;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MitraSCombatAdditions
		implements ModInitializer {

	public static final String MOD_ID =
			"mitras-combat-additions";

	public static final Logger LOGGER =
			LoggerFactory.getLogger(
					MOD_ID
			);


	@Override
	public void onInitialize() {

		ModEffects.registerModEffects();

		ModItems.registerModItems();

		ModCreativeModeTabs.registerCreativeModeTabs();

		LOGGER.info(
				"Hello Fabric world!"
		);
	}
}