package name.modid.particle;

import name.modid.MitraSCombatAdditions;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class ModParticles {
    public static final SimpleParticleType FLAIL_PARTICLE =
            registerParticle("flail_particle", FabricParticleTypes.simple());


    private static SimpleParticleType registerParticle(String name, SimpleParticleType particleType) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MitraSCombatAdditions.MOD_ID, name), particleType);
    }

    public static void registerParticles() {
        MitraSCombatAdditions.LOGGER.info("Registering Particles for " + MitraSCombatAdditions.MOD_ID);
    }
}
