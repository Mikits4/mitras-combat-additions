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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.UUID;

public class HeavyCoreEntity extends Entity {
    private static final double GRAPPLE_LIFT = 0.45D;
    private static final double ENTITY_PULL_LIFT = 0.18D;

    /*
     * How long a throw can remain in the air.
     */
    private static final int MAX_FLIGHT_TICKS = 80;

    /*
     * Maximum distance before the core gives up and returns.
     */
    private static final double MAX_THROW_DISTANCE = 24.0D;

    /*
     * How long a hooked entity can remain attached.
     */
    private static final int MAX_HOOK_TICKS = 60;

    /*
     * Charge time.
     * 40 ticks = 2 seconds.
     */
    private static final int MAX_CHARGE_TICKS = 40;

    /*
     * Throw speed range.
     */
    private static final double MIN_THROW_SPEED = 1.6D;
    private static final double MAX_THROW_SPEED = 4.8D;

    /*
     * Return speed.
     */
    private static final double RETURN_SPEED = 1.6D;

    /*
     * Stronger than a fishing rod.
     *
     * Vanilla fishing-hook pulling is roughly around 0.1 per tick.
     */
    private static final double ENTITY_PULL_STRENGTH = 0.40D;

    /*
     * Player grapple acceleration.
     */
    private static final double GRAPPLE_STRENGTH = 0.40D;

    /*
     * Maximum speed while being grappled.
     */
    private static final double MAX_GRAPPLE_SPEED = 1.65D;

    private UUID ownerUuid;

    /*
     * Server and client both maintain these locally.
     * This means charging/animation does not need a server
     * teleport every tick.
     */
    private int chargeTicks = 0;
    private double orbitAngle = 0.0D;

    private int flightTicks = 0;
    private int hookTicks = 0;

    /*
     * Smooth visual spin.
     *
     * Renderer can interpolate it every frame.
     */
    private float spinAngle = 0.0F;

    private float wobblePitch = 0.0F;
    private float wobbleRoll = 0.0F;

    private float wobblePitchVelocity = 0.0F;
    private float wobbleRollVelocity = 0.0F;

    private static final float SWING_DAMAGE = 5.0F;
    private static final float THROW_DAMAGE = 6.0F;

    private static final int SWING_HIT_COOLDOWN = 5;

    private final java.util.Map<UUID, Integer> swingHitCooldowns =
            new java.util.HashMap<>();

    private Vec3 lastClientPosition = Vec3.ZERO;
    private Vec3 lastClientVelocity = Vec3.ZERO;

    private enum State {
        READY,
        CHARGING,
        FLYING,
        HOOKED,
        RETURNING
    }

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
     * true = crouching/fishing-rod mode
     * false = standing/grapple mode
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



    public HeavyCoreEntity(
            EntityType<? extends HeavyCoreEntity> type,
            Level level
    ) {
        super(type, level);

        /*
         * Normal entity.
         *
         * We deliberately are NOT a FallingBlockEntity.
         */
        setNoGravity(true);

        /*
         * We perform projectile collision ourselves with
         * ProjectileUtil.
         */
        this.noPhysics = true;
    }

    // -------------------------------------------------------------------------
    // STATE
    // -------------------------------------------------------------------------

