package dev.smarthorde.inject;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.ai.combat.SmartMeleeAttackGoal;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import dev.smarthorde.entity.ai.defense.DodgeGoal;
import dev.smarthorde.entity.ai.movement.ClimbOrStackGoal;
import dev.smarthorde.entity.ai.movement.FlankGoal;
import dev.smarthorde.entity.ai.movement.MaintainDistanceGoal;
import dev.smarthorde.entity.ai.movement.PeacefulStrollGoal;
import dev.smarthorde.entity.ai.movement.SeparationGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版陆地僵尸增强注入（无反射，全部走公开 API）：
 * <ul>
 *   <li>排除中立僵尸猪灵（避免下界无故主动敌对）与水生三叉戟溺尸（避免破坏游泳与远程投掷 AI）；</li>
 *   <li>属性强化（血量 x1.5、速度 x1.15）通过 PersistentData 标记仅首次生成生效，防止读档重复叠乘；</li>
 *   <li>每次实体入世幂等重装战术大脑，并将原版随机漫步替换为 {@link PeacefulStrollGoal} 防止交战中转身散步。</li>
 * </ul>
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class VanillaMobEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger("VanillaMobEnhancer");
    private static final String INJECTED_TAG = "smarthorde_injected";

    private static final double SPEED_MULTIPLIER = 1.15;
    private static final double HEALTH_MULTIPLIER = 1.5;

    /** 原版僵尸的玩家目标选择器位于优先级 2（村民/铁傀儡为 3，予以保留）。 */
    private static final int PLAYER_TARGET_PRIORITY = 2;

    private static boolean loggedFirstInjection;

    private VanillaMobEnhancer() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) {
            return;
        }
        if (zombie instanceof SmartZombie
                || zombie instanceof HordeBoss
                || zombie instanceof ZombifiedPiglin
                || zombie instanceof Drowned) {
            return;
        }
        if (SmartHordeConfig.SPEC.isLoaded() && !SmartHordeConfig.INJECT_VANILLA.get()) {
            return;
        }

        // 属性倍率随存档保留，仅首次标记时施加一次
        if (!zombie.getPersistentData().getBoolean(INJECTED_TAG)) {
            zombie.getPersistentData().putBoolean(INJECTED_TAG, true);
            applyStatBuffs(zombie);
        }

        // Goal 选择器仅驻留内存，读档/区块恢复后幂等重装
        upgradeBrainIdempotent(zombie);

        if (!loggedFirstInjection) {
            loggedFirstInjection = true;
            LOGGER.info("[SmartHorde] Vanilla land zombie enhanced: hp x{}, speed x{} + tactical AI goals installed",
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

    private static void upgradeBrainIdempotent(Zombie zombie) {
        for (WrappedGoal goal : zombie.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof SmartMeleeAttackGoal) {
                return;
            }
        }

        List<WrappedGoal> toRemoveGoals = new ArrayList<>();
        for (WrappedGoal goal : zombie.goalSelector.getAvailableGoals()) {
            if ((goal.getGoal() instanceof MeleeAttackGoal && !(goal.getGoal() instanceof SmartMeleeAttackGoal))
                    || (goal.getGoal() instanceof WaterAvoidingRandomStrollGoal
                    && !(goal.getGoal() instanceof PeacefulStrollGoal))) {
                toRemoveGoals.add(goal);
            }
        }
        for (WrappedGoal goal : toRemoveGoals) {
            zombie.goalSelector.removeGoal(goal.getGoal());
        }

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

        zombie.goalSelector.addGoal(1, new DodgeGoal(zombie));
        zombie.goalSelector.addGoal(2, new ClimbOrStackGoal(zombie));
        zombie.goalSelector.addGoal(3, new SmartMeleeAttackGoal(zombie, 1.05D, false));
        zombie.goalSelector.addGoal(4, new SeparationGoal(zombie));
        zombie.goalSelector.addGoal(5, new FlankGoal(zombie, 1.2));
        zombie.goalSelector.addGoal(6, new MaintainDistanceGoal(zombie, 1.05));
        zombie.goalSelector.addGoal(7, new PeacefulStrollGoal(zombie, 1.0));
    }
}
