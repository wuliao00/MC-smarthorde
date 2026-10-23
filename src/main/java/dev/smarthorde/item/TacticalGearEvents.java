package dev.smarthorde.item;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 智潮战术装备战斗被动事件处理（零方块破坏，纯攻防机制联动）。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class TacticalGearEvents {

    private static final float HELMET_HORDE_DAMAGE_MUL = 0.85F;
    private static final float BLADE_ELITE_BOSS_DAMAGE_MUL = 1.25F;

    private TacticalGearEvents() {
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();

        // 1. 智潮战术头盔：降低来自智能僵尸与尸潮领主的 15% 伤害
        if (victim instanceof Player player
                && player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SmartHelmetItem
                && (sourceEntity instanceof SmartZombie || sourceEntity instanceof HordeBoss)) {
            event.setAmount(event.getAmount() * HELMET_HORDE_DAMAGE_MUL);
        }

        // 2. 智潮破阵之刃：对精英智尸与尸潮领主造成 +25% 破阵伤害
        if (sourceEntity instanceof LivingEntity attacker
                && attacker.getMainHandItem().getItem() instanceof SmartBladeItem) {
            boolean bonusTarget = (victim instanceof SmartZombie sz && sz.getEliteRole().isElite())
                    || victim instanceof HordeBoss;
            if (bonusTarget) {
                event.setAmount(event.getAmount() * BLADE_ELITE_BOSS_DAMAGE_MUL);
                if (victim.level() instanceof ServerLevel serverLevel
                        && SmartHordeConfig.isParticlesEnabledSafe()) {
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.55, victim.getZ(),
                            6, 0.25, 0.25, 0.25, 0.15);
                }
            }
        }
    }
}
