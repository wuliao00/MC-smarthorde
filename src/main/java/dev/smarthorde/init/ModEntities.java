package dev.smarthorde.init;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体注册 + 属性注册 + 生成规则。
 * 自然生成规则（weight=100，每组 1~4 只）由
 * data/smarthorde/neoforge/biome_modifier/smart_zombie_spawns.json 以数据驱动方式添加，
 * 这是 1.21.1 推荐做法，替代旧版 AddSpawnEvent/BiomeLoadingEvent。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SmartHordeMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SmartZombie>> SMART_ZOMBIE =
            ENTITY_TYPES.register("smart_zombie", () -> EntityType.Builder
                    .of(SmartZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("smart_zombie"));

    public static final DeferredHolder<EntityType<?>, EntityType<HordeBoss>> HORDE_BOSS =
            ENTITY_TYPES.register("horde_boss", () -> EntityType.Builder
                    .of(HordeBoss::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.2F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("horde_boss"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(SMART_ZOMBIE.get(), SmartZombie.createAttributes().build());
        event.put(HORDE_BOSS.get(), HordeBoss.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        // ON_GROUND 放置 + 亮度<=7 的怪物生成规则（Monster.checkMonsterSpawnRules 内部校验光照）。
        // ADD 不覆盖其他 mod 对同一实体的放置规则，兼容性更好
        event.register(SMART_ZOMBIE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SmartZombie::checkSmartZombieSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.ADD);
        event.register(HORDE_BOSS.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HordeBoss::checkHordeBossSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.ADD);
    }
}
