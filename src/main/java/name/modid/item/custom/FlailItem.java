package name.modid.item.custom;

import name.modid.effect.ModEffects;
import name.modid.entity.ModEntities;
import name.modid.entity.custom.HeavyCoreEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlailItem extends Item {

    private static final int DEFAULT_ATTACK_DAMAGE = 5;
    private static final float DEFAULT_ATTACK_SPEED = -2.2F;

    private static final Map<UUID, FlailState> ACTIVE_FLAILS =
            new ConcurrentHashMap<>();

    public FlailItem(Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------------------------
    // ATTRIBUTES
    // -------------------------------------------------------------------------

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                DEFAULT_ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                DEFAULT_ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    // -------------------------------------------------------------------------
    // MELEE BLEED
    // -------------------------------------------------------------------------

    @Override
    public void hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker
    ) {
        MobEffectInstance existing =
                target.getEffect(ModEffects.BLEED);

        int amplifier =
                existing == null
                        ? 0
                        : existing.getAmplifier() + 1;

        target.addEffect(
                new MobEffectInstance(
                        ModEffects.BLEED,
                        20,
                        amplifier
                ),
                attacker
        );
    }

    // -------------------------------------------------------------------------
    // START CHARGING
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack =
                player.getItemInHand(hand);

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }

        FlailState state =
                ACTIVE_FLAILS.computeIfAbsent(
                        player.getUUID(),
                        id -> new FlailState()
                );

        HeavyCoreEntity core =
                state.getCore(serverLevel);

        /*
         * Do not allow another throw while the existing core
         * is charging, flying, hooked, or returning.
         */
        if (
                core != null
                        && core.isAlive()
                        && !core.isReady()
        ) {
            return InteractionResult.FAIL;
        }

        /*
         * Create a new core after the previous one returned
         * and discarded itself.
         */
        if (core == null || !core.isAlive()) {
            core = new HeavyCoreEntity(
                    ModEntities.HEAVY_CORE_ENTITY,
                    serverLevel
            );

            core.setOwner(player, hand);
            core.startCharging();

            serverLevel.addFreshEntity(core);

            state.coreUuid =
                    core.getUUID();
        }

        core.startCharging();

        player.startUsingItem(hand);

        return InteractionResult.CONSUME;
    }

    // -------------------------------------------------------------------------
    // USE ANIMATION
    // -------------------------------------------------------------------------

    @Override
    public ItemUseAnimation getUseAnimation(
            ItemStack stack
    ) {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public int getUseDuration(
            ItemStack stack,
            LivingEntity entity
    ) {
        return 72000;
    }

    // -------------------------------------------------------------------------
    // RELEASE / THROW
    // -------------------------------------------------------------------------

    @Override
    public boolean releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity livingEntity,
            int timeLeft
    ) {
        if (!(livingEntity instanceof Player player)) {
            return false;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        FlailState state =
                ACTIVE_FLAILS.get(
                        player.getUUID()
                );

        if (state == null) {
            return false;
        }

        HeavyCoreEntity core =
                state.getCore(serverLevel);

        if (core == null || !core.isAlive()) {
            return false;
        }

        /*
         * Only charging cores can be thrown.
         */
        if (!core.isCharging()) {
            return false;
        }

        /*
         * Crouching selects fishing-rod mode.
         *
         * Standing selects grapple mode.
         */
        boolean reversePull =
                player.isCrouching();

        core.launch(
                player.getLookAngle(),
                reversePull
        );

        player.level().playSound(
                null,
                core.getX(),
                core.getY(),
                core.getZ(),
                SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        return true;
    }

    // -------------------------------------------------------------------------
    // ACTIVE STATE
    // -------------------------------------------------------------------------

    private static class FlailState {

        private UUID coreUuid;

        private HeavyCoreEntity getCore(
                ServerLevel level
        ) {
            if (coreUuid == null) {
                return null;
            }

            Entity entity =
                    level.getEntity(coreUuid);

            return entity instanceof HeavyCoreEntity core
                    ? core
                    : null;
        }
    }
}
