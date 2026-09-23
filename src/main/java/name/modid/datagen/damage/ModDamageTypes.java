package name.modid.datagen.damage;

import name.modid.MitraSCombatAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public class ModDamageTypes {

    public static final ResourceKey<DamageType> BLEED =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath(
                            MitraSCombatAdditions.MOD_ID,
                            "bleed"
                    )
            );

    public static void bootstrap(BootstrapContext<DamageType> context) {
        context.register(
                BLEED,
                new DamageType("bleed", 0.1f, DamageEffects.HURT)
        );
    }

    public static DamageSource create(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(
                level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(key)
        );
    }
}