package name.modid.item;

import name.modid.MitraSCombatAdditions;

import name.modid.item.custom.FlailItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Consumer;
import java.util.function.Function;

public class ModItems {

    public static final Item FLAIL = registerItem(
            "flail",
            FlailItem::new
    );

    private static Item registerItem(String name, Function<Item.Properties, Item> function) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MitraSCombatAdditions.MOD_ID, name),
                function.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MitraSCombatAdditions.MOD_ID, name)))));
    }

    public static void registerModItems() {
        MitraSCombatAdditions.LOGGER.info("Registering Mod Items for " + MitraSCombatAdditions.MOD_ID);
    }
}