    private State getState() {
        int value = entityData.get(DATA_STATE);

        if (value < 0 || value >= State.values().length) {
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

    // -------------------------------------------------------------------------
    // OWNER
    // -------------------------------------------------------------------------

    public void setOwner(
            Player player,
            InteractionHand hand
    ) {
        ownerUuid = player.getUUID();

        entityData.set(
                DATA_OWNER,
                player.getUUID().toString()
        );

        boolean leftHand =
                hand == InteractionHand.OFF_HAND
                        ? player.getMainArm() == HumanoidArm.RIGHT
                        : player.getMainArm() == HumanoidArm.LEFT;

        entityData.set(
                DATA_LEFT_HAND,
                leftHand
        );
    }

    private Player getOwner() {
        String uuidString = entityData.get(DATA_OWNER);

        if (uuidString == null || uuidString.isEmpty()) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            return level().getPlayerByUUID(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // CHARGING
    // -------------------------------------------------------------------------

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

    /*
     * 0.0 -> 1.0 charge amount.
     */
    public float getChargeProgress() {
        return Math.min(
                1.0F,
                chargeTicks / (float) MAX_CHARGE_TICKS
        );
    }

    /*
     * Throw speed based on charge.
     *
     * Uses quadratic scaling so the last part of the wind-up
     * matters much more.
     */
    public double getThrowSpeed() {
        double progress = getChargeProgress();

        double curvedProgress =
                progress * progress;

        return MIN_THROW_SPEED
                + (MAX_THROW_SPEED - MIN_THROW_SPEED)
                * curvedProgress;
    }

    // -------------------------------------------------------------------------
    // THROW
    // -------------------------------------------------------------------------

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

        double speed = getThrowSpeed();

        Vec3 velocity;

        if (direction.lengthSqr() < 1.0E-6D) {
            velocity = Vec3.ZERO;
        } else {
            velocity = direction
                    .normalize()
                    .scale(speed);
        }

        setNoGravity(true);
        setDeltaMovement(velocity);

        hurtMarked = true;
    }

    // -------------------------------------------------------------------------
    // TICK
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        Player owner = getOwner();

        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }

        /*
         * Client:
         *
         * Simulate local movement as well.
         * This prevents the projectile from visually relying
         * only on ~20 server position updates per second.
         */
        if (level().isClientSide()) {
            tickClient(owner);
            return;
        }

        /*
         * Server:
         */
        tickServer(owner);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    // -------------------------------------------------------------------------
    // CLIENT TICK
    // -------------------------------------------------------------------------

    private void tickClient(Player owner) {
        State state = getState();

        if (state == State.CHARGING) {
            tickCharging(owner);
        } else if (state == State.FLYING) {
            move(
                    MoverType.SELF,
                    getDeltaMovement()
            );

            setDeltaMovement(
                    getDeltaMovement()
                            .scale(0.99D)
            );
        } else if (state == State.HOOKED) {
            Entity target = getHookedEntity();

            if (target != null && target.isAlive()) {
                Vec3 center =
                        target.getBoundingBox()
                                .getCenter();

                setPos(
                        center.x,
                        center.y,
                        center.z
                );
            }
        } else if (state == State.RETURNING) {
            tickReturning(owner);
        }

        /*
         * Use the current movement direction as the wobble reference.
         *
         * During charging the head direction becomes the reference,
         * so the wobble/orientation follows the player's view.
         */
        Vec3 referenceAxis;

        if (state == State.CHARGING) {
            referenceAxis = owner.getLookAngle();
        } else {
            referenceAxis = getDeltaMovement();

            if (referenceAxis.lengthSqr() < 1.0E-6D) {
                referenceAxis = owner.getLookAngle();
            }
        }

        updateWobble(referenceAxis);
    }

    // -------------------------------------------------------------------------
    // SERVER TICK
    // -------------------------------------------------------------------------

    private void tickServer(Player owner) {
        State state = getState();

        switch (state) {
            case CHARGING -> tickCharging(owner);
            case FLYING -> tickFlying(owner);
            case HOOKED -> tickHooked(owner);
            case RETURNING -> tickReturning(owner);
            case READY -> {
                // Nothing.
            }
        }
    }

    // -------------------------------------------------------------------------
    // CHARGE ORBIT
    // -------------------------------------------------------------------------

    private void tickCharging(Player owner) {
        chargeTicks++;

        if (chargeTicks > MAX_CHARGE_TICKS) {
            chargeTicks = MAX_CHARGE_TICKS;
        }

        float progress = getChargeProgress();

        /*
         * The orbit gets faster as the flail is wound up.
         */
        double orbitSpeed =
                0.16D
                        + (0.26D * progress);

        orbitAngle += orbitSpeed;

        if (orbitAngle >= Math.PI * 2.0D) {
            orbitAngle -= Math.PI * 2.0D;
        }

        /*
         * ---------------------------------------------------------
         * PLAYER HEAD DIRECTION
         * ---------------------------------------------------------
         *
         * This is the direction the player is looking.
         * We want pitch to affect the plane of the wind-up.
         */
        Vec3 forward = owner.getLookAngle();

        if (forward.lengthSqr() < 1.0E-6D) {
            forward = new Vec3(
                    0.0D,
                    0.0D,
                    1.0D
            );
        } else {
            forward = forward.normalize();
        }

        /*
         * Horizontal right vector based on the player's yaw.
         *
         * Unlike forward, this remains horizontal.
         */
        float yaw = owner.getYRot();
        double yawRadians = Math.toRadians(yaw);

        Vec3 right = new Vec3(
                Math.cos(yawRadians),
                0.0D,
                Math.sin(yawRadians)
        ).normalize();

        /*
         * Create a vector perpendicular to both right and forward.
         *
         * At normal head pitch this is approximately world-up.
         *
         * Looking upward/downward tilts this vector accordingly.
         */
        Vec3 up =
                forward.cross(right);

        if (up.lengthSqr() < 1.0E-6D) {
            up = new Vec3(
                    0.0D,
                    1.0D,
                    0.0D
            );
        } else {
            up = up.normalize();
        }

        /*
         * ---------------------------------------------------------
         * ORBIT
         * ---------------------------------------------------------
         *
         * The orbit plane is made from:
         *
         *     forward + up
         *
         * with right as the plane's normal.
         *
         * That means:
         *
         *   look straight ahead → vertical-ish wind-up
         *   look upward         → wind-up pitches upward
         *   look downward       → wind-up pitches downward
         */
        double radius =
                1.85D
                        + (0.55D * progress);

        Vec3 orbitOffset =
                forward.scale(
                        Math.cos(orbitAngle) * radius
                ).add(
                        up.scale(
                                Math.sin(orbitAngle) * radius
                        )
                );


        /*
         * ---------------------------------------------------------
         * HAND CENTER
         * ---------------------------------------------------------
         *
         * The flail now swings around the player's hand,
         * not around their head.
         */
        Vec3 handPosition =
                getHandPosition(owner);

        Vec3 target =
                handPosition.add(orbitOffset);

        setPos(
                target.x,
                target.y,
                target.z
        );

        setDeltaMovement(Vec3.ZERO);

        /*
         * Visual spin.
         */
        float spinSpeed =
                12.0F
                        + (32.0F * progress);

        spinAngle += spinSpeed;

        if (spinAngle >= 360.0F) {
            spinAngle -= 360.0F;
        }
    }

    // -------------------------------------------------------------------------
    // FLYING
    // -------------------------------------------------------------------------

    private void tickFlying(Player owner) {
        flightTicks++;

        Vec3 velocity = getDeltaMovement();

        if (velocity.lengthSqr() < 1.0E-6D) {
            beginReturning();
            return;
        }

        /*
         * IMPORTANT:
         *
         * We check the entire movement vector before moving.
         * ProjectileUtil performs the block/entity raycast, so
         * fast throws cannot simply tunnel through a target.
         */
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(
                this,
                this::canHitEntity
        );

        if (hit.getType() != HitResult.Type.MISS) {
            setPos(
                    hit.getLocation().x,
                    hit.getLocation().y,
                    hit.getLocation().z
            );

            if (hit instanceof EntityHitResult entityHit) {
                handleEntityHit(
                        owner,
                        entityHit
                );
            } else {
                /*
                 * Block hit.
                 *
                 * We aren't making walls grapple points here;
                 * the core simply returns.
                 */
                beginReturning();
            }

            return;
        }

        /*
         * Move on the server.
         */
        move(
                MoverType.SELF,
                velocity
        );

        /*
         * Slight drag.
         */
        setDeltaMovement(
                velocity.scale(0.99D)
        );

        /*
         * Safety limits.
         */
        if (
                flightTicks >= MAX_FLIGHT_TICKS
                        || distanceTo(owner) >= MAX_THROW_DISTANCE
        ) {
            beginReturning();
            return;
        }

        hurtMarked = true;
    }

    // -------------------------------------------------------------------------
    // ENTITY HIT
    // -------------------------------------------------------------------------

    private void handleEntityHit(
            Player owner,
            EntityHitResult hit
    ) {
        Entity target = hit.getEntity();

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
                entityData.get(DATA_REVERSE_PULL);

        Vec3 targetCenter =
                target.getBoundingBox().getCenter();


        if (target instanceof LivingEntity livingTarget) {
            livingTarget.hurt(
                    owner.damageSources().playerAttack(owner),
                    5.0F
            );
        }

        /*
         * ---------------------------------------------------------
         * CROUCHING = FISHING ROD MODE
         * ---------------------------------------------------------
         */


        if (reversePull) {

            // Pull starts immediately.
            pullEntityTowardPlayer(
                    owner,
                    target
            );

            return;
        }

        /*
         * ---------------------------------------------------------
         * STANDING = GRAPPLE MODE
         * ---------------------------------------------------------
         *
         * Give the player an immediate burst toward the target,
         * with an upward component so the grapple actually lifts
         * the player off the ground.
         */
        Vec3 playerPosition =
                owner.getEyePosition();

        Vec3 direction =
                targetCenter.subtract(playerPosition);

        if (direction.lengthSqr() > 1.0E-6D) {
            direction = direction.normalize();

            /*
             * Add upward lift AFTER normalizing the direction.
             */
            direction = new Vec3(
                    direction.x,
                    direction.y + GRAPPLE_LIFT,
                    direction.z
            ).normalize();

            double burstStrength = Math.min(
                    1.35D + owner.distanceTo(target) * 0.04D,
                    2.1D
            );

            owner.setDeltaMovement(
                    owner.getDeltaMovement()
                            .add(
                                    direction.scale(
                                            burstStrength
                                    )
                            )
            );

            owner.hurtMarked = true;
        }
    }

    private Entity getHookedEntity() {
        String uuidString =
                entityData.get(DATA_HOOKED_ENTITY);

        if (uuidString == null || uuidString.isEmpty()) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            return level().getEntity(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // HOOKED ENTITY
    // -------------------------------------------------------------------------

    private void tickHooked(Player owner) {
        Entity target = getHookedEntity();

        if (
                target == null
                        || !target.isAlive()
                        || target == owner
        ) {
            beginReturning();
            return;
        }

        hookTicks++;

        /*
         * Keep the core attached to the target during
         * the short latch period.
         */
        Vec3 targetCenter =
                target.getBoundingBox().getCenter();

        setPos(
                targetCenter.x,
                targetCenter.y,
                targetCenter.z
        );

        setDeltaMovement(Vec3.ZERO);

        boolean reversePull =
                entityData.get(DATA_REVERSE_PULL);

        /*
         * Crouching mode continues pulling during
         * the latch period.
         */
        if (reversePull) {
            pullEntityTowardPlayer(
                    owner,
                    target
            );
        }

        /*
         * 3 ticks = 150 ms.
         *
         * The actual pull already happened on impact.
         * This is only the latch/release delay.
         */
        if (hookTicks >= 3) {
            beginReturning();
        }
    }

    // -------------------------------------------------------------------------
    // PULL ENTITY TO PLAYER
    // -------------------------------------------------------------------------

    private void pullEntityTowardPlayer(
            Player owner,
            Entity target
    ) {
        Vec3 targetPosition =
                target.getBoundingBox().getCenter();

        Vec3 ownerPosition =
                owner.getEyePosition();

        Vec3 direction =
                ownerPosition.subtract(targetPosition);

        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        direction = direction.normalize();

        /*
         * Give the hooked entity some upward movement too.
         */
        direction = new Vec3(
                direction.x,
                direction.y + ENTITY_PULL_LIFT,
                direction.z
        ).normalize();

        Vec3 pull =
                direction.scale(
                        ENTITY_PULL_STRENGTH
                );

        Vec3 velocity =
                target.getDeltaMovement()
                        .add(pull);

        double maxSpeed = 1.5D;

        if (velocity.length() > maxSpeed) {
            velocity =
                    velocity.normalize()
                            .scale(maxSpeed);
        }

        target.setDeltaMovement(velocity);

        target.hurtMarked = true;
    }

    // -------------------------------------------------------------------------
    // PULL PLAYER TO ENTITY
    // -------------------------------------------------------------------------

    private void pullPlayerTowardEntity(
            Player player,
            Entity target
    ) {
        Vec3 targetPosition =
                target.getBoundingBox()
                        .getCenter();

        Vec3 playerPosition =
                player.getEyePosition();

        Vec3 direction =
                targetPosition
                        .subtract(playerPosition);

        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        double distance =
                direction.length();

        double strength =
                Math.min(
                        GRAPPLE_STRENGTH
                                + distance * 0.025D,
                        0.55D
                );

        Vec3 pull =
                direction
                        .normalize()
                        .scale(strength);

        Vec3 velocity =
                player.getDeltaMovement()
                        .add(pull);

        if (velocity.length() > MAX_GRAPPLE_SPEED) {
            velocity =
                    velocity.normalize()
                            .scale(MAX_GRAPPLE_SPEED);
        }

        player.setDeltaMovement(velocity);

        player.hurtMarked = true;
    }

    // -------------------------------------------------------------------------
    // RETURN
    // -------------------------------------------------------------------------

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
                owner.getEyePosition()
                        .subtract(
                                0.0D,
                                0.15D,
                                0.0D
                        );

        Vec3 difference =
                target.subtract(position());

        double distance =
                difference.length();

        if (distance <= 0.7D) {
            /*
             * Returned to the player.
             *
             * Remove the projectile so another one can be thrown.
             */
            discard();
            return;
        }

        Vec3 velocity =
                difference
                        .normalize()
                        .scale(
                                Math.min(
                                        RETURN_SPEED
                                                + distance * 0.04D,
                                        2.4D
                                )
                        );

        setDeltaMovement(velocity);

        move(
                MoverType.SELF,
                velocity
        );

        hurtMarked = true;
    }

    // -------------------------------------------------------------------------
    // COLLISION FILTER
    // -------------------------------------------------------------------------

    private boolean canHitEntity(Entity entity) {
        if (entity == this) {
            return false;
        }

        if (entity == getOwner()) {
            return false;
        }

        if (!entity.isAlive()) {
            return false;
        }

        /*
         * Spectators shouldn't be hookable.
         */
        if (entity instanceof Player player && player.isSpectator()) {
            return false;
        }

        return entity.isPickable();
    }

    // -------------------------------------------------------------------------
    // RENDERER ACCESS
    // -------------------------------------------------------------------------

    /*
     * Used by HeavyCoreRenderer.
     *
     * partialTick lets the renderer animate between game ticks.
     */
    public float getSpinAngle(float partialTick) {
        if (!isCharging()) {
            return spinAngle;
        }

        float progress =
                getChargeProgress();

        float spinSpeed =
                12.0F
                        + (32.0F * progress);

        return spinAngle
                + spinSpeed * partialTick;
    }

    // -------------------------------------------------------------------------
    // SYNCHED DATA
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // SAVE / LOAD
    // -------------------------------------------------------------------------

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
                entityData.get(DATA_REVERSE_PULL)
        );
    }

    @Override
    protected void readAdditionalSaveData(
            ValueInput input
    ) {
        input.getString("Owner").ifPresent(owner -> {
            try {
                ownerUuid = UUID.fromString(owner);

                entityData.set(
                        DATA_OWNER,
                        owner
                );
            } catch (IllegalArgumentException ignored) {
                ownerUuid = null;

                entityData.set(
                        DATA_OWNER,
                        ""
                );
            }
        });

        int stateId =
                input.getInt("State")
                        .orElse(State.READY.ordinal());

        if (
                stateId >= 0
                        && stateId < State.values().length
        ) {
            setState(
                    State.values()[stateId]
            );
        } else {
            setState(State.READY);
        }

        boolean reversePull = input.read(
                "ReversePull",
                Codec.BOOL
        ).orElse(false);
    }

    // -------------------------------------------------------------------------
    // SIZE
    // -------------------------------------------------------------------------

    @Override
    public EntityDimensions getDimensions(
            Pose pose
    ) {
        return EntityDimensions.fixed(
                1.0F,
                1.0F
        );
    }

    public float getWobblePitch() {
        return wobblePitch;
    }

    public float getWobbleRoll() {
        return wobbleRoll;
    }

    private void updateWobble(Vec3 referenceAxis) {
        Vec3 movement =
                position()
                        .subtract(lastClientPosition);

        Vec3 acceleration =
                movement.subtract(lastClientVelocity);

        /*
         * The wobble reacts to sudden changes in movement,
         * rather than simply pointing at the direction of travel.
         *
         * This gives it a little "mass on a chain" feeling.
         */
        Vec3 axis = referenceAxis;

        if (axis.lengthSqr() < 1.0E-6D) {
            axis = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            axis = axis.normalize();
        }

        Vec3 worldUp = new Vec3(
                0.0D,
                1.0D,
                0.0D
        );

        Vec3 right =
                axis.cross(worldUp);

        /*
         * If the axis is nearly vertical, choose another basis.
         */
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(
                    1.0D,
                    0.0D,
                    0.0D
            );
        } else {
            right = right.normalize();
        }

        Vec3 up =
                right.cross(axis).normalize();

        /*
         * Convert acceleration into the flail's local axes.
         */
        double pitchTarget =
                acceleration.dot(up) * 260.0D;

        double rollTarget =
                acceleration.dot(right) * 260.0D;

        pitchTarget = Math.max(
                -18.0D,
                Math.min(
                        18.0D,
                        pitchTarget
                )
        );

        rollTarget = Math.max(
                -18.0D,
                Math.min(
                        18.0D,
                        rollTarget
                )
        );

        /*
         * Spring.
         */
        wobblePitchVelocity +=
                (float) (pitchTarget - wobblePitch)
                        * 0.18F;

        wobbleRollVelocity +=
                (float) (rollTarget - wobbleRoll)
                        * 0.18F;

        /*
         * Damping.
         */
        wobblePitchVelocity *= 0.80F;
        wobbleRollVelocity *= 0.80F;

        wobblePitch += wobblePitchVelocity;
        wobbleRoll += wobbleRollVelocity;

        /*
         * Prevent tiny numerical oscillations from lasting forever.
         */
        wobblePitch *= 0.985F;
        wobbleRoll *= 0.985F;

        lastClientPosition = position();
        lastClientVelocity = movement;
    }

