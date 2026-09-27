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
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlailItem extends Item {

    // =========================================================================
    // YOUR ORIGINAL VALUES
    // =========================================================================

    /*
     * KEEPING YOUR EXACT VALUES.
     */
    private static final int DEFAULT_ATTACK_DAMAGE = 5;

    private static final float DEFAULT_ATTACK_SPEED = -2.2F;


    // =========================================================================
    // ACTIVE FLAILS
    // =========================================================================

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
         * This attaches createAttributes() to the actual item.
         *
         * Your previous version created the modifiers but never placed
         * them into the item's Properties.
         */
        super(
                properties
                        .stacksTo(1)
                        .attributes(
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
                 * YOUR DAMAGE VALUE:
                 *
                 * +5 attack damage modifier.
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
                 * YOUR ATTACK SPEED VALUE:
                 *
                 * -2.2 attack speed modifier.
                 *
                 * We are NOT changing it.
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
    // START / CONTINUE USING
    // =========================================================================

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    ) {

        ItemStack stack =
                player.getItemInHand(
                        hand
                );


        /*
         * Client accepts the use.
         * Server creates/controls the projectile.
         */
        if (
                level.isClientSide()
        ) {

            return InteractionResult.CONSUME;
        }


        if (
                !(level instanceof ServerLevel serverLevel)
        ) {

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
                state.getCore(
                        serverLevel
                );


        /*
         * =============================================================
         * TERRARIA-STYLE GROUNDED MODE
         * =============================================================
         *
         * If the projectile is already flying and the player presses
         * right click, convert it into the sagging/grounded flail.
         */
        if (
                core != null
                        && core.isAlive()
                        && core.isFlying()
        ) {

            core.beginSagging();


            /*
             * Continue using the item so releaseUsing() fires when
             * the player releases right click.
             */
            state.useHand = hand;

            player.startUsingItem(
                    hand
            );


            return InteractionResult.CONSUME;
        }


        /*
         * Clear a stale projectile UUID.
         */
        if (
                core == null
                        && state.coreUuid != null
        ) {

            state.coreUuid = null;
        }


        /*
         * Don't start a new charge while an existing projectile is
         * charging, flying, hooked, sagging, or returning.
         */
        if (
                core != null
                        && core.isAlive()
                        && !core.isReady()
        ) {

            return InteractionResult.FAIL;
        }


        // ---------------------------------------------------------------------
        // CREATE NEW CORE
        // ---------------------------------------------------------------------

        if (
                core == null
                        || !core.isAlive()
        ) {

            core =
                    new HeavyCoreEntity(
                            ModEntities.HEAVY_CORE_ENTITY,
                            serverLevel
                    );


            core.setOwner(
                    player,
                    hand
            );


            /*
             * Start charging before inserting the entity into the world.
             */
            core.startCharging();


            /*
             * =============================================================
             * CRITICAL SPAWN-CHUNK FIX
             * =============================================================
             *
             * Put the projectile directly at the player's hand BEFORE
             * adding it to the world.
             *
             * This prevents it from initially existing around world origin,
             * which was responsible for spawn-chunk-dependent behavior.
             */

            double handHeight =
                    player.isCrouching()
                            ? 1.02D
                            : 1.22D;


            float yaw =
                    player.getYRot();


            double radians =
                    Math.toRadians(
                            yaw
                    );


            Vec3 right =
                    new Vec3(
                            Math.cos(
                                    radians
                            ),
                            0.0D,
                            Math.sin(
                                    radians
                            )
                    );


            Vec3 forward =
                    new Vec3(
                            -Math.sin(
                                    radians
                            ),
                            0.0D,
                            Math.cos(
                                    radians
                            )
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
             * HeavyCoreEntity has a 0.5-high hitbox.
             *
             * Entity Y is its bottom, so subtract 0.25 to make the
             * hitbox center coincide with the hand/projectile center.
             */
            core.setPos(
                    handPosition.x,
                    handPosition.y - 0.25D,
                    handPosition.z
            );


            serverLevel.addFreshEntity(
                    core
            );


            state.coreUuid =
                    core.getUUID();
        }


        /*
         * Existing READY core can start a fresh charge.
         */
        if (
                core.isReady()
        ) {

            core.startCharging();
        }


        state.useHand = hand;

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
    // INTERRUPTED USE
    // =========================================================================

    @Override
    public void inventoryTick(
            ItemStack stack,
            ServerLevel level,
            Entity entity,
            EquipmentSlot slot
    ) {

        super.inventoryTick(
                stack,
                level,
                entity,
                slot
        );

        if (
                !(entity instanceof Player player)
        ) {

            return;
        }

        FlailState state =
                ACTIVE_FLAILS.get(
                        player.getUUID()
                );

        if (
                state == null
                        || state.useHand == null
        ) {

            return;
        }

        EquipmentSlot expectedSlot =
                state.useHand == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND
                        : EquipmentSlot.OFFHAND;

        /*
         * Swapping hands while charging moves the ItemStack to the other
         * equipment slot.  That interrupts Minecraft's item-use state, so
         * releaseUsing() is not a safe place to handle the cancellation.
         *
         * The Item itself is still ticked in its new slot, letting us detect
         * the move and cancel the projectile cleanly without throwing or
         * returning it as if the player had released the swing.
         */
        if (
                slot != expectedSlot
        ) {

            HeavyCoreEntity core =
                    state.getCore(
                            level
                    );

            if (
                    core != null
                            && core.isAlive()
                            && (core.isCharging() || core.isSagging())
            ) {

                core.cancelUse();
            }

            state.useHand = null;
        }
    }


    // =========================================================================
    // RELEASE / THROW / RETURN
    // =========================================================================

    @Override
    public boolean releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity livingEntity,
            int timeLeft
    ) {

        if (
                !(livingEntity instanceof Player player)
        ) {

            return false;
        }


        if (
                !(level instanceof ServerLevel serverLevel)
        ) {

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
                state.getCore(
                        serverLevel
                );


        if (
                core == null
                        || !core.isAlive()
        ) {

            state.coreUuid =
                    null;
            state.useHand = null;

            return false;
        }


        /*
         * An interrupted item use (most notably swapping hands with the
         * offhand key) must not count as releasing the flail to throw it.
         * Only the same hand and same ItemStack that started the use may
         * complete a charge.
         */
        InteractionHand useHand = state.useHand;

        if (
                useHand == null
                        || player.getItemInHand(useHand) != stack
        ) {

            state.useHand = null;

            if (
                    core.isCharging()
                            || core.isSagging()
            ) {

                core.cancelUse();
            }

            return false;
        }


        /*
         * =============================================================
         * GROUNDED FLAIL
         * =============================================================
         *
         * Releasing right click while grounded tells the projectile
         * to return.
         */
        if (
                core.isSagging()
        ) {

            core.releaseSagging();
            state.useHand = null;

            return true;
        }


        /*
         * Only CHARGING can become a normal throw.
         */
        if (
                !core.isCharging()
        ) {

            return false;
        }


        /*
         * Crouching = pull entity toward player.
         *
         * Standing = grapple player toward entity.
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

        state.useHand = null;

        return true;
    }


    // =========================================================================
    // ACTIVE STATE
    // =========================================================================

    private static class FlailState {

        private UUID coreUuid;
        private InteractionHand useHand;


        private HeavyCoreEntity getCore(
                ServerLevel level
        ) {

            if (
                    coreUuid == null
            ) {

                return null;
            }


            Entity entity =
                    level.getEntity(
                            coreUuid
                    );


            if (
                    entity instanceof HeavyCoreEntity core
            ) {

                if (
                        !core.isAlive()
                ) {

                    coreUuid =
                            null;

                    return null;
                }


                return core;
            }


            /*
             * UUID no longer resolves to the flail.
             */
            coreUuid =
                    null;


            return null;
        }
    }
}