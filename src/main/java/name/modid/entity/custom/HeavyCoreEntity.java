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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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

public class HeavyCoreEntity extends Projectile {

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
            Math.abs(
                    ORBIT_START_SPEED
            )
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
     * THROW DAMAGE
     * -------------------------------------------------------------------------
     *
     * The projectile damage is multiplied by its actual impact speed and then
     * rounded upward, following the basic damage behavior of vanilla arrows.
     *
     * There is intentionally NO custom maximum damage.
     */

    private static final float THROW_DAMAGE = 3.0F;


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

    private static final double MAX_THROW_DISTANCE = 20.0D;


    /*
     * -------------------------------------------------------------------------
     * HOOKING
     * -------------------------------------------------------------------------
     */

    private static final int HOOK_LATCH_TICKS = 4;


    /*
     * -------------------------------------------------------------------------
     * DAMAGE
     * -------------------------------------------------------------------------
     */

    private static final float SWING_DAMAGE = 5.0F;

    private static final int SWING_HIT_COOLDOWN = 5;


    /*
     * -------------------------------------------------------------------------
     * SYMMETRICAL GRAPPLE
     * -------------------------------------------------------------------------
     *
     * Both grapple directions use the same directional pull and the same
     * upward lift.
     */

    private static final double GRAPPLE_BASE_STRENGTH = 0.40D;

    private static final double GRAPPLE_DISTANCE_SCALE = 0.04D;

    private static final double GRAPPLE_MAX_FORCE = 2.10D;

    private static final double GRAPPLE_MAX_SPEED = 1.50D;

    /*
     * Small shared upward acceleration.
     *
     * This is enough to help a grounded player break free of ground friction
     * without giving the grapple the much stronger launch from before.
     */

    private static final double GRAPPLE_VERTICAL_LIFT = 0.12D;

    /*
     * Maximum upward velocity created by grapple lift.
     */

    private static final double GRAPPLE_MAX_VERTICAL_SPEED = 1.20D;


    /*
     * -------------------------------------------------------------------------
     * SAGGING / TERRARIA-STYLE GROUNDED FLAIL
     * -------------------------------------------------------------------------
     */

    private static final double SAG_HORIZONTAL_DRAG = 0.82D;

    private static final double SAG_GRAVITY = 0.08D;

    private static final double SAG_MAX_FALL_SPEED = 1.00D;

    private static final double SAG_GROUND_DRAG = 0.55D;

    private static final float SAG_DAMAGE = THROW_DAMAGE;

    private static final int SAG_DAMAGE_COOLDOWN = 5;

    private static final double SAG_DAMAGE_RADIUS = 0.35D;


    /*
     * -------------------------------------------------------------------------
     * CHUNK LOADING / SIMULATION
     * -------------------------------------------------------------------------
     */

    private static final int GRAPPLE_CHUNK_TICKET_RADIUS = 3;


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


    /*
     * -------------------------------------------------------------------------
     * CLIENT VISUAL PREDICTION
     * -------------------------------------------------------------------------
     *
     * These values only affect rendering.
     *
     * The actual entity position remains server authoritative.
     */

    private Vec3 clientVisualPosition = Vec3.ZERO;

    private Vec3 clientVisualPreviousPosition = Vec3.ZERO;

    private Vec3 clientVisualVelocity = Vec3.ZERO;

    private boolean clientVisualInitialized = false;

    private State clientVisualState = State.READY;

    private int clientVisualChargeTicks = 0;

    private double clientVisualOrbitAngle = 0.0D;

    private float clientVisualSpinAngle = 0.0F;


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

    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS =
            SynchedEntityData.defineId(
                    HeavyCoreEntity.class,
                    EntityDataSerializers.INT
            );


    // =========================================================================
    // INTERNAL STATE
    // =========================================================================

    /*
     * Kept separately from Projectile's owner reference so the renderer can
     * always resolve the owner from synchronized entity data.
     */

    private UUID ownerUuid;

    private int flightTicks = 0;

    private int hookTicks = 0;

    private double orbitAngle = 0.0D;


    /*
     * -------------------------------------------------------------------------
     * CLIENT WOBBLE
     * -------------------------------------------------------------------------
     */

    private float wobblePitch = 0.0F;

    private float wobbleRoll = 0.0F;

    private float wobblePitchVelocity = 0.0F;

    private float wobbleRollVelocity = 0.0F;

    private Vec3 lastClientPosition = Vec3.ZERO;

    private Vec3 lastClientVelocity = Vec3.ZERO;

    private boolean clientMotionInitialized = false;


    /*
     * -------------------------------------------------------------------------
     * DAMAGE COOLDOWNS
     * -------------------------------------------------------------------------
     */

    private final Map<UUID, Integer> swingHitCooldowns =
            new HashMap<>();

    private final Map<UUID, Integer> sagHitCooldowns =
            new HashMap<>();


    /*
     * -------------------------------------------------------------------------
     * CHUNK TICKET
     * -------------------------------------------------------------------------
     */

