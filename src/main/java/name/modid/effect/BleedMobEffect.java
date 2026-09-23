package name.modid.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class BleedMobEffect extends MobEffect {

    public static final int DAMAGE_INTERVAL = 40;

    protected BleedMobEffect(
            final MobEffectCategory category,
            final int color
    ) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(
            final ServerLevel level,
            final LivingEntity mob,
            final int amplification
    ) {
        mob.invulnerableTime = 0;

        mob.hurtServer(
                level,
                mob.damageSources().wither(),
                0.1F
        );

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            final int tickCount,
            final int amplification
    ) {
        int interval = DAMAGE_INTERVAL >> amplification;

        if (interval <= 0) {
            interval = 1;
        }

        return tickCount % interval == 0;
    }
}