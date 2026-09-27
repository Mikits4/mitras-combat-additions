package name.modid.effect;

import name.modid.MitraSCombatAdditions;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class ModEffects {

    public static final Holder<MobEffect> SHOCK = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(
                    MitraSCombatAdditions.MOD_ID,
                    "bleed"
            ),
            new ShockMobEffect(
                    MobEffectCategory.HARMFUL,
                    7561558
            )
    );

    public static void registerModEffects() {
        MitraSCombatAdditions.LOGGER.info(
                "Registering mod effects for " + MitraSCombatAdditions.MOD_ID
        );
    }
}