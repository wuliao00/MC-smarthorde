package dev.smarthorde.effects;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.combat.AttackMove;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * 集中式特效管线（轮9）。服务端权威：sendParticles 自动同步给附近客户端。
 * 所有方法自带配置开关检查，调用方无需判断。
 */
public final class HordeEffects {

    private HordeEffects() {}

    private static boolean particles() { return SmartHordeConfig.EFFECT_PARTICLES.get(); }
    private static boolean sounds()    { return SmartHordeConfig.EFFECT_SOUNDS.get(); }

    // ===== 攻击：前摇蓄力 =====
    public static void playWindup(ServerLevel level, Mob mob) {
        if (particles()) {
            level.sendParticles(ParticleTypes.CRIT,
                    mob.getX(), mob.getY() + mob.getBbHeight() + 0.2D, mob.getZ(),
                    8, 0.25D, 0.15D, 0.25D, 0.03D);
        }
        if (sounds()) {
            level.playSound(null, mob.blockPosition(), ModSounds.SWING_WINDUP.get(),
                    SoundSource.HOSTILE, 0.8F, 0.7F + mob.getRandom().nextFloat() * 0.3F);
        }
    }

    // ===== 攻击：命中瞬间 =====
    public static void playStrike(ServerLevel level, Mob mob, AttackMove move) {
        if (particles()) {
            if (move.id() == 1) {
                Vec3 look = mob.getLookAngle();
                level.sendParticles(ParticleTypes.SONIC_BOOM,
                        mob.getX() + look.x * 1.2D,
                        mob.getY() + mob.getBbHeight() * 0.6D,
                        mob.getZ() + look.z * 1.2D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            } else {
                spawnArc(level, mob, move.arcDegrees(), move.range());
            }
        }
        if (sounds()) {
            level.playSound(null, mob.blockPosition(), ModSounds.STRIKE_HIT.get(),
                    SoundSource.HOSTILE, 0.9F, 0.85F + mob.getRandom().nextFloat() * 0.3F);
        }
    }

    private static void spawnArc(ServerLevel level, Mob mob, float arcDegrees, double range) {
        Vec3 look = mob.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        double arcRad = Math.toRadians(arcDegrees);
        int count = Mth.clamp((int) (arcDegrees / 20.0F), 3, 20);
        double y = mob.getY() + mob.getBbHeight() * 0.65D;
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5D : (double) i / (count - 1);
            double a = baseAngle - arcRad / 2.0D + arcRad * t;
            double r = range * 0.75D;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    mob.getX() + Math.cos(a) * r,
                    y + (level.getRandom().nextDouble() - 0.5D) * 0.3D,
                    mob.getZ() + Math.sin(a) * r,
                    1, 0.02D, 0.02D, 0.02D, 0.01D);
        }
    }

    // ===== 闪避 =====
    public static void playDodge(ServerLevel level, Vec3 pos) {
        if (particles()) {
            level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.6D, pos.z,
                    12, 0.35D, 0.45D, 0.35D, 0.03D);
            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.3D, pos.z,
                    6, 0.25D, 0.2D, 0.25D, 0.01D);
        }
        if (sounds()) {
            level.playSound(null, BlockPos.containing(pos), ModSounds.EVADE_WHOOSH.get(),
                    SoundSource.HOSTILE, 0.7F, 1.2F + level.getRandom().nextFloat() * 0.3F);
        }
    }

    // ===== 尸潮开场 =====
    public static void playWaveStart(ServerLevel level, BlockPos center, double radius) {
        if (sounds()) {
            level.playSound(null, center, ModSounds.WAVE_HORN.get(),
                    SoundSource.HOSTILE, 3.0F, 0.9F);
        }
        if (particles()) {
            int count = 48;
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2.0D * i / count;
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        center.getX() + 0.5D + Math.cos(a) * radius,
                        center.getY() + 0.2D,
                        center.getZ() + 0.5D + Math.sin(a) * radius,
                        1, 0.0D, 0.05D, 0.0D, 0.01D);
            }
        }
    }

    // ===== 清波 =====
    public static void playWaveClear(ServerLevel level, Vec3 pos) {
        if (sounds()) {
            level.playSound(null, BlockPos.containing(pos), ModSounds.WAVE_CLEAR.get(),
                    SoundSource.MASTER, 1.0F, 1.0F);
        }
        spawnFireworks(level, pos, 10);
    }

    // ===== 通关 =====
    public static void playVictory(ServerLevel level, Vec3 pos) {
        if (sounds()) {
            level.playSound(null, BlockPos.containing(pos), ModSounds.VICTORY.get(),
                    SoundSource.MASTER, 1.2F, 1.0F);
        }
        spawnFireworks(level, pos, 30);
    }

    private static void spawnFireworks(ServerLevel level, Vec3 pos, int count) {
        if (!particles()) return;
        for (int i = 0; i < count; i++) {
            double dx = (level.getRandom().nextDouble() - 0.5D) * 3.0D;
            double dy = level.getRandom().nextDouble() * 2.5D + 0.5D;
            double dz = (level.getRandom().nextDouble() - 0.5D) * 3.0D;
            level.sendParticles(ParticleTypes.FIREWORK,
                    pos.x + dx, pos.y + dy, pos.z + dz,
                    1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
    }
}