    private ChunkPos chunkTicketPosition = null;


    // =========================================================================
    // STATE
    // =========================================================================

    /*
     * SAGGING is intentionally last so older saved ordinal values remain valid.
     */

    private enum State {

        READY,

        CHARGING,

        FLYING,

        HOOKED,

        RETURNING,

        SAGGING
    }


    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public HeavyCoreEntity(
            EntityType<? extends HeavyCoreEntity> type,
            Level level
    ) {

        super(
                type,
                level
        );


        setNoGravity(
                true
        );


        this.noPhysics =
                true;
    }


    // =========================================================================
    // STATE HELPERS
    // =========================================================================

    private State getState() {

        int value =
                entityData.get(
                        DATA_STATE
                );


        if (
                value < 0
                        || value >= State.values().length
        ) {

            return State.READY;
        }


        return State.values()[value];
    }


    private void setState(
            State state
    ) {

        entityData.set(
                DATA_STATE,
                state.ordinal()
        );
    }


    public boolean isReady() {

        return getState()
                == State.READY;
    }


    public boolean isCharging() {

        return getState()
                == State.CHARGING;
    }


    public boolean isFlying() {

        return getState()
                == State.FLYING;
    }


    public boolean isSagging() {

        return getState()
                == State.SAGGING;
    }


    // =========================================================================
    // OWNER
    // =========================================================================

