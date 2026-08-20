package dev.smarthorde.client;

import dev.smarthorde.SmartHorde;
import dev.smarthorde.init.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端渲染注册（轮11修复）。
 * 用 HumanoidMobRenderer 子类让 SmartZombie/Boss 有可见模型和贴图。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SmartHorde.MOD_ID)
public final class SmartHordeClient {

    private SmartHordeClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SMART_ZOMBIE.get(), SmartZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.HORDE_BOSS.get(), HordeBossRenderer::new);
        event.registerEntityRenderer(ModEntities.HORDE_ARCHER.get(), HordeArcherRenderer::new);
        event.registerEntityRenderer(ModEntities.HORDE_BRUTE.get(), HordeBruteRenderer::new);
    }
}
