package dev.smarthorde.entity.ai.defense;

import dev.smarthorde.entity.SmartZombie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.EnumSet;

/**
 * 战术举盾推进（重装先锋专属 AI）：
 * 副手持盾且未处于破盾冷却时，在中远距离推进或遭遇玩家拉弓瞄准期间举起盾牌，
 * 稳步逼近至近战起手距离后收盾出招；玩家可用斧头重击触发破盾硬直。
 *
 * <p>注：仅通过 {@link SmartZombie#setShieldBlocking(boolean)} 驱动模型举盾与自定义格挡减伤，
 * 不调用原版 {@code startUsingItem}，避免触发原版二次 100% 免伤叠加与 80% 移速锁死 Bug。
 */
public class ShieldGuardGoal extends Goal {

    private static final double MIN_GUARD_DISTANCE = 3.5;
    private static final double MAX_GUARD_DISTANCE = 28.0;
    private static final double GUARD_SPEED_MODIFIER = 0.85;
    private static final int MAX_HOLD_TICKS = 75;
    private static final int REUSE_COOLDOWN_TICKS = 30;

    private final SmartZombie zombie;
    private int holdTicks;
    private int nextUseTick;
    private int repathCooldown;

    public ShieldGuardGoal(SmartZombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.zombie.tickCount < this.nextUseTick) {
            return false;
        }
        if (!this.zombie.hasOffhandShield() || this.zombie.isShieldDisabled()
                || this.zombie.isExecutingAttack() || this.zombie.isWallClimbing()) {
            return false;
        }
        LivingEntity target = this.zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double dist = this.zombie.distanceTo(target);
        if (isTargetDrawingRangedWeapon(target) && dist >= 2.2 && dist <= 40.0) {
            return true;
        }
        return dist >= MIN_GUARD_DISTANCE && dist <= MAX_GUARD_DISTANCE
                && this.zombie.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.zombie.hasOffhandShield() || this.zombie.isShieldDisabled()
                || this.zombie.isExecutingAttack() || this.zombie.isWallClimbing()) {
            return false;
        }
        if (this.holdTicks >= MAX_HOLD_TICKS) {
            return false;
        }
        LivingEntity target = this.zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double dist = this.zombie.distanceTo(target);
        if (isTargetDrawingRangedWeapon(target) && dist >= 2.2) {
            return dist <= 42.0;
        }
        return dist > MIN_GUARD_DISTANCE - 0.3 && dist <= MAX_GUARD_DISTANCE + 2.0;
    }

    @Override
    public void start() {
        this.holdTicks = 0;
        this.repathCooldown = 0;
        this.zombie.setShieldBlocking(true);
    }

    @Override
    public void tick() {
        this.holdTicks++;
        LivingEntity target = this.zombie.getTarget();
        if (target == null) {
            return;
        }
        this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.repathCooldown-- <= 0 || !this.zombie.getNavigation().isInProgress()) {
            this.repathCooldown = 6;
            this.zombie.getNavigation().moveTo(target, GUARD_SPEED_MODIFIER);
        }
    }

    @Override
    public void stop() {
        this.zombie.setShieldBlocking(false);
        this.nextUseTick = this.zombie.tickCount + REUSE_COOLDOWN_TICKS;
    }

    private static boolean isTargetDrawingRangedWeapon(LivingEntity target) {
        if (!(target instanceof Player player) || !player.isUsingItem()) {
            return false;
        }
        return player.getUseItem().getItem() instanceof ProjectileWeaponItem;
    }
}
