package dev.smarthorde.init;

import dev.smarthorde.SmartHorde;
import dev.smarthorde.entity.HordeArcher;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.HordeBrute;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SmartHorde.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SmartZombie>> SMART_ZOMBIE =
            ENTITY_TYPES.register("smart_zombie", () -> EntityType.Builder
                    .of(SmartZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
                    .build("smart_zombie"));

    // [轮10] Boss 实体
    public static final DeferredHolder<EntityType<?>, EntityType<HordeBoss>> HORDE_BOSS =
            ENTITY_TYPES.register("horde_boss", () -> EntityType.Builder
                    .of(HordeBoss::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.2F)
                    .eyeHeight(2.8F)
                    .clientTrackingRange(16)
                    .build("horde_boss"));

    // [轮12] Boss 变体：远程弓手
    public static final DeferredHolder<EntityType<?>, EntityType<HordeArcher>> HORDE_ARCHER =
            ENTITY_TYPES.register("horde_archer", () -> EntityType.Builder
                    .of(HordeArcher::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.0F)
                    .eyeHeight(1.8F)
                    .clientTrackingRange(16)
                    .build("horde_archer"));

    // [轮12] Boss 变体：肉盾蛮兽
    public static final DeferredHolder<EntityType<?>, EntityType<HordeBrute>> HORDE_BRUTE =
            ENTITY_TYPES.register("horde_brute", () -> EntityType.Builder
                    .of(HordeBrute::new, MobCategory.MONSTER)
                    .sized(1.4F, 1.6F)
                    .eyeHeight(1.0F)
                    .clientTrackingRange(16)
                    .build("horde_brute"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    private ModEntities() {}
}
