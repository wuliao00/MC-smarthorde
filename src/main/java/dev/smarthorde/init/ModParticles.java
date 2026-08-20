package dev.smarthorde.init;

import dev.smarthorde.SmartHorde;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 粒子注册表。telegraph 范围提示粒子在轮3+ add。 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SmartHorde.MOD_ID);

    // 轮3示例（holder 泛型以 IDE 推断为准）：
    // PARTICLE_TYPES.register("telegraph_arc", () -> new SimpleParticleType(false));

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }

    private ModParticles() {}
}
