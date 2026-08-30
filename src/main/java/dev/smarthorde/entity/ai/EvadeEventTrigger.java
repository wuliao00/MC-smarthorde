package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * [C4] 事件驱动闪避触发器。
 *
 * <p>SmartZombie 即将受到伤害时（{@link LivingIncomingDamageEvent}，伤害结算前），
 * 为其 {@link EvadeGoal} 置位 pendingEvade 标志；canUse 轮询到该标志后
 * 立即执行受击闪避（导航规避，不取消伤害）。hurtTime 轮询路径保留为兜底。
 *
 * <p>通过遍历 goalSelector（Mob 公开字段）定位 EvadeGoal，无需在 SmartZombie 中持有引用；
 * NeoForge {@code @EventBusSubscriber} 注解自动注册到游戏总线。
 */
@EventBusSubscriber
public final class EvadeEventTrigger {

    private EvadeEventTrigger() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!SmartHordeConfig.EVADE.get()) return;
        if (!(event.getEntity() instanceof SmartZombie zombie)) return;
        if (zombie.level().isClientSide()) return;

        for (WrappedGoal wrapper : zombie.goalSelector.getAvailableGoals()) {
            if (wrapper.getGoal() instanceof EvadeGoal evadeGoal) {
                evadeGoal.requestEvade();
                break;
            }
        }
    }
}
