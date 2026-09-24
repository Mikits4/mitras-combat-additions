package name.modid.entity.custom;

import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeavyCoreEntity extends Entity {

    // =========================================================================
    // TUNING
    // =========================================================================

    /*
     * -------------------------------------------------------------------------
     * CHARGE / WIND-UP
     * -------------------------------------------------------------------------
     */

    /*
     * Maximum wind-up time.
     *
     * 40 ticks = 2 seconds.
     */
    private static final int CHARGE_DURATION_TICKS = 40;

    /*
     * Angular orbit speed at minimum wind-up.
     *
     * Negative means the flail rotates in the chosen direction.
     */
    private static final double ORBIT_START_SPEED = -0.15D;

    /*
     * Additional angular speed at full wind-up.
     */
    private static final double ORBIT_SPEED_GROWTH = -0.50D;

    /*
     * Radius of the orbit at minimum charge.
     */
    private static final double ORBIT_START_RADIUS = 0.20D;

    /*
     * Additional orbit radius at full charge.
     */
    private static final double ORBIT_RADIUS_GROWTH = 0.60D;


    /*
     * -------------------------------------------------------------------------
     * THROW
     * -------------------------------------------------------------------------
     */

    /*
     * Minimum launch speed.
     *
     * A barely wound throw should basically fall away from the hand.
     */
    private static final double THROW_MIN_SPEED = 0.20D;

    /*
     * Maximum launch speed at full wind-up.
     */
    private static final double THROW_MAX_SPEED = 2.20D;

    /*
     * Physical swing speed at minimum charge.
     *
     * This is derived from the actual orbit settings so these values
     * stay synchronized if the orbit is changed later.
     */
    private static final double THROW_MIN_SWING_SPEED =
            Math.abs(ORBIT_START_SPEED)
                    * ORBIT_START_RADIUS;

    /*
     * Physical swing speed at maximum charge.
     */
    private static final double THROW_FULL_SWING_SPEED =
            Math.abs(
                    ORBIT_START_SPEED
                            + ORBIT_SPEED_GROWTH
            )
                    * (
                    ORBIT_START_RADIUS
                            + ORBIT_RADIUS_GROWTH
            );

    /*
     * 1.0 = linear conversion from swing speed to throw speed.
     *
     * The previous value of 2.0 made the middle of the wind-up
     * dramatically weaker than the full wind-up.
     */
    private static final double THROW_SPEED_CURVE = 1.0D;

    /*
     * Tiny upward release velocity.
     *
     * Gravity is responsible for the arc.
     */
    private static final double THROW_UPWARD_VELOCITY = 0.02D;


    /*
     * -------------------------------------------------------------------------
     * PROJECTILE PHYSICS
     * -------------------------------------------------------------------------
     */

    /*
     * Gravity applied manually every tick while flying.
     */
    private static final double PROJECTILE_GRAVITY = 0.025D;

    /*
     * Air drag.
     */
    private static final double PROJECTILE_AIR_DRAG = 0.995D;


    /*
     * -------------------------------------------------------------------------
     * FLIGHT LIMITS
     * -------------------------------------------------------------------------
     */

    /*
     * Maximum amount of time the projectile can remain flying.
     */
    private static final int MAX_FLIGHT_TICKS = 80;

    /*
     * Absolute maximum distance before automatic return.
     *
     * Full-power throws can now actually make use of their speed.
     */
    private static final double MAX_THROW_DISTANCE = 40.0D;


    /*
     * -------------------------------------------------------------------------
     * HOOKING
     * -------------------------------------------------------------------------
     */

    /*
     * Number of ticks the head remains attached to an entity.
     *
     * Increased from 3 to 8 so the grapple can continuously accelerate
     * the player instead of only giving a single burst.
     */
    private static final int HOOK_LATCH_TICKS = 4;


    /*
     * -------------------------------------------------------------------------
     * DAMAGE
     * -------------------------------------------------------------------------
     */

    private static final float SWING_DAMAGE = 5.0F;

    private static final float THROW_DAMAGE = 6.0F;

    private static final int SWING_HIT_COOLDOWN = 5;


    /*
     * -------------------------------------------------------------------------
     * ENTITY PULL
     * -------------------------------------------------------------------------
     */

    /*
     * Base strength of the crouching grapple.
     *
     * The standing grapple uses this exact same value as its base strength.
     */
    private static final double ENTITY_PULL_STRENGTH = 0.40D;

    private static final double ENTITY_PULL_LIFT = 1.50D;

    private static final double ENTITY_PULL_MAX_SPEED = 1.50D;


    /*
     * -------------------------------------------------------------------------
     * GRAPPLE
     * -------------------------------------------------------------------------
     */

    /*
     * Standing grapple starts at exactly the same base strength
     * as the crouching/entity pull.
     */
    private static final double GRAPPLE_STRENGTH =
            ENTITY_PULL_STRENGTH;

    /*
     * Additional horizontal force based on distance.
     *
     * The farther away the target is, the stronger the horizontal pull.
     */
    private static final double GRAPPLE_DISTANCE_SCALE = 0.04D;

    /*
     * Continuous upward acceleration applied every hooked tick.
     *
     * This is intentionally independent from the horizontal force.
     */
    private static final double GRAPPLE_VERTICAL_FORCE = 0.14D;

    /*
     * Maximum upward velocity from the continuous grapple lift.
     */
    private static final double GRAPPLE_MAX_VERTICAL_SPEED = 1.20D;

    /*
     * Maximum horizontal grapple force per hooked tick.
     */
    private static final double MAX_GRAPPLE_FORCE = 2.10D;


    /*
     * -------------------------------------------------------------------------
     * RETURN
     * -------------------------------------------------------------------------
     */

    private static final double RETURN_BASE_SPEED = 1.60D;

    private static final double RETURN_DISTANCE_SPEED = 0.04D;

    private static final double RETURN_MAX_SPEED = 2.40D;

    /*
     * When this close, snap to the player and discard.
     */
    private static final double RETURN_CAPTURE_DISTANCE = 0.65D;


    /*
     * -------------------------------------------------------------------------
     * HAND POSITION
     * -------------------------------------------------------------------------
     *
     * These match the current renderer.
     * Keeping both sides identical prevents the chain and orbit center
     * from drifting apart.
     */

    private static final double HAND_SIDE_OFFSET = 0.36D;

    private static final double HAND_FORWARD_OFFSET = 0.04D;

    private static final double HAND_HEIGHT_STANDING = 1.22D;

    private static final double HAND_HEIGHT_CROUCHING = 1.02D;


    /*
     * -------------------------------------------------------------------------
     * VISUAL SPIN
     * -------------------------------------------------------------------------
     */

    private static final float VISUAL_SPIN_START = 12.0F;

    private static final float VISUAL_SPIN_GROWTH = 32.0F;


    /*
     * -------------------------------------------------------------------------
     * WOBBLE
     * -------------------------------------------------------------------------
     */

    private static final double WOBBLE_ACCELERATION_SCALE = 260.0D;

    private static final double WOBBLE_MAX_ANGLE = 18.0D;

    private static final float WOBBLE_SPRING = 0.18F;

    private static final float WOBBLE_DAMPING = 0.80F;

    private static final float WOBBLE_SETTLE = 0.985F;


    // =========================================================================
    // SYNCHED DATA
    // =========================================================================

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.INT
            );

    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.STRING
            );

    private static final EntityDataAccessor<String> DATA_HOOKED_ENTITY =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.STRING
            );

    /*
     * true  = crouching / reverse fishing-rod pull
     * false = standing / grapple
     */
    private static final EntityDataAccessor<Boolean> DATA_REVERSE_PULL =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<Boolean> DATA_LEFT_HAND =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.BOOLEAN
            );


    // =========================================================================
    // INTERNAL STATE
    // =========================================================================

    private UUID ownerUuid;

    private int chargeTicks = 0;

    private double orbitAngle = 0.0D;

    private int flightTicks = 0;

    private int hookTicks = 0;

    private float spinAngle = 0.0F;

    private float wobblePitch = 0.0F;

    private float wobbleRoll = 0.0F;

    private float wobblePitchVelocity = 0.0F;

    private float wobbleRollVelocity = 0.0F;

    private Vec3 lastClientPosition = Vec3.ZERO;

    private Vec3 lastClientVelocity = Vec3.ZERO;

    private final Map<UUID, Integer> swingHitCooldowns =
            new HashMap<>();


    // =========================================================================
    // STATE
    // =========================================================================

    private enum State {
        READY,
        CHARGING,
        FLYING,
        HOOKED,
        RETURNING
    }


    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public HeavyCoreEntity(
            EntityType<? extends HeavyCoreEntity> type,
            Level level
    ) {
        super(type, level);

        /*
         * We handle projectile gravity ourselves.
         */
        setNoGravity(true);

        /*
         * Collision is handled with ProjectileUtil.
         */
        this.noPhysics = true;
    }


    // =========================================================================
    // STATE HELPERS
    // =========================================================================

    private State getState() {
        int value =
                entityData.get(DATA_STATE);

        if (
                value < 0
                        || value >= State.values().length
        ) {
            return State.READY;
        }

        return State.values()[value];
    }

    private void setState(State state) {
        entityData.set(
                DATA_STATE,
                state.ordinal()
        );
    }

    public boolean isReady() {
        return getState() == State.READY;
    }

    public boolean isCharging() {
        return getState() == State.CHARGING;
    }


    // =========================================================================
    // OWNER
    // =========================================================================

    public void setOwner(
            Player player,
            InteractionHand hand
    ) {
        ownerUuid =
                player.getUUID();

        entityData.set(
                DATA_OWNER,
                player.getUUID().toString()
        );

        boolean leftHand;

        if (hand == InteractionHand.OFF_HAND) {
            leftHand =
                    player.getMainArm()
                            == HumanoidArm.RIGHT;
        } else {
            leftHand =
                    player.getMainArm()
                            == HumanoidArm.LEFT;
        }

        entityData.set(
                DATA_LEFT_HAND,
                leftHand
        );
    }

    private Player getOwner() {
        String uuidString =
                entityData.get(DATA_OWNER);

        if (
                uuidString == null
                        || uuidString.isEmpty()
        ) {
            return null;
        }

        try {
            UUID uuid =
                    UUID.fromString(uuidString);

            return level()
                    .getPlayerByUUID(uuid);

        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public UUID getOwnerUuid() {
        if (ownerUuid != null) {
            return ownerUuid;
        }

        String value =
                entityData.get(DATA_OWNER);

        if (
                value == null
                        || value.isEmpty()
        ) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }


    // =========================================================================
    // CHARGING
    // =========================================================================

    public void startCharging() {
        setState(State.CHARGING);

        chargeTicks = 0;

        orbitAngle = 0.0D;

        spinAngle = 0.0F;

        flightTicks = 0;

        hookTicks = 0;

        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );

        setNoGravity(true);

        setDeltaMovement(Vec3.ZERO);
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public float getChargeProgress() {
        return Math.min(
                1.0F,
                chargeTicks
                        / (float) CHARGE_DURATION_TICKS
        );
    }


    /*
     * Returns the actual physical speed of the swinging head.
     *
     * IMPORTANT:
     *
     * The orbit can rotate in a negative direction.
     * Physical speed is still positive, so we use abs().
     */
    public double getSwingSpeed() {
        double progress =
                getChargeProgress();

        double orbitSpeed =
                ORBIT_START_SPEED
                        + (
                        ORBIT_SPEED_GROWTH
                                * progress
                );

        double radius =
                ORBIT_START_RADIUS
                        + (
                        ORBIT_RADIUS_GROWTH
                                * progress
                );

        return Math.abs(orbitSpeed)
                * radius;
    }


    /*
     * Converts actual physical swing speed into throw speed.
     *
     * Minimum swing = minimum throw.
     * Maximum swing = maximum throw.
     */
    public double getThrowSpeed() {
        double swingSpeed =
                getSwingSpeed();

        double swingRange =
                THROW_FULL_SWING_SPEED
                        - THROW_MIN_SWING_SPEED;

        if (swingRange <= 1.0E-6D) {
            return THROW_MIN_SPEED;
        }

        double normalized =
                (
                        swingSpeed
                                - THROW_MIN_SWING_SPEED
                )
                        / swingRange;

        normalized =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                normalized
                        )
                );

        double curved =
                Math.pow(
                        normalized,
                        THROW_SPEED_CURVE
                );

        return THROW_MIN_SPEED
                + (
                THROW_MAX_SPEED
                        - THROW_MIN_SPEED
        )
                * curved;
    }


    // =========================================================================
    // CHARGE ORBIT
    // =========================================================================

    private void tickCharging(Player owner) {

        chargeTicks++;

        if (chargeTicks > CHARGE_DURATION_TICKS) {
            chargeTicks =
                    CHARGE_DURATION_TICKS;
        }

        float progress =
                getChargeProgress();

        double orbitSpeed =
                ORBIT_START_SPEED
                        + (
                        ORBIT_SPEED_GROWTH
                                * progress
                );

        orbitAngle += orbitSpeed;

        orbitAngle %=
                Math.PI * 2.0D;

        if (orbitAngle < 0.0D) {
            orbitAngle +=
                    Math.PI * 2.0D;
        }


        Vec3 forward =
                owner.getLookAngle();

        if (forward.lengthSqr() < 1.0E-6D) {
            forward =
                    new Vec3(
                            0.0D,
                            0.0D,
                            1.0D
                    );
        } else {
            forward =
                    forward.normalize();
        }


        float yaw =
                owner.getYRot();

        double yawRadians =
                Math.toRadians(yaw);

        Vec3 right =
                new Vec3(
                        Math.cos(yawRadians),
                        0.0D,
                        Math.sin(yawRadians)
                ).normalize();


        Vec3 up =
                forward.cross(right);

        if (up.lengthSqr() < 1.0E-6D) {
            up =
                    new Vec3(
                            0.0D,
                            1.0D,
                            0.0D
                    );
        } else {
            up =
                    up.normalize();
        }


        double radius =
                ORBIT_START_RADIUS
                        + (
                        ORBIT_RADIUS_GROWTH
                                * progress
                );


        Vec3 orbitOffset =
                forward.scale(
                        Math.cos(orbitAngle)
                                * radius
                ).add(
                        up.scale(
                                Math.sin(orbitAngle)
                                        * radius
                        )
                );


        Vec3 handPosition =
                getHandPosition(owner);


        Vec3 projectileCenter =
                handPosition
                        .add(orbitOffset);

        setProjectileCenter(
                projectileCenter
        );

        setDeltaMovement(Vec3.ZERO);


        float spinSpeed =
                VISUAL_SPIN_START
                        + (
                        VISUAL_SPIN_GROWTH
                                * progress
                );

        spinAngle += spinSpeed;

        while (spinAngle >= 360.0F) {
            spinAngle -= 360.0F;
        }

        while (spinAngle < 0.0F) {
            spinAngle += 360.0F;
        }


        tickSwingDamage(owner);
    }


    // =========================================================================
    // LAUNCH
    // =========================================================================

    public void launch(
            Vec3 direction,
            boolean reversePull
    ) {
        if (!isCharging()) {
            return;
        }

        entityData.set(
                DATA_REVERSE_PULL,
                reversePull
        );

        setState(State.FLYING);

        flightTicks = 0;

        hookTicks = 0;


        double speed =
                getThrowSpeed();


        Vec3 launchDirection;

        if (direction.lengthSqr() < 1.0E-6D) {
            launchDirection =
                    new Vec3(
                            0.0D,
                            0.0D,
                            1.0D
                    );
        } else {
            launchDirection =
                    direction.normalize();
        }


        Vec3 velocity =
                launchDirection
                        .scale(speed)
                        .add(
                                0.0D,
                                THROW_UPWARD_VELOCITY,
                                0.0D
                        );

        setNoGravity(true);

        setDeltaMovement(velocity);

        hurtMarked = true;
    }


    // =========================================================================
    // MAIN TICK
    // =========================================================================

    @Override
    public void tick() {
        super.tick();

        Player owner =
                getOwner();

        if (
                owner == null
                        || !owner.isAlive()
        ) {
            discard();
            return;
        }


        if (level().isClientSide()) {
            tickClient(owner);
            return;
        }


        tickServer(owner);
    }


    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float damage
    ) {
        return false;
    }


    // =========================================================================
    // CLIENT TICK
    // =========================================================================

    private void tickClient(Player owner) {

        State state =
                getState();

        switch (state) {

            case CHARGING ->
                    tickCharging(owner);

            case FLYING ->
                    tickClientFlightPhysics();

            case HOOKED -> {
                Entity target =
                        getHookedEntity();

                if (
                        target != null
                                && target.isAlive()
                ) {
                    Vec3 center =
                            target.getBoundingBox()
                                    .getCenter();

                    setProjectileCenter(
                            center
                    );
                }
            }

            case RETURNING ->
                    tickReturning(owner);

            case READY -> {
                // Nothing.
            }
        }


        Vec3 referenceAxis;

        if (state == State.CHARGING) {
            referenceAxis =
                    owner.getLookAngle();
        } else {
            referenceAxis =
                    getDeltaMovement();

            if (
                    referenceAxis.lengthSqr()
                            < 1.0E-6D
            ) {
                referenceAxis =
                        owner.getLookAngle();
            }
        }

        updateWobble(referenceAxis);
    }


    private void tickClientFlightPhysics() {

        Vec3 velocity =
                getDeltaMovement();


        velocity =
                new Vec3(
                        velocity.x,
                        velocity.y
                                - PROJECTILE_GRAVITY,
                        velocity.z
                );


        velocity =
                velocity.scale(
                        PROJECTILE_AIR_DRAG
                );


        move(
                MoverType.SELF,
                velocity
        );


        setDeltaMovement(velocity);
    }


    // =========================================================================
    // SERVER TICK
    // =========================================================================

    private void tickServer(Player owner) {

        State state =
                getState();

        switch (state) {

            case CHARGING ->
                    tickCharging(owner);

            case FLYING ->
                    tickFlying(owner);

            case HOOKED ->
                    tickHooked(owner);

            case RETURNING ->
                    tickReturning(owner);

            case READY -> {
                // Nothing.
            }
        }
    }


    // =========================================================================
    // FLYING
    // =========================================================================

    private void tickFlying(Player owner) {

        flightTicks++;


        Vec3 velocity =
                getDeltaMovement();


        if (velocity.lengthSqr() < 1.0E-8D) {
            beginReturning();
            return;
        }


        HitResult hit =
                ProjectileUtil.getHitResultOnMoveVector(
                        this,
                        this::canHitEntity
                );


        if (
                hit.getType()
                        != HitResult.Type.MISS
        ) {

            setProjectileCenter(
                    hit.getLocation()
            );


            if (
                    hit instanceof EntityHitResult entityHit
            ) {
                handleEntityHit(
                        owner,
                        entityHit
                );
            } else {
                beginReturning();
            }

            return;
        }


        velocity =
                new Vec3(
                        velocity.x,
                        velocity.y
                                - PROJECTILE_GRAVITY,
                        velocity.z
                );


        velocity =
                velocity.scale(
                        PROJECTILE_AIR_DRAG
                );


        move(
                MoverType.SELF,
                velocity
        );

        setDeltaMovement(velocity);


        if (
                flightTicks
                        >= MAX_FLIGHT_TICKS
                        || distanceTo(owner)
                        >= MAX_THROW_DISTANCE
        ) {
            beginReturning();
            return;
        }


        hurtMarked = true;
    }


    // =========================================================================
    // ENTITY HIT
    // =========================================================================

    private void handleEntityHit(
            Player owner,
            EntityHitResult hit
    ) {

        Entity target =
                hit.getEntity();


        if (!canHitEntity(target)) {
            beginReturning();
            return;
        }


        entityData.set(
                DATA_HOOKED_ENTITY,
                target.getUUID().toString()
        );


        hookTicks = 0;


        setState(State.HOOKED);


        setDeltaMovement(Vec3.ZERO);


        boolean reversePull =
                entityData.get(
                        DATA_REVERSE_PULL
                );


        if (
                target instanceof LivingEntity livingTarget
        ) {
            livingTarget.hurt(
                    owner.damageSources()
                            .playerAttack(owner),
                    THROW_DAMAGE
            );
        }


        if (reversePull) {

            /*
             * Crouching mode:
             * pull the entity immediately.
             * Continued pulling happens every hooked tick.
             */
            pullEntityTowardPlayer(
                    owner,
                    target
            );

            return;
        }


        /*
         * Standing mode:
         *
         * No one-time launch is applied here.
         *
         * The full grapple force is continuously applied
         * by tickHooked() for the entire hook duration.
         */
    }


    // =========================================================================
    // HOOKED ENTITY
    // =========================================================================

    private Entity getHookedEntity() {

        String uuidString =
                entityData.get(
                        DATA_HOOKED_ENTITY
                );


        if (
                uuidString == null
                        || uuidString.isEmpty()
        ) {
            return null;
        }


        try {

            UUID uuid =
                    UUID.fromString(uuidString);

            return level().getEntity(uuid);

        } catch (IllegalArgumentException ignored) {

            return null;
        }
    }


    private void tickHooked(Player owner) {

        Entity target =
                getHookedEntity();


        if (
                target == null
                        || !target.isAlive()
                        || target == owner
        ) {
            beginReturning();
            return;
        }


        hookTicks++;


        Vec3 targetCenter =
                target.getBoundingBox()
                        .getCenter();


        setProjectileCenter(
                targetCenter
        );


        /*
         * This is the FLAIL's movement, not the player's.
         */
        setDeltaMovement(Vec3.ZERO);


        boolean reversePull =
                entityData.get(
                        DATA_REVERSE_PULL
                );


        if (reversePull) {

            /*
             * Crouch mode:
             * pull entity toward player every hooked tick.
             */
            pullEntityTowardPlayer(
                    owner,
                    target
            );

        } else {

            /*
             * Standing grapple:
             *
             * Apply the grapple continuously for EVERY
             * hooked tick.
             *
             * This means both horizontal and vertical
             * movement continue while attached.
             */
            pullPlayerTowardEntity(
                    owner,
                    target
            );
        }


        if (
                hookTicks
                        >= HOOK_LATCH_TICKS
        ) {
            beginReturning();
        }
    }


    // =========================================================================
    // PULL ENTITY TOWARD PLAYER
    // =========================================================================

    private void pullEntityTowardPlayer(
            Player owner,
            Entity target
    ) {

        Vec3 targetPosition =
                target.getBoundingBox()
                        .getCenter();

        Vec3 ownerPosition =
                owner.getEyePosition();


        Vec3 direction =
                ownerPosition
                        .subtract(targetPosition);


        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }


        direction =
                direction.normalize();


        /*
         * Keep the crouch grapple's original lift behavior.
         */
        direction =
                new Vec3(
                        direction.x,
                        direction.y
                                + ENTITY_PULL_LIFT,
                        direction.z
                ).normalize();


        Vec3 pull =
                direction.scale(
                        ENTITY_PULL_STRENGTH
                );


        Vec3 velocity =
                target.getDeltaMovement()
                        .add(pull);


        if (
                velocity.length()
                        > ENTITY_PULL_MAX_SPEED
        ) {
            velocity =
                    velocity.normalize()
                            .scale(
                                    ENTITY_PULL_MAX_SPEED
                            );
        }


        target.setDeltaMovement(velocity);

        target.hurtMarked = true;
    }


    // =========================================================================
    // PULL PLAYER TOWARD ENTITY
    // =========================================================================

    private void pullPlayerTowardEntity(
            Player player,
            Entity target
    ) {

        Vec3 targetPosition =
                target.getBoundingBox()
                        .getCenter();


        Vec3 playerPosition =
                player.getEyePosition();


        Vec3 difference =
                targetPosition
                        .subtract(playerPosition);


        if (difference.lengthSqr() < 1.0E-6D) {
            return;
        }


        /*
         * Measure distance BEFORE normalizing.
         *
         * This is used for distance-based grapple scaling.
         */
        double distance =
                difference.length();


        /*
         * Horizontal direction toward the target.
         *
         * Y is deliberately ignored here so the main grapple
         * remains a forward pull rather than a vertical launch.
         */
        Vec3 horizontalDirection =
                new Vec3(
                        difference.x,
                        0.0D,
                        difference.z
                );


        if (horizontalDirection.lengthSqr()
                < 1.0E-6D) {

            horizontalDirection =
                    Vec3.ZERO;

        } else {

            horizontalDirection =
                    horizontalDirection.normalize();
        }


        /*
         * Start with the same base strength as the crouch grapple.
         *
         * Then increase horizontal force with distance.
         */
        double grappleForce =
                GRAPPLE_STRENGTH
                        + (
                        distance
                                * GRAPPLE_DISTANCE_SCALE
                );


        grappleForce =
                Math.min(
                        grappleForce,
                        MAX_GRAPPLE_FORCE
                );


        /*
         * Continuous horizontal force.
         *
         * This is applied every hooked tick.
         */
        Vec3 horizontalPull =
                horizontalDirection.scale(
                        grappleForce
                );


        /*
         * Continuous vertical force.
         *
         * This is also applied every hooked tick.
         */
        double newVerticalVelocity =
                player.getDeltaMovement().y
                        + GRAPPLE_VERTICAL_FORCE;


        /*
         * Limit upward velocity so the player does not
         * accelerate upward forever during the grapple.
         */
        newVerticalVelocity =
                Math.min(
                        newVerticalVelocity,
                        GRAPPLE_MAX_VERTICAL_SPEED
                );


        /*
         * Preserve the player's current velocity while adding
         * the continuous grapple force.
         */
        Vec3 velocity =
                player.getDeltaMovement();


        velocity =
                new Vec3(
                        velocity.x
                                + horizontalPull.x,
                        newVerticalVelocity,
                        velocity.z
                                + horizontalPull.z
                );


        player.setDeltaMovement(
                velocity
        );


        player.hurtMarked = true;
    }


    // =========================================================================
    // RETURN
    // =========================================================================

    private void beginReturning() {

        setState(State.RETURNING);


        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );


        setNoGravity(true);


        setDeltaMovement(Vec3.ZERO);
    }


    private void tickReturning(Player owner) {

        Vec3 target =
                owner.getBoundingBox()
                        .getCenter();


        Vec3 difference =
                target.subtract(
                        position()
                );


        double distance =
                difference.length();


        if (
                distance
                        <= RETURN_CAPTURE_DISTANCE
        ) {

            setPos(
                    target.x,
                    target.y,
                    target.z
            );

            setDeltaMovement(Vec3.ZERO);

            discard();

            return;
        }


        if (distance < 1.0E-6D) {

            setPos(
                    target.x,
                    target.y,
                    target.z
            );

            setDeltaMovement(Vec3.ZERO);

            discard();

            return;
        }


        double speed =
                Math.min(
                        RETURN_BASE_SPEED
                                + (
                                distance
                                        * RETURN_DISTANCE_SPEED
                        ),
                        RETURN_MAX_SPEED
                );


        Vec3 direction =
                difference.normalize();


        double step =
                Math.min(
                        speed,
                        distance
                );


        Vec3 movement =
                direction.scale(step);


        if (step >= distance) {

            setPos(
                    target.x,
                    target.y,
                    target.z
            );

            setDeltaMovement(Vec3.ZERO);

            discard();

            return;
        }


        setDeltaMovement(movement);

        move(
                MoverType.SELF,
                movement
        );

        hurtMarked = true;
    }


    // =========================================================================
    // COLLISION FILTER
    // =========================================================================

    private boolean canHitEntity(
            Entity entity
    ) {

        if (entity == this) {
            return false;
        }

        if (entity == getOwner()) {
            return false;
        }

        if (!entity.isAlive()) {
            return false;
        }

        if (
                entity instanceof Player player
                        && player.isSpectator()
        ) {
            return false;
        }

        return entity.isPickable();
    }


    // =========================================================================
    // SWING DAMAGE
    // =========================================================================

    private void tickSwingDamage(
            Player owner
    ) {

        swingHitCooldowns.replaceAll(
                (uuid, cooldown) ->
                        cooldown - 1
        );


        swingHitCooldowns.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= 0
                );


        for (
                Entity target :
                level().getEntities(
                        this,
                        getBoundingBox()
                                .inflate(0.35D),
                        this::canHitEntity
                )
        ) {

            if (target == owner) {
                continue;
            }


            if (
                    swingHitCooldowns
                            .containsKey(
                                    target.getUUID()
                            )
            ) {
                continue;
            }


            target.hurt(
                    owner.damageSources()
                            .playerAttack(owner),
                    SWING_DAMAGE
            );


            swingHitCooldowns.put(
                    target.getUUID(),
                    SWING_HIT_COOLDOWN
            );
        }
    }


    // =========================================================================
    // RENDERER ACCESS
    // =========================================================================

    public float getSpinAngle(
            float partialTick
    ) {

        if (!isCharging()) {
            return spinAngle;
        }


        float progress =
                getChargeProgress();


        float spinSpeed =
                VISUAL_SPIN_START
                        + (
                        VISUAL_SPIN_GROWTH
                                * progress
                );


        return spinAngle
                + (
                spinSpeed
                        * partialTick
        );
    }


    public float getWobblePitch() {
        return wobblePitch;
    }


    public float getWobbleRoll() {
        return wobbleRoll;
    }


    // =========================================================================
    // WOBBLE
    // =========================================================================

    private void updateWobble(
            Vec3 referenceAxis
    ) {

        Vec3 movement =
                position()
                        .subtract(
                                lastClientPosition
                        );


        Vec3 acceleration =
                movement.subtract(
                        lastClientVelocity
                );


        Vec3 axis =
                referenceAxis;


        if (axis.lengthSqr() < 1.0E-6D) {
            axis =
                    new Vec3(
                            0.0D,
                            0.0D,
                            1.0D
                    );
        } else {
            axis =
                    axis.normalize();
        }


        Vec3 worldUp =
                new Vec3(
                        0.0D,
                        1.0D,
                        0.0D
                );


        Vec3 right =
                axis.cross(worldUp);


        if (right.lengthSqr() < 1.0E-6D) {
            right =
                    new Vec3(
                            1.0D,
                            0.0D,
                            0.0D
                    );
        } else {
            right =
                    right.normalize();
        }


        Vec3 up =
                right.cross(axis);


        if (up.lengthSqr() < 1.0E-6D) {
            up =
                    new Vec3(
                            0.0D,
                            1.0D,
                            0.0D
                    );
        } else {
            up =
                    up.normalize();
        }


        double pitchTarget =
                acceleration.dot(up)
                        * WOBBLE_ACCELERATION_SCALE;


        double rollTarget =
                acceleration.dot(right)
                        * WOBBLE_ACCELERATION_SCALE;


        pitchTarget =
                Math.max(
                        -WOBBLE_MAX_ANGLE,
                        Math.min(
                                WOBBLE_MAX_ANGLE,
                                pitchTarget
                        )
                );


        rollTarget =
                Math.max(
                        -WOBBLE_MAX_ANGLE,
                        Math.min(
                                WOBBLE_MAX_ANGLE,
                                rollTarget
                        )
                );


        wobblePitchVelocity +=
                (
                        (float) pitchTarget
                                - wobblePitch
                ) * WOBBLE_SPRING;


        wobbleRollVelocity +=
                (
                        (float) rollTarget
                                - wobbleRoll
                ) * WOBBLE_SPRING;


        wobblePitchVelocity *=
                WOBBLE_DAMPING;

        wobbleRollVelocity *=
                WOBBLE_DAMPING;


        wobblePitch +=
                wobblePitchVelocity;

        wobbleRoll +=
                wobbleRollVelocity;


        wobblePitch *=
                WOBBLE_SETTLE;

        wobbleRoll *=
                WOBBLE_SETTLE;


        lastClientPosition =
                position();

        lastClientVelocity =
                movement;
    }


    // =========================================================================
    // HAND POSITION
    // =========================================================================

    private Vec3 getHandPosition(
            Player player
    ) {

        float yaw =
                player.getYRot();


        double yawRadians =
                Math.toRadians(yaw);


        Vec3 forward =
                new Vec3(
                        -Math.sin(yawRadians),
                        0.0D,
                        Math.cos(yawRadians)
                );


        Vec3 right =
                new Vec3(
                        Math.cos(yawRadians),
                        0.0D,
                        Math.sin(yawRadians)
                );


        boolean leftHand =
                isLeftHand();


        double sideOffset =
                leftHand
                        ? -HAND_SIDE_OFFSET
                        : HAND_SIDE_OFFSET;


        double handHeight =
                player.isCrouching()
                        ? HAND_HEIGHT_CROUCHING
                        : HAND_HEIGHT_STANDING;


        return player.position()
                .add(
                        right.scale(
                                sideOffset
                        )
                )
                .add(
                        forward.scale(
                                HAND_FORWARD_OFFSET
                        )
                )
                .add(
                        0.0D,
                        handHeight,
                        0.0D
                );
    }


    public boolean isLeftHand() {
        return entityData.get(
                DATA_LEFT_HAND
        );
    }


    // =========================================================================
    // SYNCHED DATA
    // =========================================================================

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {

        builder.define(
                DATA_STATE,
                State.READY.ordinal()
        );


        builder.define(
                DATA_OWNER,
                ""
        );


        builder.define(
                DATA_HOOKED_ENTITY,
                ""
        );


        builder.define(
                DATA_REVERSE_PULL,
                false
        );


        builder.define(
                DATA_LEFT_HAND,
                false
        );
    }


    // =========================================================================
    // SAVE / LOAD
    // =========================================================================

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output
    ) {

        if (ownerUuid != null) {
            output.putString(
                    "Owner",
                    ownerUuid.toString()
            );
        }


        output.putInt(
                "State",
                getState().ordinal()
        );


        output.putBoolean(
                "ReversePull",
                entityData.get(
                        DATA_REVERSE_PULL
                )
        );


        output.putBoolean(
                "LeftHand",
                entityData.get(
                        DATA_LEFT_HAND
                )
        );
    }


    @Override
    protected void readAdditionalSaveData(
            ValueInput input
    ) {

        input.getString("Owner")
                .ifPresent(
                        owner -> {
                            try {

                                ownerUuid =
                                        UUID.fromString(
                                                owner
                                        );

                                entityData.set(
                                        DATA_OWNER,
                                        owner
                                );

                            } catch (
                                    IllegalArgumentException ignored
                            ) {

                                ownerUuid = null;

                                entityData.set(
                                        DATA_OWNER,
                                        ""
                                );
                            }
                        }
                );


        int stateId =
                input.getInt("State")
                        .orElse(
                                State.READY.ordinal()
                        );


        if (
                stateId >= 0
                        && stateId
                        < State.values().length
        ) {

            setState(
                    State.values()[stateId]
            );

        } else {

            setState(State.READY);
        }


        boolean reversePull =
                input.read(
                        "ReversePull",
                        Codec.BOOL
                ).orElse(false);


        entityData.set(
                DATA_REVERSE_PULL,
                reversePull
        );


        boolean leftHand =
                input.read(
                        "LeftHand",
                        Codec.BOOL
                ).orElse(false);


        entityData.set(
                DATA_LEFT_HAND,
                leftHand
        );
    }


    // =========================================================================
    // SIZE
    // =========================================================================

    @Override
    public EntityDimensions getDimensions(
            Pose pose
    ) {
        return EntityDimensions.fixed(
                0.5F,
                0.5F
        );
    }

    private void setProjectileCenter(
            Vec3 center
    ) {

        double halfHeight =
                getDimensions(Pose.STANDING).height()
                        * 0.5D;

        setPos(
                center.x,
                center.y - halfHeight,
                center.z
        );
    }
}