    public void setOwner(
            Player player,
            InteractionHand hand
    ) {

        /*
         * Use Projectile's owner system as well as the synchronized UUID
         * used by the renderer.
         */

        super.setOwner(
                player
        );


        ownerUuid =
                player.getUUID();


        entityData.set(
                DATA_OWNER,
                player.getUUID().toString()
        );


        boolean leftHand;


        if (
                hand == InteractionHand.OFF_HAND
        ) {

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


    /*
     * Returns the owner as a Player.
     *
     * The real Projectile owner is preferred. The synchronized UUID is used
     * as a fallback.
     */

    private Player getOwnerPlayer() {

        Entity projectileOwner =
                getOwner();


        if (
                projectileOwner
                        instanceof Player player
        ) {

            return player;
        }


        if (
                ownerUuid != null
        ) {

            Entity entity =
                    level().getEntity(
                            ownerUuid
                    );


            if (
                    entity
                            instanceof Player player
            ) {

                return player;
            }


            Player player =
                    level().getPlayerByUUID(
                            ownerUuid
                    );


            if (
                    player != null
            ) {

                return player;
            }
        }


        String uuidString =
                entityData.get(
                        DATA_OWNER
                );


        if (
                uuidString == null
                        || uuidString.isEmpty()
        ) {

            return null;
        }


        try {

            UUID uuid =
                    UUID.fromString(
                            uuidString
                    );


            ownerUuid =
                    uuid;


            return level()
                    .getPlayerByUUID(
                            uuid
                    );

        } catch (
                IllegalArgumentException ignored
        ) {

            return null;
        }
    }


    public UUID getOwnerUuid() {

        Entity projectileOwner =
                getOwner();


        if (
                projectileOwner != null
        ) {

            return projectileOwner.getUUID();
        }


        if (
                ownerUuid != null
        ) {

            return ownerUuid;
        }


        String value =
                entityData.get(
                        DATA_OWNER
                );


        if (
                value == null
                        || value.isEmpty()
        ) {

            return null;
        }


        try {

            ownerUuid =
                    UUID.fromString(
                            value
                    );


            return ownerUuid;

        } catch (
                IllegalArgumentException ignored
        ) {

            return null;
        }
    }


    // =========================================================================
    // CHARGING
    // =========================================================================

    public void startCharging() {

        setState(
                State.CHARGING
        );


        entityData.set(
                DATA_CHARGE_TICKS,
                0
        );


        orbitAngle =
                0.0D;


        flightTicks =
                0;


        hookTicks =
                0;


        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );


        setNoGravity(
                true
        );


        this.noPhysics =
                true;


        setDeltaMovement(
                Vec3.ZERO
        );
    }


    public int getChargeTicks() {

        return entityData.get(
                DATA_CHARGE_TICKS
        );
    }


    public float getChargeProgress() {

        return Math.min(
                1.0F,
                getChargeTicks()
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


        return Math.abs(
                orbitSpeed
        )
                * radius;
    }


    public double getThrowSpeed() {

        double swingSpeed =
                getSwingSpeed();


        double swingRange =
                THROW_FULL_SWING_SPEED
                        - THROW_MIN_SWING_SPEED;


        if (
                swingRange
                        <= 1.0E-6D
        ) {

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

    private void tickCharging(
            Player owner
    ) {

        int chargeTicks =
                Math.min(
                        getChargeTicks() + 1,
                        CHARGE_DURATION_TICKS
                );


        entityData.set(
                DATA_CHARGE_TICKS,
                chargeTicks
        );


        float progress =
                getChargeProgress();


        double orbitSpeed =
                ORBIT_START_SPEED
                        + (
                        ORBIT_SPEED_GROWTH
                                * progress
                );


        orbitAngle +=
                orbitSpeed;


        orbitAngle %=
                Math.PI * 2.0D;


        if (
                orbitAngle
                        < 0.0D
        ) {

            orbitAngle +=
                    Math.PI * 2.0D;
        }


        Vec3 forward =
                owner.getLookAngle();


        if (
                forward.lengthSqr()
                        < 1.0E-6D
        ) {

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
                Math.toRadians(
                        yaw
                );


        Vec3 right =
                new Vec3(
                        Math.cos(yawRadians),
                        0.0D,
                        Math.sin(yawRadians)
                ).normalize();


        Vec3 up =
                forward.cross(
                        right
                );


        if (
                up.lengthSqr()
                        < 1.0E-6D
        ) {

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
                                Math.cos(
                                        orbitAngle
                                )
                                        * radius
                        )
                        .add(
                                up.scale(
                                        Math.sin(
                                                orbitAngle
                                        )
                                                * radius
                                )
                        );


        Vec3 handPosition =
                getHandPosition(
                        owner
                );


        Vec3 projectileCenter =
                handPosition
                        .add(
                                orbitOffset
                        );


        setProjectileCenter(
                projectileCenter
        );


        setDeltaMovement(
                Vec3.ZERO
        );
    }


    // =========================================================================
    // PROJECTILE CENTER / HITBOX ALIGNMENT
    // =========================================================================

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

        if (
                !isCharging()
        ) {

            return;
        }


        entityData.set(
                DATA_REVERSE_PULL,
                reversePull
        );


        setState(
                State.FLYING
        );


        flightTicks =
                0;


        hookTicks =
                0;


        double speed =
                getThrowSpeed();


        Vec3 launchDirection;


        if (
                direction.lengthSqr()
                        < 1.0E-6D
        ) {

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
                        .scale(
                                speed
                        )
                        .add(
                                0.0D,
                                THROW_UPWARD_VELOCITY,
                                0.0D
                        );


        setNoGravity(
                true
        );


        this.noPhysics =
                true;


        setDeltaMovement(
                velocity
        );


        hurtMarked =
                true;
    }


    // =========================================================================
    // SAGGING MODE
    // =========================================================================

    public void beginSagging() {

        if (
                !isFlying()
        ) {

            return;
        }


        setState(
                State.SAGGING
        );


        setNoGravity(
                true
        );


        this.noPhysics =
                false;


        Vec3 velocity =
                getDeltaMovement();


        setDeltaMovement(
                new Vec3(
                        velocity.x * 0.20D,
                        Math.min(
                                velocity.y,
                                0.0D
                        ),
                        velocity.z * 0.20D
                )
        );


        sagHitCooldowns.clear();


        hurtMarked =
                true;
    }


    public void releaseSagging() {

        if (
                !isSagging()
        ) {

            return;
        }


        beginReturning();
    }


    // =========================================================================
    // MAIN TICK
    // =========================================================================

    @Override
    public void tick() {

        super.tick();


        Player owner =
                getOwnerPlayer();


        if (
                owner == null
                        || !owner.isAlive()
        ) {

            releaseChunkTicket();

            discard();

            return;
        }


        if (
                level().isClientSide()
        ) {

            tickClient(
                    owner
            );

            return;
        }


        ServerLevel serverLevel =
                (ServerLevel) level();


        updateChunkTicket(
                serverLevel
        );


        tickServer(
                owner
        );


        if (
                isRemoved()
        ) {

            releaseChunkTicket();

        } else {

            updateChunkTicket(
                    serverLevel
            );
        }
    }


    // =========================================================================
    // REMOVAL
    // =========================================================================

    @Override
    public void remove(
            RemovalReason reason
    ) {

        releaseChunkTicket();

        super.remove(
                reason
        );
    }


    // =========================================================================
    // CHUNK TICKET
    // =========================================================================

    private void updateChunkTicket(
            ServerLevel serverLevel
    ) {

        if (
                isRemoved()
        ) {

            return;
        }


        BlockPos pos =
                blockPosition();


        ChunkPos newChunk =
                new ChunkPos(
                        pos.getX() >> 4,
                        pos.getZ() >> 4
                );


        if (
                chunkTicketPosition != null
                        && chunkTicketPosition.equals(
                        newChunk
                )
        ) {

            return;
        }


        serverLevel.getChunkSource()
                .addTicketWithRadius(
                        TicketType.FORCED,
                        newChunk,
                        GRAPPLE_CHUNK_TICKET_RADIUS
                );


        ChunkPos oldChunk =
                chunkTicketPosition;


        chunkTicketPosition =
                newChunk;


        if (
                oldChunk != null
        ) {

            serverLevel.getChunkSource()
                    .removeTicketWithRadius(
                            TicketType.FORCED,
                            oldChunk,
                            GRAPPLE_CHUNK_TICKET_RADIUS
                    );
        }
    }


    private void releaseChunkTicket() {

        if (
                chunkTicketPosition == null
        ) {

            return;
        }


        if (
                level()
                        instanceof ServerLevel serverLevel
        ) {

            serverLevel.getChunkSource()
                    .removeTicketWithRadius(
                            TicketType.FORCED,
                            chunkTicketPosition,
                            GRAPPLE_CHUNK_TICKET_RADIUS
                    );
        }


        chunkTicketPosition =
                null;
    }


    // =========================================================================
    // DAMAGE
    // =========================================================================

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float damage
    ) {

        return false;
    }


    /*
     * Vanilla-arrow-style velocity damage.
     *
     * There is no custom damage ceiling.
     *
     * Example with THROW_DAMAGE = 6:
     *
     *   speed 1.0 -> 6 damage
     *   speed 1.5 -> 9 damage
     *   speed 2.2 -> 14 damage
     *   speed 3.0 -> 18 damage
     */

    private float getImpactDamage(
            double impactSpeed
    ) {

        return (float) Math.ceil(
                Math.max(
                        0.0D,
                        impactSpeed
                                * THROW_DAMAGE
                )
        );
    }


    // =========================================================================
    // CLIENT TICK
    // =========================================================================

    private void tickClient(
            Player owner
    ) {

        State state =
                getState();


        if (
                state
                        != clientVisualState
        ) {

            clientVisualState =
                    state;


            clientVisualInitialized =
                    false;


            if (
                    state
                            == State.CHARGING
            ) {

                clientVisualChargeTicks =
                        getChargeTicks();

                clientVisualOrbitAngle =
                        0.0D;

                clientVisualSpinAngle =
                        0.0F;
            }
        }


        if (
                !clientVisualInitialized
        ) {

            clientVisualPosition =
                    position();

            clientVisualPreviousPosition =
                    clientVisualPosition;

            clientVisualVelocity =
                    getDeltaMovement();

            clientVisualInitialized =
                    true;
        }


        clientVisualPreviousPosition =
                clientVisualPosition;


        switch (state) {

            case CHARGING ->

                    tickClientCharging(
                            owner
                    );


            case FLYING ->

                    tickClientFlightVisual();


            case HOOKED ->

                    tickClientHooked();


            case SAGGING ->

                    tickClientSaggingVisual();


            case RETURNING ->

                    tickClientReturningVisual(
                            owner
                    );


            case READY -> {

                clientVisualPosition =
                        position();

                clientVisualVelocity =
                        Vec3.ZERO;
            }
        }


        updateClientVisualCorrection();


        Vec3 referenceAxis;


        if (
                state == State.CHARGING
        ) {

            referenceAxis =
                    owner.getLookAngle();

        } else {

            referenceAxis =
                    clientVisualPosition
                            .subtract(
                                    clientVisualPreviousPosition
                            );


            if (
                    referenceAxis.lengthSqr()
                            < 1.0E-6D
            ) {

                referenceAxis =
                        clientVisualVelocity;


                if (
                        referenceAxis.lengthSqr()
                                < 1.0E-6D
                ) {

                    referenceAxis =
                            owner.getLookAngle();
                }
            }
        }


        updateWobble(
                referenceAxis
        );
    }


    // =========================================================================
    // CLIENT CHARGING
    // =========================================================================

    private void tickClientCharging(
            Player owner
    ) {

        clientVisualChargeTicks =
                Math.min(
                        CHARGE_DURATION_TICKS,
                        Math.max(
                                clientVisualChargeTicks + 1,
                                getChargeTicks()
                        )
                );


        float progress =
                Math.min(
                        1.0F,
                        clientVisualChargeTicks
                                / (float) CHARGE_DURATION_TICKS
                );


        double orbitSpeed =
                ORBIT_START_SPEED
                        + (
                        ORBIT_SPEED_GROWTH
                                * progress
                );


        clientVisualOrbitAngle +=
                orbitSpeed;


        clientVisualOrbitAngle %=
                Math.PI * 2.0D;


        if (
                clientVisualOrbitAngle
                        < 0.0D
        ) {

            clientVisualOrbitAngle +=
                    Math.PI * 2.0D;
        }


        Vec3 forward =
                owner.getLookAngle();


        if (
                forward.lengthSqr()
                        < 1.0E-6D
        ) {

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
                Math.toRadians(
                        yaw
                );


        Vec3 right =
                new Vec3(
                        Math.cos(yawRadians),
                        0.0D,
                        Math.sin(yawRadians)
                ).normalize();


        Vec3 up =
                forward.cross(
                        right
                );


        if (
                up.lengthSqr()
                        < 1.0E-6D
        ) {

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
                                Math.cos(
                                        clientVisualOrbitAngle
                                )
                                        * radius
                        )
                        .add(
                                up.scale(
                                        Math.sin(
                                                clientVisualOrbitAngle
                                        )
                                                * radius
                                )
                        );


        Vec3 handPosition =
                getHandPosition(
                        owner
                );


        Vec3 visualCenter =
                handPosition
                        .add(
                                orbitOffset
                        );


        clientVisualPosition =
                new Vec3(
                        visualCenter.x,
                        visualCenter.y
                                - getDimensions(
                                Pose.STANDING
                        ).height()
                                * 0.5D,
                        visualCenter.z
                );


        clientVisualVelocity =
                Vec3.ZERO;


        float spinSpeed =
                VISUAL_SPIN_START
                        + (
                        VISUAL_SPIN_GROWTH
                                * progress
                );


        clientVisualSpinAngle +=
                spinSpeed;


        while (
                clientVisualSpinAngle
                        >= 360.0F
        ) {

            clientVisualSpinAngle -=
                    360.0F;
        }
    }


    // =========================================================================
    // CLIENT FLIGHT VISUAL
    // =========================================================================

    private void tickClientFlightVisual() {

        Vec3 velocity =
                getDeltaMovement();


        if (
                velocity.lengthSqr()
                        < 1.0E-8D
        ) {

            clientVisualVelocity =
                    Vec3.ZERO;

            clientVisualPosition =
                    position();

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


        clientVisualVelocity =
                velocity;


        clientVisualPosition =
                clientVisualPosition.add(
                        velocity
                );
    }


    // =========================================================================
    // CLIENT HOOKED VISUAL
    // =========================================================================

    private void tickClientHooked() {

        Entity target =
                getHookedEntity();


        if (
                target == null
                        || !target.isAlive()
        ) {

            clientVisualPosition =
                    position();

            clientVisualVelocity =
                    Vec3.ZERO;

            return;
        }


        Vec3 targetCenter =
                target.getBoundingBox()
                        .getCenter();


        clientVisualPosition =
                new Vec3(
                        targetCenter.x,
                        targetCenter.y
                                - getDimensions(
                                Pose.STANDING
                        ).height()
                                * 0.5D,
                        targetCenter.z
                );


        clientVisualVelocity =
                Vec3.ZERO;
    }


    // =========================================================================
    // CLIENT SAGGING VISUAL
    // =========================================================================

    private void tickClientSaggingVisual() {

        Vec3 velocity =
                clientVisualVelocity;


        Vec3 authoritativeVelocity =
                getDeltaMovement();


        if (
                authoritativeVelocity.lengthSqr()
                        > 1.0E-8D
        ) {

            velocity =
                    new Vec3(
                            authoritativeVelocity.x,
                            authoritativeVelocity.y,
                            authoritativeVelocity.z
                    );
        }


        double motionX =
                velocity.x
                        * SAG_HORIZONTAL_DRAG;


        double motionZ =
                velocity.z
                        * SAG_HORIZONTAL_DRAG;


        double motionY =
                velocity.y
                        - SAG_GRAVITY;


        motionY =
                Math.max(
                        motionY,
                        -SAG_MAX_FALL_SPEED
                );


        velocity =
                new Vec3(
                        motionX,
                        motionY,
                        motionZ
                );


        clientVisualVelocity =
                velocity;


        clientVisualPosition =
                clientVisualPosition.add(
                        velocity
                );


        if (
                onGround()
        ) {

            clientVisualVelocity =
                    new Vec3(
                            velocity.x
                                    * SAG_GROUND_DRAG,
                            0.0D,
                            velocity.z
                                    * SAG_GROUND_DRAG
                    );
        }
    }


    // =========================================================================
    // CLIENT RETURN VISUAL
    // =========================================================================

    private void tickClientReturningVisual(
            Player owner
    ) {

        Vec3 target =
                owner.getBoundingBox()
                        .getCenter();


        Vec3 targetPosition =
                new Vec3(
                        target.x,
                        target.y
                                - getDimensions(
                                Pose.STANDING
                        ).height()
                                * 0.5D,
                        target.z
                );


        Vec3 difference =
                targetPosition.subtract(
                        clientVisualPosition
                );


        double distance =
                difference.length();


        if (
                distance
                        < 1.0E-6D
        ) {

            clientVisualVelocity =
                    Vec3.ZERO;

            clientVisualPosition =
                    targetPosition;

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


        clientVisualVelocity =
                movement;


        clientVisualPosition =
                clientVisualPosition.add(
                        movement
                );
    }


    // =========================================================================
    // CLIENT VISUAL CORRECTION
    // =========================================================================

    private void updateClientVisualCorrection() {

        State state =
                getState();


        if (
                state == State.CHARGING
                        || state == State.HOOKED
                        || state == State.READY
        ) {

            return;
        }


        Vec3 authoritative =
                position();


        Vec3 difference =
                authoritative.subtract(
                        clientVisualPosition
                );


        double distance =
                difference.length();


        if (
                distance
                        < 0.05D
        ) {

            return;
        }


        double correctionStrength;


        if (
                distance > 2.0D
        ) {

            correctionStrength =
                    0.80D;

        } else if (
                distance > 0.75D
        ) {

            correctionStrength =
                    0.45D;

        } else {

            correctionStrength =
                    0.18D;
        }


        clientVisualPosition =
                clientVisualPosition.lerp(
                        authoritative,
                        correctionStrength
                );
    }


    // =========================================================================
    // RENDER POSITION
    // =========================================================================

    public Vec3 getRenderPosition(
            float partialTick
    ) {

        if (
                !level().isClientSide()
                        || !clientVisualInitialized
        ) {

            return position();
        }


        return clientVisualPreviousPosition.lerp(
                clientVisualPosition,
                partialTick
        );
    }


    // =========================================================================
    // SERVER TICK
    // =========================================================================

    private void tickServer(
            Player owner
    ) {

        State state =
                getState();


        switch (state) {

            case CHARGING -> {

                tickCharging(
                        owner
                );


                tickSwingDamage(
                        owner
                );
            }


            case FLYING ->

                    tickFlying(
                            owner
                    );


            case HOOKED ->

                    tickHooked(
                            owner
                    );


            case RETURNING ->

                    tickReturning(
                            owner
                    );


            case SAGGING ->

                    tickSagging(
                            owner
                    );


            case READY -> {
                // Nothing.
            }
        }
    }


    // =========================================================================
    // FLYING
    // =========================================================================

    private void tickFlying(
            Player owner
    ) {

        flightTicks++;


        Vec3 velocity =
                getDeltaMovement();


        if (
                velocity.lengthSqr()
                        < 1.0E-8D
        ) {

            beginReturning();

            return;
        }


        HitResult hit =
                ProjectileUtil
                        .getHitResultOnMoveVector(
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


        hurtMarked =
                true;
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


        if (
                !canHitEntity(
                        target
                )
        ) {

            beginReturning();

            return;
        }


        /*
         * Capture the projectile's actual impact speed BEFORE we stop it for
         * the hooked state.
         */

        double impactSpeed =
                getDeltaMovement()
                        .length();


        float impactDamage =
                getImpactDamage(
                        impactSpeed
                );


        entityData.set(
                DATA_HOOKED_ENTITY,
                target.getUUID()
                        .toString()
        );


        hookTicks =
                0;


        setState(
                State.HOOKED
        );


        setDeltaMovement(
                Vec3.ZERO
        );


        boolean reversePull =
                entityData.get(
                        DATA_REVERSE_PULL
                );


        if (
                target
                        instanceof LivingEntity livingTarget
        ) {

            DamageSource projectileDamage =
                    damageSources()
                            .source(
                                    DamageTypes.THROWN,
                                    this,
                                    owner
                            );


            livingTarget.hurt(
                    projectileDamage,
                    impactDamage
            );
        }


        if (
                reversePull
        ) {

            pullToward(
                    target,
                    owner
            );
        }
    }


    // =========================================================================
    // HOOKED
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
                    UUID.fromString(
                            uuidString
                    );


            return level().getEntity(
                    uuid
            );

        } catch (
                IllegalArgumentException ignored
        ) {

            return null;
        }
    }


    private void tickHooked(
            Player owner
    ) {

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


        setDeltaMovement(
                Vec3.ZERO
        );


        boolean reversePull =
                entityData.get(
                        DATA_REVERSE_PULL
                );


        if (
                reversePull
        ) {

            /*
             * Pull the entity toward the player.
             */

            pullToward(
                    target,
                    owner
            );

        } else {

            /*
             * Pull the player toward the entity.
             */

            pullToward(
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
    // SYMMETRICAL PULL
    // =========================================================================

    /*
     * mover  = entity being pulled
     * target = entity being pulled toward
     *
     * Both directions use exactly the same directional force and lift.
     */

    private void pullToward(
            Entity mover,
            Entity target
    ) {

        Vec3 moverPosition =
                mover.getBoundingBox()
                        .getCenter();


        Vec3 targetPosition =
                target.getBoundingBox()
                        .getCenter();


        Vec3 difference =
                targetPosition.subtract(
                        moverPosition
                );


        double distance =
                difference.length();


        if (
                distance
                        < 1.0E-6D
        ) {

            return;
        }


        Vec3 direction =
                difference.normalize();


        /*
         * Same distance-based directional grapple force.
         */

        double pullStrength =
                GRAPPLE_BASE_STRENGTH
                        + (
                        distance
                                * GRAPPLE_DISTANCE_SCALE
                );


        pullStrength =
                Math.min(
                        pullStrength,
                        GRAPPLE_MAX_FORCE
                );


        Vec3 pull =
                direction.scale(
                        pullStrength
                );


        Vec3 velocity =
                mover.getDeltaMovement()
                        .add(
                                pull
                        );


        /*
         * Cap horizontal grapple velocity independently from the vertical
         * lift.
         */

        double horizontalSpeed =
                Math.sqrt(
                        (
                                velocity.x
                                        * velocity.x
                        )
                                + (
                                velocity.z
                                        * velocity.z
                        )
                );


        if (
                horizontalSpeed
                        > GRAPPLE_MAX_SPEED
        ) {

            double scale =
                    GRAPPLE_MAX_SPEED
                            / horizontalSpeed;


            velocity =
                    new Vec3(
                            velocity.x
                                    * scale,
                            velocity.y,
                            velocity.z
                                    * scale
                    );
        }


        /*
         * Same upward acceleration in both directions.
         */

        double verticalVelocity =
                velocity.y
                        + GRAPPLE_VERTICAL_LIFT;


        verticalVelocity =
                Math.min(
                        verticalVelocity,
                        GRAPPLE_MAX_VERTICAL_SPEED
                );


        /*
         * Make the lift strong enough to actually break ground contact.
         */

        if (
                mover.onGround()
                        && verticalVelocity
                        < GRAPPLE_VERTICAL_LIFT
        ) {

            verticalVelocity =
                    GRAPPLE_VERTICAL_LIFT;
        }


        velocity =
                new Vec3(
                        velocity.x,
                        verticalVelocity,
                        velocity.z
                );


        mover.setDeltaMovement(
                velocity
        );


        mover.hurtMarked =
                true;
    }


    // =========================================================================
    // SAGGING / GROUNDED FLAIL
    // =========================================================================

    private void tickSagging(
            Player owner
    ) {

        this.noPhysics =
                false;


        Vec3 velocity =
                getDeltaMovement();


        double motionX =
                velocity.x
                        * SAG_HORIZONTAL_DRAG;


        double motionZ =
                velocity.z
                        * SAG_HORIZONTAL_DRAG;


        double motionY =
                velocity.y
                        - SAG_GRAVITY;


        motionY =
                Math.max(
                        motionY,
                        -SAG_MAX_FALL_SPEED
                );


        velocity =
                new Vec3(
                        motionX,
                        motionY,
                        motionZ
                );


        move(
                MoverType.SELF,
                velocity
        );


        if (
                onGround()
        ) {

            Vec3 groundVelocity =
                    getDeltaMovement();


            double groundX =
                    groundVelocity.x
                            * SAG_GROUND_DRAG;


            double groundZ =
                    groundVelocity.z
                            * SAG_GROUND_DRAG;


            if (
                    Math.abs(groundX)
                            < 0.01D
                            && Math.abs(groundZ)
                            < 0.01D
            ) {

                groundX =
                        0.0D;

                groundZ =
                        0.0D;
            }


            setDeltaMovement(
                    new Vec3(
                            groundX,
                            0.0D,
                            groundZ
                    )
            );

        } else {

            setDeltaMovement(
                    velocity
            );
        }


        tickSagDamage(
                owner
        );


        hurtMarked =
                true;
    }


    private void tickSagDamage(
            Player owner
    ) {

        sagHitCooldowns.replaceAll(
                (
                        uuid,
                        cooldown
                ) ->
                        cooldown - 1
        );


        sagHitCooldowns.entrySet()
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
                                .inflate(
                                        SAG_DAMAGE_RADIUS,
                                        0.30D,
                                        SAG_DAMAGE_RADIUS
                                ),
                        entity ->
                                entity instanceof LivingEntity
                                        && canHitEntity(
                                        entity
                                )
                )
        ) {

            if (
                    target == owner
            ) {

                continue;
            }


            if (
                    sagHitCooldowns.containsKey(
                            target.getUUID()
                    )
            ) {

                continue;
            }


            target.hurt(
                    damageSources()
                            .source(
                                    DamageTypes.THROWN,
                                    this,
                                    owner
                            ),
                    SAG_DAMAGE
            );


            sagHitCooldowns.put(
                    target.getUUID(),
                    SAG_DAMAGE_COOLDOWN
            );
        }
    }


    // =========================================================================
    // RETURN
    // =========================================================================

    public void cancelUse() {

        if (
                !isCharging()
                        && !isSagging()
        ) {

            return;
        }


        setState(
                State.READY
        );


        entityData.set(
                DATA_CHARGE_TICKS,
                0
        );


        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );


        orbitAngle =
                0.0D;


        flightTicks =
                0;


        hookTicks =
                0;


        setNoGravity(
                true
        );


        this.noPhysics =
                true;


        setDeltaMovement(
                Vec3.ZERO
        );
    }


    public void beginReturning() {

        setState(
                State.RETURNING
        );


        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );


        setNoGravity(
                true
        );


        this.noPhysics =
                false;


        setDeltaMovement(
                Vec3.ZERO
        );
    }


    private void tickReturning(
            Player owner
    ) {

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


        if (
                distance
                        < 1.0E-6D
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


        if (
                step >= distance
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


        setDeltaMovement(
                movement
        );


        move(
                MoverType.SELF,
                movement
        );


        hurtMarked =
                true;
    }


    // =========================================================================
    // COLLISION FILTER
    // =========================================================================

    @Override
    protected boolean canHitEntity(
            Entity entity
    ) {

        if (
                entity == this
        ) {

            return false;
        }


        if (
                !entity.isAlive()
        ) {

            return false;
        }


        if (
                entity instanceof Player player
                        && player.isSpectator()
        ) {

            return false;
        }


        /*
         * Let Projectile retain its normal owner handling.
         */

        if (
                !super.canHitEntity(
                        entity
                )
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
                (
                        uuid,
                        cooldown
                ) ->
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
                                .inflate(
                                        0.35D
                                ),
                        this::canHitEntity
                )
        ) {

            if (
                    target == owner
            ) {

                continue;
            }


            if (
                    swingHitCooldowns.containsKey(
                            target.getUUID()
                    )
            ) {

                continue;
            }


            target.hurt(
                    damageSources()
                            .source(
                                    DamageTypes.THROWN,
                                    this,
                                    owner
                            ),
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

        if (
                !isCharging()
        ) {

            return 0.0F;
        }


        float progress =
                Math.min(
                        1.0F,
                        clientVisualChargeTicks
                                / (float) CHARGE_DURATION_TICKS
                );


        float spinSpeed =
                VISUAL_SPIN_START
                        + (
                        VISUAL_SPIN_GROWTH
                                * progress
                );


        return clientVisualSpinAngle
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

        Vec3 currentPosition =
                clientVisualPosition;


        if (
                !clientMotionInitialized
        ) {

            lastClientPosition =
                    currentPosition;

            lastClientVelocity =
                    Vec3.ZERO;

            clientMotionInitialized =
                    true;

            return;
        }


        Vec3 movement =
                currentPosition.subtract(
                        lastClientPosition
                );


        Vec3 acceleration =
                movement.subtract(
                        lastClientVelocity
                );


        Vec3 axis =
                referenceAxis;


        if (
                axis.lengthSqr()
                        < 1.0E-6D
        ) {

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
                axis.cross(
                        worldUp
                );


        if (
                right.lengthSqr()
                        < 1.0E-6D
        ) {

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
                right.cross(
                        axis
                );


        if (
                up.lengthSqr()
                        < 1.0E-6D
        ) {

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
                acceleration.dot(
                        up
                )
                        * WOBBLE_ACCELERATION_SCALE;


        double rollTarget =
                acceleration.dot(
                        right
                )
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
                )
                        * WOBBLE_SPRING;


        wobbleRollVelocity +=
                (
                        (float) rollTarget
                                - wobbleRoll
                )
                        * WOBBLE_SPRING;


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
                currentPosition;


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
                Math.toRadians(
                        yaw
                );


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


        builder.define(
                DATA_CHARGE_TICKS,
                0
        );
    }


    // =========================================================================
    // SAVE / LOAD
    // =========================================================================

    @Override
    protected void addAdditionalSaveData(
            ValueOutput output
    ) {

        /*
         * Save Projectile's own owner state.
         */

        super.addAdditionalSaveData(
                output
        );


        /*
         * Keep our synchronized owner UUID as well.
         */

        if (
                ownerUuid != null
        ) {

            output.putString(
                    "Owner",
                    ownerUuid.toString()
            );
        }


        output.putInt(
                "State",
                getState().ordinal()
        );


        output.putInt(
                "ChargeTicks",
                getChargeTicks()
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

        /*
         * Restore Projectile's own saved data first.
         */

        super.readAdditionalSaveData(
                input
        );


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


                                Entity ownerEntity =
                                        level().getEntity(
                                                ownerUuid
                                        );


                                if (
                                        ownerEntity != null
                                ) {

                                    super.setOwner(
                                            ownerEntity
                                    );
                                }

                            } catch (
                                    IllegalArgumentException ignored
                            ) {

                                ownerUuid =
                                        null;


                                entityData.set(
                                        DATA_OWNER,
                                        ""
                                );
                            }
                        }
                );


        int stateId =
                input.getInt(
                                "State"
                        )
                        .orElse(
                                State.READY.ordinal()
                        );


        if (
                stateId >= 0
                        && stateId < State.values().length
        ) {

            setState(
                    State.values()[stateId]
            );

        } else {

            setState(
                    State.READY
            );
        }


        int chargeTicks =
                input.getInt(
                                "ChargeTicks"
                        )
                        .orElse(0);


        chargeTicks =
                Math.max(
                        0,
                        Math.min(
                                CHARGE_DURATION_TICKS,
                                chargeTicks
                        )
                );


        entityData.set(
                DATA_CHARGE_TICKS,
                chargeTicks
        );


        boolean reversePull =
                input.read(
                                "ReversePull",
                                Codec.BOOL
                        )
                        .orElse(false);


        entityData.set(
                DATA_REVERSE_PULL,
                reversePull
        );


        boolean leftHand =
                input.read(
                                "LeftHand",
                                Codec.BOOL
                        )
                        .orElse(false);


        entityData.set(
                DATA_LEFT_HAND,
                leftHand
        );


        entityData.set(
                DATA_HOOKED_ENTITY,
                ""
        );


        input.getString(
                        "HookedEntity"
                )
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