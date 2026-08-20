package dev.smarthorde.init;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** 实体默认属性（mod 总线事件，非 DeferredRegister）。 */
public final class ModAttributes {
    public static void register(IEventBus modBus) {
        modBus.addListener(ModAttributes::onAttributeCreation);
    }

    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        // SmartZombie
        event.put(ModEntities.SMART_ZOMBIE.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 48D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .build());

        // [轮10] HordeBoss —— 大幅强化
        event.put(ModEntities.HORDE_BOSS.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 64D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.ARMOR, 8.0D)
                .build());

        // [轮12] HordeArcher —— 远程型 Boss
        event.put(ModEntities.HORDE_ARCHER.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .build());

        // [轮12] HordeBrute —— 肉盾型 Boss
        event.put(ModEntities.HORDE_BRUTE.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.FOLLOW_RANGE, 32D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 15.0D)
                .build());
    }

    private ModAttributes() {}
}
