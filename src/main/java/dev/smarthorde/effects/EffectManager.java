package dev.smarthorde.effects;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 粒子与音效统一出口：全部受 {@code effects.particlesEnabled} / {@code effects.soundsEnabled}
 * 与 {@code performance.maxParticles} 约束，且均通过安全封装读取以防配置尚未加载时抛异常。
 */
public final class EffectManager {

    private EffectManager() {
    }

    /** 尸潮来袭：火焰粒子脉冲 + 号角音效。 */
    public static void spawnHordeStartEffect(ServerLevel level, Vec3 pos) {
        if (SmartHordeConfig.isParticlesEnabledSafe()) {
            int count = Math.min(32, SmartHordeConfig.maxParticlesSafe());
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, count, 1.5, 0.8, 1.5, 0.04);
        }
        if (SmartHordeConfig.isSoundsEnabledSafe()) {
            level.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.HORDE_WAVE_START.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        }
    }

    /** 波次清除：村民开心粒子 + 丧钟余韵。 */
    public static void spawnWaveClearEffect(ServerLevel level, Vec3 pos) {
        if (SmartHordeConfig.isParticlesEnabledSafe()) {
            int count = Math.min(24, SmartHordeConfig.maxParticlesSafe());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1.2, pos.z, count, 1.2, 0.8, 1.2, 0.05);
        }
        if (SmartHordeConfig.isSoundsEnabledSafe()) {
            level.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.HORDE_WAVE_CLEAR.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
    }

    /** 闪避成功：烟雾残影。 */
    public static void spawnDodgeParticles(ServerLevel level, Vec3 pos) {
        if (!SmartHordeConfig.isParticlesEnabledSafe()) {
            return;
        }
        int count = Math.min(8, SmartHordeConfig.maxParticlesSafe());
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, count, 0.25, 0.2, 0.25, 0.04);
    }

    /** 招式蓄力或命中：愤怒村民（前摇预警）/ 暴击火花（命中反馈）。 */
    public static void spawnAttackParticles(ServerLevel level, Vec3 pos, boolean telegraph) {
        if (!SmartHordeConfig.isParticlesEnabledSafe()) {
            return;
        }
        if (telegraph) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x, pos.y, pos.z, 1, 0.15, 0.1, 0.15, 0.0);
        } else {
            int count = Math.min(8, SmartHordeConfig.maxParticlesSafe());
            level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, count, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /** 尸潮百夫长战吼特效：金色冲击波环 + 震天战吼声。 */
    public static void spawnWarCryEffect(ServerLevel level, Entity source) {
        if (SmartHordeConfig.isParticlesEnabledSafe()) {
            spawnRingParticles(level, source.position().add(0, 0.35, 0),
                    ParticleTypes.FLAME, 2.6, 16, 0.03);
            int burst = Math.min(10, SmartHordeConfig.maxParticlesSafe());
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    source.getX(), source.getY() + source.getBbHeight() * 0.85, source.getZ(),
                    burst, 0.35, 0.35, 0.35, 0.12);
        }
        if (SmartHordeConfig.isSoundsEnabledSafe()) {
            level.playSound(null, source, ModSounds.WAR_CRY.get(), SoundSource.HOSTILE, 1.3F, 1.0F);
        }
    }

    /** 通用水平圆环粒子扩散（受 maxParticles 安全裁剪）。 */
    public static void spawnRingParticles(ServerLevel level, Vec3 center, ParticleOptions particle,
                                          double radius, int steps, double speed) {
        if (!SmartHordeConfig.isParticlesEnabledSafe()) {
            return;
        }
        int clampedSteps = Math.max(4, Math.min(steps, SmartHordeConfig.maxParticlesSafe()));
        for (int i = 0; i < clampedSteps; i++) {
            double theta = (Math.PI * 2.0 * i) / clampedSteps;
            double px = center.x + Math.cos(theta) * radius;
            double pz = center.z + Math.sin(theta) * radius;
            level.sendParticles(particle, px, center.y, pz, 1, 0.05, 0.05, 0.05, speed);
        }
    }

    /** 播放受控音效。 */
    public static void playSfx(ServerLevel level, Entity entity, SoundEvent sound, float volume, float pitch) {
        if (SmartHordeConfig.isSoundsEnabledSafe()) {
            level.playSound(null, entity, sound, SoundSource.HOSTILE, volume, pitch);
        }
    }
}