    private void tickSwingDamage(Player owner) {
        /*
         * Count down existing hit cooldowns.
         */
        swingHitCooldowns.replaceAll(
                (uuid, cooldown) -> cooldown - 1
        );

        swingHitCooldowns.entrySet().removeIf(
                entry -> entry.getValue() <= 0
        );

        /*
         * Look for entities close to the core.
         *
         * The extra inflation gives the Heavy Core a little
         * "meat" to its hitbox so it doesn't need pixel-perfect
         * contact.
         */
        for (Entity target : level().getEntities(
                this,
                getBoundingBox().inflate(0.35D),
                this::canHitEntity
        )) {

            if (target == owner) {
                continue;
            }

            if (swingHitCooldowns.containsKey(target.getUUID())) {
                continue;
            }

            /*
             * Damage the target.
             */
            target.hurt(
                    owner.damageSources().playerAttack(owner),
                    SWING_DAMAGE
            );

            /*
             * Prevent the same target from being hit again
             * for a few ticks.
             */
            swingHitCooldowns.put(
                    target.getUUID(),
                    SWING_HIT_COOLDOWN
            );
        }
    }
    private Vec3 getHandPosition(Player player) {
        float yaw = player.getYRot();
        double yawRadians = Math.toRadians(yaw);

        /*
         * Player's horizontal forward direction.
         */
        Vec3 forward = new Vec3(
                -Math.sin(yawRadians),
                0.0D,
                Math.cos(yawRadians)
        );

        /*
         * Player's horizontal right direction.
         */
        Vec3 right = new Vec3(
                Math.cos(yawRadians),
                0.0D,
                Math.sin(yawRadians)
        );

        /*
         * Which arm is holding the flail.
         */
        boolean leftHand = isLeftHand();

        double sideOffset =
                leftHand
                        ? -0.32D
                        : 0.32D;

        /*
         * Slightly forward from the shoulder/torso.
         */
        double forwardOffset = 0.28D;

        /*
         * Approximate hand height.
         */
        double handHeight =
                player.isCrouching()
                        ? 0.95D
                        : 1.15D;

        return player.position()
                .add(
                        right.scale(sideOffset)
                )
                .add(
                        forward.scale(forwardOffset)
                )
                .add(
                        0.0D,
                        handHeight,
                        0.0D
                );
    }

    public boolean isLeftHand() {
        return entityData.get(DATA_LEFT_HAND);
    }

    public UUID getOwnerUuid() {
        if (ownerUuid != null) {
            return ownerUuid;
        }

        String value =
                entityData.get(DATA_OWNER);

        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
