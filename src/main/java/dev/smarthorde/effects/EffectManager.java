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
 * 粒子+音效集中管线。
 * 受 effects.particlesEnabled / effects.soundsEnabled 控制；
 * 粒子数量封顶 performance.maxParticles（默认 48）。
 */
public final class EffectManager {

    private EffectManager() {
    }

    /** 攻击特效：telegraph=true 时头顶冒 CRIT 预警粒子；否则为命中横扫粒子。 */
    public static void spawnAttackParticles(ServerLevel level, Vec3 center, boolean telegraph) {
        if (!SmartHordeConfig.PARTICLES_ENABLED.get()) {
            return;
        }
        if (telegraph) {
            send(level, ParticleTypes.CRIT, center, 6, 0.25, 0.1, 0.25, 0.05);
        } else {
            send(level, ParticleTypes.SWEEP_ATTACK, center, 2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    /** 闪避烟尘。 */
    public static void spawnDodgeParticles(ServerLevel level, Vec3 center) {
        if (!SmartHordeConfig.PARTICLES_ENABLED.get()) {
            return;
        }
        send(level, ParticleTypes.CLOUD, center.add(0, 0.3, 0), 6, 0.3, 0.1, 0.3, 0.02);
    }

    /** 尸潮来袭：火焰环绕 + 战号音效。 */
    public static void spawnHordeStartEffect(ServerLevel level, Vec3 center) {
        if (SmartHordeConfig.PARTICLES_ENABLED.get()) {
            send(level, ParticleTypes.FLAME, center.add(0, 1, 0), 24, 1.5, 0.5, 1.5, 0.01);
        }
        playSfx(level, center, ModSounds.HORDE_WAVE_START.get(), 1.2F, 1.0F);
    }

    /** 清波庆祝：烟花/快乐粒子 + 音效。 */
    public static void spawnWaveClearEffect(ServerLevel level, Vec3 center) {
        if (SmartHordeConfig.PARTICLES_ENABLED.get()) {
            send(level, ParticleTypes.HAPPY_VILLAGER, center.add(0, 1.5, 0), 20, 1.0, 0.8, 1.0, 0.1);
            send(level, ParticleTypes.FIREWORK, center.add(0, 2.0, 0), 12, 0.6, 0.6, 0.6, 0.05);
        }
        playSfx(level, center, ModSounds.HORDE_WAVE_CLEAR.get(), 1.0F, 1.0F);
    }

    /** 音效播放统一入口（受 soundsEnabled 控制）。 */
    public static void playSfx(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        if (!SmartHordeConfig.SOUNDS_ENABLED.get()) {
            return;
        }
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** 实体音效（跟随实体位置与移动）。 */
    public static void playSfx(ServerLevel level, Entity entity, SoundEvent sound, float volume, float pitch) {
        if (!SmartHordeConfig.SOUNDS_ENABLED.get()) {
            return;
        }
        level.playSound(null, entity, sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void send(ServerLevel level, ParticleOptions particle, Vec3 center,
                             int count, double spreadX, double spreadY, double spreadZ, double speed) {
        int capped = Math.min(count, SmartHordeConfig.MAX_PARTICLES.get());
        level.sendParticles(particle, center.x, center.y, center.z,
                capped, spreadX, spreadY, spreadZ, speed);
    }
}
