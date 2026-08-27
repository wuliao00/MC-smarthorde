package dev.smarthorde.entity.ai.defense;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.init.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 三源闪避：
 * 1. 受击后 5 tick 内 -> 向后 + 侧向冲刺；
 * 2. 检测 48 格内持弓瞄准本实体的玩家 -> 横向侧跳；
 * 3. 检测 8 格内朝自己飞来的箭矢 -> 垂直于箭矢方向冲刺。
 * 冷却从配置读取（默认 90 tick = 4.5 秒，受难度影响）。
 * 面向任意 PathfinderMob：SmartZombie 直接使用，
 * 原版僵尸经 VanillaMobEnhancer 注入同款行为。
 */
public class DodgeGoal extends Goal {

    private static final double BOW_THREAT_RANGE = 48.0;
    private static final double ARROW_THREAT_RANGE = 8.0;
    private static final double DASH_STRENGTH = 0.55;

    private final PathfinderMob mob;
    private int nextDodgeTick;

    public DodgeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextDodgeTick || !this.mob.onGround()) {
            return false;
        }
        return findDodgeVector() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // 一次性冲刺
    }

    @Override
    public void start() {
        Vec3 dodge = findDodgeVector();
        if (dodge == null) {
            return;
        }
        this.mob.setDeltaMovement(new Vec3(dodge.x * DASH_STRENGTH, 0.28, dodge.z * DASH_STRENGTH));
        this.mob.hasImpulse = true;
        this.nextDodgeTick = this.mob.tickCount + DifficultyManager.dodgeCooldownTicks();

        if (this.mob.level() instanceof ServerLevel serverLevel) {
            EffectManager.spawnDodgeParticles(serverLevel, this.mob.position());
            if (SmartHordeConfig.SOUNDS_ENABLED.get()) {
                serverLevel.playSound(null, this.mob,
                        ModSounds.SMART_DODGE.get(), SoundSource.HOSTILE, 0.7F, 1.2F);
            }
        }
    }

    /** 依次检测三源威胁，返回冲刺方向（水平单位向量）。 */
    private Vec3 findDodgeVector() {
        // 源 1：受击后 5 tick 内（hurtTime 从 10 递减，>5 表示刚受击）
        if (this.mob.hurtTime > 5) {
            LivingEntity attacker = this.mob.getLastHurtByMob();
            Vec3 away = attacker != null
                    ? flatten(this.mob.position().subtract(attacker.position()))
                    : flatten(this.mob.getLookAngle().scale(-1));
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(0.6);
            return away.add(side).normalize();
        }

        // 源 2：48 格内正在拉弓瞄准的玩家
        Vec3 bowThreat = findAimingArcher();
        if (bowThreat != null) {
            Vec3 away = flatten(this.mob.position().subtract(new Vec3(bowThreat.x, this.mob.getY(), bowThreat.z)));
            boolean left = this.mob.getRandom().nextBoolean();
            return left ? new Vec3(-away.z, 0, away.x) : new Vec3(away.z, 0, -away.x);
        }

        // 源 3：8 格内朝自己飞来的箭矢
        Vec3 arrowMotion = findIncomingArrowMotion();
        if (arrowMotion != null) {
            Vec3 perpendicular = new Vec3(-arrowMotion.z, 0, arrowMotion.x).normalize();
            boolean left = this.mob.getRandom().nextBoolean();
            return left ? perpendicular : perpendicular.scale(-1);
        }
        return null;
    }

    /** 返回正在拉弓瞄准的玩家位置，无则 null。 */
    private Vec3 findAimingArcher() {
        List<Player> players = this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(BOW_THREAT_RANGE),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.isUsingItem()
                        && p.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
        if (players.isEmpty()) {
            return null;
        }
        return players.get(0).position();
    }

    /** 返回朝本实体飞来的箭矢运动方向，无则 null。 */
    private Vec3 findIncomingArrowMotion() {
        List<AbstractArrow> arrows = this.mob.level().getEntitiesOfClass(AbstractArrow.class,
                this.mob.getBoundingBox().inflate(ARROW_THREAT_RANGE),
                arrow -> arrow.isAlive() && arrow.getOwner() != this.mob);
        for (AbstractArrow arrow : arrows) {
            Vec3 motion = arrow.getDeltaMovement();
            Vec3 toMe = this.mob.position().subtract(arrow.position());
            if (motion.horizontalDistanceSqr() > 0.01 && flatten(motion).dot(flatten(toMe)) > 0.7) {
                return motion;
            }
        }
        return null;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
