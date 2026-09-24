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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlailItem extends Item {

    // =========================================================================
    // TUNING
    // =========================================================================

    /*
     * Damage added by the flail's item attribute.
     *
     * Player base attack damage is separate from this value.
     *
     * Therefore:
     *
     *     5.0 modifier + 1.0 player base = 6.0 total
     */
    private static final double DEFAULT_ATTACK_DAMAGE = 5.0D;

    /*
     * Vanilla sword-like final attack speed is 1.6.
     *
     * Player base attack speed = 4.0
     *
     *     4.0 + (-2.4) = 1.6
     */
    private static final double DEFAULT_ATTACK_SPEED = -2.2D;


    // =========================================================================
    // ACTIVE FLAILS
    // =========================================================================

    /*
     * One active projectile state per player UUID.
     *
     * This is server-side state used to locate the player's HeavyCoreEntity.
     */
    private static final Map<UUID, FlailState> ACTIVE_FLAILS =
            new ConcurrentHashMap<>();


    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public FlailItem(
            Properties properties
    ) {

        /*
         * IMPORTANT:
         *
         * This was the reason the damage and attack-speed values were
         * not applying.
         *
         * createAttributes() existed, but the resulting component was
         * never attached to the item.
         */
        super(
                properties.attributes(
                        createAttributes()
                )
        );
    }


    // =========================================================================
    // ATTRIBUTES
    // =========================================================================

    public static ItemAttributeModifiers createAttributes() {

        return ItemAttributeModifiers.builder()

                /*
                 * ATTACK DAMAGE
                 *
                 * This is an ADD_VALUE modifier.
                 */
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                DEFAULT_ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )

                /*
                 * ATTACK SPEED
                 *
                 * -2.4 from the player's base 4.0
                 * produces a final attack speed of 1.6.
                 */
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


    // =========================================================================
    // MELEE BLEED
    // =========================================================================

    @Override
    public void hurtEnemy(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker
    ) {

        MobEffectInstance existing =
                target.getEffect(
                        ModEffects.BLEED
                );


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


    // =========================================================================
    // START CHARGING
    // =========================================================================

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    ) {

        ItemStack stack =
                player.getItemInHand(hand);


        /*
         * Client accepts the use action.
         *
         * Actual projectile creation happens on the server.
         */
        if (level.isClientSide()) {

            return InteractionResult.CONSUME;
        }


        if (!(level instanceof ServerLevel serverLevel)) {

            return InteractionResult.FAIL;
        }


        UUID playerUuid =
                player.getUUID();


        FlailState state =
                ACTIVE_FLAILS.computeIfAbsent(
                        playerUuid,
                        id -> new FlailState()
                );


        HeavyCoreEntity core =
                state.getCore(serverLevel);


        /*
         * Clear stale projectile UUIDs.
         */
        if (
                core == null
                        && state.coreUuid != null
        ) {

            state.coreUuid = null;
        }


        /*
         * Don't allow a second projectile while the current one
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
         * Create a new Heavy Core when no active one exists.
         */
        if (
                core == null
                        || !core.isAlive()
        ) {

            core =
                    new HeavyCoreEntity(
                            ModEntities.HEAVY_CORE_ENTITY,
                            serverLevel
                    );


            /*
             * Set the owner BEFORE positioning the projectile.
             */
            core.setOwner(
                    player,
                    hand
            );


            /*
             * Start charging.
             */
            core.startCharging();


            /*
             * =============================================================
             * CRITICAL BUG FIX
             * =============================================================
             *
             * The old code added the entity to the world while it was still
             * at its constructor position.
             *
             * The constructor position is effectively world origin until
             * tickCharging() gets a chance to move the projectile to the hand.
             *
             * Outside spawn chunks, that first tick may never happen.
             *
             * Spawn it directly at the player so the entity starts in an
             * already-active/simulated chunk.
             *
             * Use the player's current position plus the hand height that
             * HeavyCoreEntity uses for standing/crouching.
             */

            double handHeight =
                    player.isCrouching()
                            ? 1.02D
                            : 1.22D;


            float yaw =
                    player.getYRot();


            double radians =
                    Math.toRadians(yaw);


            Vec3 right =
                    new Vec3(
                            Math.cos(radians),
                            0.0D,
                            Math.sin(radians)
                    );


            Vec3 forward =
                    new Vec3(
                            -Math.sin(radians),
                            0.0D,
                            Math.cos(radians)
                    );


            boolean leftHand =
                    core.isLeftHand();


            double sideOffset =
                    leftHand
                            ? -0.36D
                            : 0.36D;


            Vec3 handPosition =
                    player.position()
                            .add(
                                    right.scale(
                                            sideOffset
                                    )
                            )
                            .add(
                                    forward.scale(
                                            0.04D
                                    )
                            )
                            .add(
                                    0.0D,
                                    handHeight,
                                    0.0D
                            );


            /*
             * HeavyCoreEntity's position represents the bottom of its
             * 0.5-block-high hitbox.
             *
             * Therefore subtract half the hitbox height so the hitbox
             * center sits exactly on the hand position.
             */
            core.setPos(
                    handPosition.x,
                    handPosition.y - 0.25D,
                    handPosition.z
            );


            /*
             * NOW add it to the world.
             *
             * It is already in the player's active chunk.
             */
            serverLevel.addFreshEntity(
                    core
            );


            state.coreUuid =
                    core.getUUID();
        }


        /*
         * If a READY core already exists, begin a new charge.
         *
         * Newly-created cores were already started above.
         */
        if (core.isReady()) {

            core.startCharging();
        }


        player.startUsingItem(
                hand
        );


        return InteractionResult.CONSUME;
    }


    // =========================================================================
    // USE ANIMATION
    // =========================================================================

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


    // =========================================================================
    // RELEASE / THROW
    // =========================================================================

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


        /*
         * releaseUsing is only allowed to perform the actual throw
         * on the server.
         */
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


        /*
         * If the old entity no longer exists, clear the stale UUID.
         *
         * This makes the next use create a clean projectile.
         */
        if (
                core == null
                        || !core.isAlive()
        ) {

            state.coreUuid = null;

            return false;
        }


        /*
         * Only charging cores can be thrown.
         */
        if (!core.isCharging()) {

            return false;
        }


        /*
         * Crouching:
         *
         *     entity is pulled toward player
         *
         * Standing:
         *
         *     player is grappled toward entity
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


    // =========================================================================
    // ACTIVE STATE
    // =========================================================================

    private static class FlailState {

        private UUID coreUuid;


        private HeavyCoreEntity getCore(
                ServerLevel level
        ) {

            if (coreUuid == null) {

                return null;
            }


            Entity entity =
                    level.getEntity(
                            coreUuid
                    );


            if (
                    entity instanceof HeavyCoreEntity core
            ) {

                /*
                 * If the entity has been removed, treat it as nonexistent.
                 */
                if (!core.isAlive()) {

                    coreUuid = null;

                    return null;
                }


                return core;
            }


            /*
             * The stored entity UUID no longer resolves
             * to a HeavyCoreEntity.
             */
            coreUuid = null;


            return null;
        }
    }
}