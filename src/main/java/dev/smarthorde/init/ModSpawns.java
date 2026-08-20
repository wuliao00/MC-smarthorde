package dev.smarthorde.init;

import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** 自然生成规则：夜间按原版怪物规则刷出，为轮6夜间尸潮铺路。 */
public final class ModSpawns {
    public static void register(IEventBus modBus) {
        modBus.addListener(ModSpawns::onRegisterSpawnPlacements);
    }

    private static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.SMART_ZOMBIE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModSpawns() {}
}
