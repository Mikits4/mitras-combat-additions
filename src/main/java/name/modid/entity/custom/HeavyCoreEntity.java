package name.modid.entity.custom;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
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

    private static final int CHARGE_DURATION_TICKS = 40;

    private static final double ORBIT_START_SPEED = -0.15D;

    private static final double ORBIT_SPEED_GROWTH = -0.50D;

    private static final double ORBIT_START_RADIUS = 0.20D;

    private static final double ORBIT_RADIUS_GROWTH = 0.60D;


    /*
     * -------------------------------------------------------------------------
     * THROW
     * -------------------------------------------------------------------------
     */

    private static final double THROW_MIN_SPEED = 0.20D;

    private static final double THROW_MAX_SPEED = 2.20D;

    private static final double THROW_MIN_SWING_SPEED =
            Math.abs(ORBIT_START_SPEED)
                    * ORBIT_START_RADIUS;

    private static final double THROW_FULL_SWING_SPEED =
            Math.abs(
                    ORBIT_START_SPEED
                            + ORBIT_SPEED_GROWTH
            )
                    * (
                    ORBIT_START_RADIUS
                            + ORBIT_RADIUS_GROWTH
            );

    private static final double THROW_SPEED_CURVE = 1.0D;

    private static final double THROW_UPWARD_VELOCITY = 0.02D;


    /*
     * -------------------------------------------------------------------------
     * PROJECTILE PHYSICS
     * -------------------------------------------------------------------------
     */

    private static final double PROJECTILE_GRAVITY = 0.025D;

    private static final double PROJECTILE_AIR_DRAG = 0.995D;


    /*
     * -------------------------------------------------------------------------
     * FLIGHT LIMITS
     * -------------------------------------------------------------------------
     */

    private static final int MAX_FLIGHT_TICKS = 80;

    private static final double MAX_THROW_DISTANCE = 40.0D;


    /*
     * -------------------------------------------------------------------------
     * HOOKING
     * -------------------------------------------------------------------------
     */

    /*
     * The grapple applies force continuously for this entire duration.
     *
     * 8 ticks = 0.4 seconds.
     */
    private static final int HOOK_LATCH_TICKS = 8;


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
     * Standing grapple uses this same value as its base.
     */
    private static final double ENTITY_PULL_STRENGTH = 0.40D;

    private static final double ENTITY_PULL_LIFT = 0.18D;

    private static final double ENTITY_PULL_MAX_SPEED = 1.50D;


    /*
     * -------------------------------------------------------------------------
     * STANDING GRAPPLE
     * -------------------------------------------------------------------------
     */

    /*
     * Same base value as crouching/entity pull.
     */
    private static final double GRAPPLE_STRENGTH =
            ENTITY_PULL_STRENGTH;

    /*
     * Additional horizontal force for each block of distance.
     */
    private static final double GRAPPLE_DISTANCE_SCALE = 0.04D;

    /*
     * Continuous upward acceleration per hooked tick.
     */
    private static final double GRAPPLE_VERTICAL_FORCE = 0.14D;

    /*
     * Maximum upward velocity reached by the continuous lift.
     */
    private static final double GRAPPLE_MAX_VERTICAL_SPEED = 1.20D;

    /*
     * Maximum horizontal grapple force applied per tick.
     */
    private static final double MAX_GRAPPLE_FORCE = 2.10D;


    /*
     * -------------------------------------------------------------------------
     * CHUNK LOADING
     * -------------------------------------------------------------------------
     *
     * The projectile is an independently ticking moving entity.
     *
     * Without an active chunk ticket, the server can stop ticking it once
     * it travels outside the normally simulated area.
     *
     * The ticket follows the projectile and guarantees its current chunk
     * and a small radius remain loaded/simulated.
     */

    private static final int GRAPPLE_CHUNK_TICKET_RADIUS = 2;


    /*
     * -------------------------------------------------------------------------
     * RETURN
     * -------------------------------------------------------------------------
     */

    private static final double RETURN_BASE_SPEED = 1.60D;

    private static final double RETURN_DISTANCE_SPEED = 0.04D;

    private static final double RETURN_MAX_SPEED = 2.40D;

    private static final double RETURN_CAPTURE_DISTANCE = 0.65D;


    /*
     * -------------------------------------------------------------------------
     * HAND POSITION
     * -------------------------------------------------------------------------
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

    /*
     * Current server chunk being kept alive for this projectile.
     *
     * Only used on the logical server.
     */
    private ChunkPos chunkTicketPosition = null;


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
         * Projectile gravity is handled manually.
         */
        setNoGravity(true);

        /*
         * Collision is handled explicitly with ProjectileUtil.
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
    // CHARGE
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
                        )
                        .add(
                                up.scale(
                                        Math.sin(orbitAngle)
                                                * radius
                                )
                        );


        Vec3 handPosition =
                getHandPosition(owner);


        /*
         * Calculate the VISUAL CENTER of the projectile.
         *
         * The hitbox is centered on X/Z and rises from entity position Y,
         * so setProjectileCenter() converts this visual center into the
         * entity position Minecraft expects.
         */
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

            spinAngle -=
                    360.0F;
        }


        while (spinAngle < 0.0F) {

            spinAngle +=
                    360.0F;
        }
    }


    // =========================================================================
    // PROJECTILE CENTER / HITBOX
    // =========================================================================

    /*
     * Sets the ENTITY POSITION so that the entity's bounding box is centered
     * on the supplied visual/projectile center.
     *
     * EntityDimensions height extends upward from position().Y.
     */
    private void setProjectileCenter(
            Vec3 center
    ) {

        double halfHeight =
                getDimensions(
                        Pose.STANDING
                ).height()
                        * 0.5D;


        setPos(
                center.x,
                center.y - halfHeight,
                center.z
        );
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


        setDeltaMovement(
                velocity
        );


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

            releaseChunkTicket();

            discard();

            return;
        }


        if (level().isClientSide()) {

            tickClient(owner);

            return;
        }


        ServerLevel serverLevel =
                (ServerLevel) level();


        /*
         * Refresh the chunk ticket BEFORE processing movement.
         *
         * This guarantees the current chunk is loaded when the entity
         * needs to tick.
         */
        updateChunkTicket(serverLevel);


        tickServer(owner);


        /*
         * Refresh again AFTER processing movement.
         *
         * This catches the new destination chunk immediately if the
         * projectile crossed a chunk boundary this tick.
         */
        if (isRemoved()) {

            releaseChunkTicket();

        } else {

            updateChunkTicket(serverLevel);
        }
    }


    // =========================================================================
    // REMOVAL
    // =========================================================================

    /*
     * discard() is final in Entity, so cleanup is done through remove().
     *
     * This catches normal discard(), kill/remove calls, and other entity
     * removal paths so the custom chunk ticket cannot be left behind.
     */
    @Override
    public void remove(
            RemovalReason reason
    ) {

        releaseChunkTicket();

        super.remove(reason);
    }


    // =========================================================================
    // SERVER CHUNK TICKET
    // =========================================================================

    private void updateChunkTicket(
            ServerLevel serverLevel
    ) {

        if (isRemoved()) {
            return;
        }


        BlockPos pos = blockPosition();

        ChunkPos newChunk =
                new ChunkPos(
                        pos.getX() >> 4,
                        pos.getZ() >> 4
                );


        if (
                chunkTicketPosition != null
                        && chunkTicketPosition.equals(newChunk)
        ) {
            return;
        }


        /*
         * Add the new ticket FIRST.
         *
         * This prevents a gap where the old chunk is unloaded before
         * the new chunk has been protected.
         */
        serverLevel.getChunkSource()
                .addTicketWithRadius(
                        TicketType.UNKNOWN,
                        newChunk,
                        GRAPPLE_CHUNK_TICKET_RADIUS
                );


        ChunkPos oldChunk =
                chunkTicketPosition;


        chunkTicketPosition =
                newChunk;


        if (oldChunk != null) {

            serverLevel.getChunkSource()
                    .removeTicketWithRadius(
                            TicketType.UNKNOWN,
                            oldChunk,
                            GRAPPLE_CHUNK_TICKET_RADIUS
                    );
        }
    }


    private void releaseChunkTicket() {

        if (chunkTicketPosition == null) {
            return;
        }


        if (
                level()
                        instanceof ServerLevel serverLevel
        ) {

            serverLevel.getChunkSource()
                    .removeTicketWithRadius(
                            TicketType.UNKNOWN,
                            chunkTicketPosition,
                            GRAPPLE_CHUNK_TICKET_RADIUS
                    );
        }


        chunkTicketPosition = null;
    }


    // =========================================================================
    // HURT
    // =========================================================================

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


        updateWobble(
                referenceAxis
        );
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


        setDeltaMovement(
                velocity
        );
    }


    // =========================================================================
    // SERVER TICK
    // =========================================================================

    private void tickServer(Player owner) {

        State state =
                getState();


        switch (state) {

            case CHARGING -> {

                tickCharging(owner);

                /*
                 * Swing damage is SERVER ONLY.
                 *
                 * This used to run from tickCharging(), which meant
                 * client-side charging could enter the damage code.
                 */
                tickSwingDamage(owner);
            }


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

            /*
             * The hit result is the visual impact CENTER.
             *
             * Convert it to the entity position so the bounding box
             * stays centered on the impact/projectile.
             */
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


        setDeltaMovement(
                velocity
        );


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
             * immediately pull the entity once,
             * then continue pulling every hooked tick.
             */
            pullEntityTowardPlayer(
                    owner,
                    target
            );


            return;
        }


        /*
         * Standing grapple:
         *
         * No one-time force is applied here.
         *
         * The complete grapple force is applied every hooked tick
         * in tickHooked().
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


        /*
         * Keep the projectile centered on the hooked entity.
         */
        setProjectileCenter(
                targetCenter
        );


        /*
         * This is the FLAIL movement.
         *
         * It does not clear the player's velocity.
         */
        setDeltaMovement(
                Vec3.ZERO
        );


        boolean reversePull =
                entityData.get(
                        DATA_REVERSE_PULL
                );


        if (reversePull) {

            /*
             * Crouch:
             * continuous entity pull.
             */
            pullEntityTowardPlayer(
                    owner,
                    target
            );

        } else {

            /*
             * Standing:
             * continuous player grapple force.
             *
             * This happens EVERY hooked tick.
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
         * Preserve the crouch grapple's original lift.
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


        target.setDeltaMovement(
                velocity
        );


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
         * Distance is calculated before normalization.
         */
        double distance =
                difference.length();


        /*
         * Horizontal direction only.
         *
         * This keeps the main grapple force forward instead of allowing
         * the target's vertical position to turn the grapple into a giant
         * upward launch.
         */
        Vec3 horizontalDirection =
                new Vec3(
                        difference.x,
                        0.0D,
                        difference.z
                );


        if (
                horizontalDirection.lengthSqr()
                        < 1.0E-6D
        ) {

            return;
        }


        horizontalDirection =
                horizontalDirection.normalize();


        /*
         * Base grapple strength is exactly the same as the crouch grapple.
         *
         * Distance adds additional horizontal force.
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
         */
        Vec3 horizontalPull =
                horizontalDirection.scale(
                        grappleForce
                );


        /*
         * Continuous vertical acceleration.
         */
        double newVerticalVelocity =
                player.getDeltaMovement().y
                        + GRAPPLE_VERTICAL_FORCE;


        /*
         * Cap upward velocity while still allowing ordinary downward
         * velocity to be corrected by the continuous lift.
         */
        newVerticalVelocity =
                Math.min(
                        newVerticalVelocity,
                        GRAPPLE_MAX_VERTICAL_SPEED
                );


        Vec3 velocity =
                player.getDeltaMovement();


        /*
         * Apply horizontal acceleration every hooked tick,
         * while applying vertical acceleration independently.
         */
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


        setDeltaMovement(
                Vec3.ZERO
        );
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

            setProjectileCenter(
                    target
            );


            setDeltaMovement(
                    Vec3.ZERO
            );


            discard();

            return;
        }


        if (distance < 1.0E-6D) {

            setProjectileCenter(
                    target
            );


            setDeltaMovement(
                    Vec3.ZERO
            );


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
                direction.scale(
                        step
                );


        if (step >= distance) {

            setProjectileCenter(
                    target
            );


            setDeltaMovement(
                    Vec3.ZERO
            );


            discard();

            return;
        }


        setDeltaMovement(
                movement
        );


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


        /*
         * Save the current hook target too.
         *
         * Otherwise reloading a world while the flail is hooked would
         * restore HOOKED state without restoring the actual target.
         */
        String hookedEntity =
                entityData.get(
                        DATA_HOOKED_ENTITY
                );


        if (
                hookedEntity != null
                        && !hookedEntity.isEmpty()
        ) {

            output.putString(
                    "HookedEntity",
                    hookedEntity
            );
        }
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

            setState(
                    State.READY
            );
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


        input.getString("HookedEntity")
                .ifPresent(
                        hookedEntity -> {

                            entityData.set(
                                    DATA_HOOKED_ENTITY,
                                    hookedEntity
                            );
                        }
                );
    }


    // =========================================================================
    // SIZE / HITBOX
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
}