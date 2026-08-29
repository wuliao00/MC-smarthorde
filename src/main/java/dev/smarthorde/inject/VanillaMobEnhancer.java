package dev.smarthorde.inject;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.combat.SmartMeleeAttackGoal;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import dev.smarthorde.entity.ai.defense.DodgeGoal;
import dev.smarthorde.entity.ai.movement.ClimbOrStackGoal;
import dev.smarthorde.entity.ai.movement.FlankGoal;
import dev.smarthorde.entity.ai.movement.MaintainDistanceGoal;
import dev.smarthorde.entity.ai.movement.SeparationGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版僵尸增强注入（无反射，全部走公开 API）：
 * 监听 EntityJoinLevelEvent，检查 inject.vanillaMobs 配置；
 * 排除 SmartZombie/HordeBoss；NBT tag "smarthorde_injected" 防重复；
 * 注入内容：
 * - 属性：血量 x1.5、速度 x1.15；
 * - 攻击：原版 MeleeAttackGoal 整体替换为招式化 SmartMeleeAttackGoal
 *   （带动态 MOVE 释放——否则它会一直占住 MOVE，饿死攀墙/包抄等战术目标）；
 * - 智能 AI：SmartTargetGoal（优先弱者）、四源闪避、绕侧包抄、分离防挤团、
 *   距离管理、攀爬/叠罗汉；
 * - 仅替换玩家的 NearestAttackableTargetGoal（优先级<=2），村民等目标保留原版行为。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class VanillaMobEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger("VanillaMobEnhancer");
    private static final String INJECTED_TAG = "smarthorde_injected";

    private static final double SPEED_MULTIPLIER = 1.15;
    private static final double HEALTH_MULTIPLIER = 1.5;

    /** 原版僵尸的玩家目标选择器位于优先级 2（村民为 3，予以保留）。 */
    private static final int PLAYER_TARGET_PRIORITY = 2;

    private static boolean loggedFirstInjection;

    private VanillaMobEnhancer() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 其他 mod 可能取消本次入世（如自定义生成逻辑），取消时不再注入
        if (event.isCanceled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) {
            return;
        }
        // 排除本 mod 实体（SmartZombie/HordeBoss 均 extends Zombie）
        if (zombie instanceof dev.smarthorde.entity.SmartZombie
                || zombie instanceof dev.smarthorde.entity.HordeBoss) {
            return;
        }
        if (!SmartHordeConfig.INJECT_VANILLA.get()) {
            return;
        }
        if (zombie.getPersistentData().getBoolean(INJECTED_TAG)) {
            return;
        }
        zombie.getPersistentData().putBoolean(INJECTED_TAG, true);

        applyStatBuffs(zombie);
        upgradeBrain(zombie);

        if (!loggedFirstInjection) {
            loggedFirstInjection = true;
            LOGGER.info("[SmartHorde] Vanilla zombie enhanced: hp x{}, speed x{} + tactical AI goals installed",
                    HEALTH_MULTIPLIER, SPEED_MULTIPLIER);
        }
    }

    private static void applyStatBuffs(Zombie zombie) {
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() < 1024.0) {
            health.setBaseValue(health.getBaseValue() * HEALTH_MULTIPLIER);
            zombie.setHealth(zombie.getMaxHealth());
        }
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() < 16.0) {
            speed.setBaseValue(speed.getBaseValue() * SPEED_MULTIPLIER);
        }
    }

    private static void upgradeBrain(Zombie zombie) {
        // 0. 用招式化攻击整体替换原版 MeleeAttackGoal：原版实现长期持有 MOVE 标志，
        //    会饿死下面注入的所有战术移动 Goal；SmartMeleeAttackGoal 出招/卡住时释放 MOVE
        List<WrappedGoal> vanillaMelee = new ArrayList<>();
        for (WrappedGoal goal : zombie.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof MeleeAttackGoal
                    && !(goal.getGoal() instanceof SmartMeleeAttackGoal)) {
                vanillaMelee.add(goal);
            }
        }
        for (WrappedGoal goal : vanillaMelee) {
            zombie.goalSelector.removeGoal(goal.getGoal());
            zombie.goalSelector.addGoal(goal.getPriority(), new SmartMeleeAttackGoal(zombie, 1.0D, false));
        }

        // 1. 用 SmartTargetGoal 替换原版的玩家目标选择器（村民/其他敌对目标不受影响）
        List<WrappedGoal> playerTargeters = new ArrayList<>();
        for (WrappedGoal goal : zombie.targetSelector.getAvailableGoals()) {
            if (goal.getPriority() <= PLAYER_TARGET_PRIORITY
                    && goal.getGoal() instanceof NearestAttackableTargetGoal) {
                playerTargeters.add(goal);
            }
        }
        for (WrappedGoal goal : playerTargeters) {
            zombie.targetSelector.removeGoal(goal.getGoal());
        }
        zombie.targetSelector.addGoal(PLAYER_TARGET_PRIORITY, new SmartTargetGoal(zombie));

        // 2. 战术移动 AI（与 SmartZombie 同源实现）
        zombie.goalSelector.addGoal(1, new DodgeGoal(zombie));
        zombie.goalSelector.addGoal(4, new FlankGoal(zombie, 1.2));
        zombie.goalSelector.addGoal(5, new SeparationGoal(zombie));
        zombie.goalSelector.addGoal(6, new MaintainDistanceGoal(zombie, 1.05));
        zombie.goalSelector.addGoal(8, new ClimbOrStackGoal(zombie));
    }
}
