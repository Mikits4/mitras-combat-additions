
        package name.modid.entity;

import name.modid.MitraSCombatAdditions;
import name.modid.entity.custom.HeavyCoreEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    public static final EntityType<HeavyCoreEntity> HEAVY_CORE_ENTITY =
            register(
                    "heavy_core_entity",
                    EntityType.Builder
                            .of(HeavyCoreEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
            );

    private static <T extends Entity> EntityType<T> register(
            String name,
            EntityType.Builder<T> builder
    ) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(
                        MitraSCombatAdditions.MOD_ID,
                        name
                )
        );

        return Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                key,
                builder.build(key)
        );
    }

    public static void registerModEntities() {
        // Loading this class causes the entity type to be registered.
    }
}

