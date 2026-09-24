package name.modid.item.custom;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ZweihanderItem extends Item {

    /*
     * ============================================================
     * ATTACK SETTINGS
     * ============================================================
     */

    /**
     * Total duration of the one-click attack.
     *
     * The player only needs to press right-click once.
     */
    private static final int ATTACK_TICKS = 12;

    /**
     * Tick at which the weapon actually hits.
     *
     * This gives the attack a wind-up before the thrust.
     */
    private static final int HIT_TICK = 4;

    /**
     * Cooldown between attacks.
     */
    private static final int COOLDOWN_TICKS = 10;

    /**
     * Vanilla kinetic damage multiplier.
     *
     * Final damage:
     *
     * base attack damage
     * + floor(relative speed * DAMAGE_MULTIPLIER)
     */
    private static final float DAMAGE_MULTIPLIER = 1.0F;

    /*
     * ============================================================
     * ATTACK STATE
     * ============================================================
     */

    /**
     * Entities hit during the current attack.
     */
    private final Set<Integer> hitEntities = new HashSet<>();

    /**
     * Makes sure the thrust only happens once.
     */
    private boolean attackPerformed = false;

    /*
     * ============================================================
     * CONSTRUCTOR
     * ============================================================
     */

    public ZweihanderItem(Properties properties) {
        super(properties);
    }

    /*
     * ============================================================
     * RIGHT CLICK
     * ============================================================
     */

    @Override
    public InteractionResult use(
            net.minecraft.world.level.Level level,
            Player player,
            InteractionHand hand
    ) {

        /*
         * Don't start another use while already using something.
         */
        if (player.isUsingItem()) {
            return InteractionResult.FAIL;
        }

        /*
         * Don't attack during cooldown.
         */
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        /*
         * Reset the state for the new attack.
         */
        hitEntities.clear();
        attackPerformed = false;

        /*
         * Start the item-use animation/timer.
         *
         * IMPORTANT:
         *
         * This happens from one click.
         * The player does not have to keep holding the button.
         */
        player.startUsingItem(hand);

        /*
         * Start cooldown immediately.
         */
        player.getCooldowns().addCooldown(
                stack,
                COOLDOWN_TICKS
        );

        return InteractionResult.CONSUME;
    }

    /*
     * ============================================================
     * USE DURATION
     * ============================================================
     */

    @Override
    public int getUseDuration(
            ItemStack stack,
            LivingEntity entity
    ) {
        return ATTACK_TICKS;
    }

    /*
     * ============================================================
     * USE TICK
     * ============================================================
     */

    @Override
    public void onUseTick(
            net.minecraft.world.level.Level level,
            LivingEntity attacker,
            ItemStack stack,
            int remainingUseTicks
    ) {

        /*
         * Only the server performs collision/damage.
         */
        if (level.isClientSide()) {
            return;
        }

        /*
         * Convert remaining duration into elapsed duration.
         */
        int ticksUsed =
                ATTACK_TICKS - remainingUseTicks;

        /*
         * Perform the thrust exactly once.
         */
        if (ticksUsed == HIT_TICK && !attackPerformed) {

            attackPerformed = true;

            performKineticAttack(
                    level,
                    attacker,
                    stack
            );
        }

        /*
         * Finish the attack automatically.
         */
        if (ticksUsed >= ATTACK_TICKS - 1) {
            attacker.releaseUsingItem();
        }
    }

    /*
     * ============================================================
     * RELEASE
     * ============================================================
     */

    @Override
    public boolean releaseUsing(
            ItemStack stack,
            net.minecraft.world.level.Level level,
            LivingEntity entity,
            int timeCharged
    ) {
        hitEntities.clear();
        attackPerformed = false;
        return true;
    }

    /*
     * ============================================================
     * KINETIC ATTACK
     * ============================================================
     */

    private void performKineticAttack(
            net.minecraft.world.level.Level level,
            LivingEntity attacker,
            ItemStack stack
    ) {

        /*
         * ========================================================
         * ATTACK DIRECTION
         * ========================================================
         */

        Vec3 attackerLookVector =
                attacker.getLookAngle();

        /*
         * Use the exact movement calculation used by
         * vanilla KineticWeapon.
         */
        Vec3 attackerMotion =
                KineticWeapon.getMotion(attacker);

        /*
         * Project the attacker's velocity onto their
         * attack direction.
         */
        double attackerSpeedProjection =
                attackerLookVector.dot(attackerMotion);

        /*
         * ========================================================
         * BASE DAMAGE
         * ========================================================
         */

        double baseDamage =
                attacker.getAttributeBaseValue(
                        Attributes.ATTACK_DAMAGE
                );

        /*
         * ========================================================
         * ATTACK RANGE
         * ========================================================
         *
         * IMPORTANT:
         *
         * We DON'T hard-code the range here.
         *
         * The range comes from the item's ATTACK_RANGE
         * component, exactly like vanilla.
         */

        AttackRange attackRange =
                attacker.getAttackRangeWith(stack);

        /*
         * ========================================================
         * ENTITY SWEEP
         * ========================================================
         */

        Collection<EntityHitResult> hits =
                ProjectileUtil.getHitEntitiesAlong(
                        attacker,
                        attackRange,
                        entity ->
                                PiercingWeapon.canHitEntity(
                                        attacker,
                                        entity
                                ),
                        ClipContext.Block.COLLIDER
                ).map(
                        ignored ->
                                java.util.List.<EntityHitResult>of(),

                        result ->
                                result
                );

        boolean affected = false;

        /*
         * ========================================================
         * PROCESS HITS
         * ========================================================
         */

        for (EntityHitResult hitResult : hits) {

            Entity target =
                    hitResult.getEntity();

            /*
             * Ender Dragon parts count as the dragon itself.
             */
            if (target instanceof EnderDragonPart dragonPart) {
                target = dragonPart.parentMob;
            }

            /*
             * Don't hit an entity twice during the same thrust.
             */
            if (!hitEntities.add(target.getId())) {
                continue;
            }

            /*
             * ====================================================
             * TARGET MOVEMENT
             * ====================================================
             */

            Vec3 targetMotion =
                    KineticWeapon.getMotion(target);

            /*
             * Project target movement onto our attack direction.
             */
            double targetSpeedProjection =
                    attackerLookVector.dot(targetMotion);

            /*
             * ====================================================
             * RELATIVE SPEED
             * ====================================================
             *
             * This is the important part that makes the weapon
             * kinetic like the vanilla spear.
             */

            double relativeSpeed =
                    Math.max(
                            0.0D,
                            attackerSpeedProjection
                                    - targetSpeedProjection
                    );

            /*
             * ====================================================
             * DAMAGE
             * ====================================================
             */

            float damage =
                    (float) baseDamage
                            + (float) Mth.floor(
                            relativeSpeed
                                    * DAMAGE_MULTIPLIER
                    );

            if (damage <= 0.0F) {
                continue;
            }

            /*
             * ====================================================
             * EQUIPMENT SLOT
             * ====================================================
             */

            EquipmentSlot slot =
                    attacker.getUsedItemHand()
                            == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.MAINHAND
                            : EquipmentSlot.OFFHAND;

            /*
             * ====================================================
             * ACTUAL HIT
             * ====================================================
             *
             * This uses Minecraft's existing stab attack logic,
             * which handles the actual entity damage/knockback.
             */

            boolean hit =
                    attacker.stabAttack(
                            slot,
                            target,
                            damage,

                            // deals damage
                            true,

                            // knockback
                            true,

                            // dismount
                            false
                    );

            affected |= hit;
        }

        /*
         * ========================================================
         * HIT FEEDBACK
         * ========================================================
         */

        if (affected) {

            /*
             * Vanilla attack feedback.
             */
            level.broadcastEntityEvent(
                    attacker,
                    (byte) 2
            );

            /*
             * Vanilla spear advancement trigger.
             */
            if (attacker instanceof ServerPlayer serverPlayer) {

                CriteriaTriggers.SPEAR_MOBS_TRIGGER.trigger(
                        serverPlayer,
                        attacker.stabbedEntities(
                                entity ->
                                        entity instanceof LivingEntity
                        )
                );
            }
        }
    }
}