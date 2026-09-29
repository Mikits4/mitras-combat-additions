package name.modid.creative;

import name.modid.MitraSCombatAdditions;
import name.modid.item.ModItems;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import static name.modid.MitraSCombatAdditions.MOD_ID;

public class ModCreativeModeTabs {
    public static final CreativeModeTab MITRAS_COMBAT_ADDITIONS_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(MOD_ID, "mitras_combat_additions"),
            FabricCreativeModeTab.builder().icon(() -> new ItemStack(ModItems.FLAIL))
                    .title(Component.translatable("creativetab.mitras-combat-additions.mitras_combat_additions"))
                    .displayItems((parameters, output) -> {

                        output.accept(ModItems.FLAIL);
                        output.accept(ModItems.ZWEIHANDER);
                    }).build());

    public static void registerCreativeModeTabs() {
        MitraSCombatAdditions.LOGGER.info("Registering Creative Mode Tabs for " + MOD_ID);
    }
}
