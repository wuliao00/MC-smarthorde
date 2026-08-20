package dev.smarthorde.inject;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原版怪物增强注入（轮11 扩展版）。
 *
 * <p>原版怪物加入世界时追加：
 * <ul>
 *   <li>索敌范围提升到 FOLLOW_RANGE 属性值（如有）</li>
 *   <li>移速 +20%</li>
 *   <li>血量 +30%（Creeper 不加血）</li>
 *   <li>攻击伤害 +20%（Creeper 不加伤害）</li>
 *   <li>添加 HurtByTargetGoal（被攻击反击）</li>
 *   <li>NearestAttackableTargetGoal 检测范围提升到 48 格</li>
 * </ul>
 * 用 NBT tag 防重复注入。受 {@code enhance.enabled} 配置控制。
 * {@code instanceof Monster} 自动覆盖 Zombie/Husk/Drowned/Skeleton/Stray/Spider/
 * CaveSpider/Creeper/Pillager/Vindicator/Ravager/Witch 等原版怪物。
 */
@EventBusSubscriber
public final class VanillaMobEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static final String TAG_INJECTED = "smarthorde_injected";

    /** NearestAttackableTargetGoal 的增强检测范围（格）。 */
    private static final double ENHANCED_FOLLOW_RANGE = 48.0D;
    /** 移速提升倍率（+20%）。 */
    private static final double SPEED_MULTIPLIER = 1.20D;
    /** 血量提升倍率（+30%）。 */
    private static final double HEALTH_MULTIPLIER = 1.30D;
    /** 攻击伤害提升倍率（+20%）。 */
    private static final double ATTACK_MULTIPLIER = 1.20D;

    private VanillaMobEnhancer() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!SmartHordeConfig.ENHANCE_VANILLA.get()) return;
        if (event.getLevel().isClientSide()) return;
        // 覆盖所有原版怪物：Zombie/Husk/Drowned/Skeleton/Stray/Spider/CaveSpider/
        // Creeper/Pillager/Vindicator/Ravager/Witch 均继承 Monster
        if (!(event.getEntity() instanceof Monster monster)) return;
        // Monster 派生自 Mob，绝大多数原版怪物（Zombie/Skeleton/Creeper 等）实际是
        // PathfinderMob；Ravager 直接继承 Monster/Mob。NearestAttackableTargetGoal 与
        // 属性 API 以 Mob 即可使用；HurtByTargetGoal 需 PathfinderMob，故按子类型分支。
        if (!(monster instanceof Mob mob)) return;

        if (mob.getPersistentData().getBoolean(TAG_INJECTED)) return;
        mob.getPersistentData().putBoolean(TAG_INJECTED, true);

        boolean isCreeper = mob instanceof Creeper;

        // 1. 提升索敌范围：替换 NearestAttackableTargetGoal 为 48 格检测版本
        mob.targetSelector.getAvailableGoals().removeIf(
                wrapper -> wrapper.getGoal() instanceof NearestAttackableTargetGoal);
        mob.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(mob, Player.class, true));

        // 若有 FOLLOW_RANGE 属性，同步提升基础值
        var followAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttr != null) {
            double cur = followAttr.getBaseValue();
            // 取 max(当前值, 48) 以保证至少能扫描到 48 格目标
            followAttr.setBaseValue(Math.max(cur, ENHANCED_FOLLOW_RANGE));
        }

        // 2. 移速 +20%
        var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * SPEED_MULTIPLIER);
        }

        // 3. 血量 +30%（Creeper 跳过，防过肉）
        if (!isCreeper) {
            var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                double newMax = Math.max(1.0D, healthAttr.getBaseValue() * HEALTH_MULTIPLIER);
                healthAttr.setBaseValue(newMax);
                ((Monster) mob).setHealth((float) newMax);
            }
        }

        // 4. 攻击伤害 +20%（Creeper 跳过，主要靠爆炸）
        if (!isCreeper) {
            var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.setBaseValue(attackAttr.getBaseValue() * ATTACK_MULTIPLIER);
            }
        }

        // 5. HurtByTargetGoal：被攻击时反击攻击者。需要 PathfinderMob（Ravager 不是，
        // 故跳过其 HIT_BY 反击 AI，其余原版怪物均满足）。
        if (mob instanceof PathfinderMob pathfinder) {
            mob.targetSelector.getAvailableGoals().removeIf(
                    wrapper -> wrapper.getGoal() instanceof HurtByTargetGoal);
            mob.targetSelector.addGoal(1, new HurtByTargetGoal(pathfinder));
        }

        LOGGER.debug("[SmartHorde] 注入增强: 原版怪物 {} @ {}",
                mob.getType().toShortString(), mob.blockPosition());
    }
}
