package dev.smarthorde.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.horde.HordeWaveManager;
import dev.smarthorde.horde.HordeWaveSpawner;
import dev.smarthorde.init.ModEntities;
import dev.smarthorde.stats.HordeStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;

import javax.annotation.Nullable;

/**
 * 命令注册（权限等级 2）：
 * /smarthorde difficulty [preset]
 * /smarthorde summon [count]
 * /smarthorde summon-boss [variant]
 * /smarthorde wave start [waves] | wave stop | wave info
 * /smarthorde top（尸潮排行榜）
 * /smarthorde stats（个人统计）
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class SmartHordeCommands {

    private SmartHordeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("smarthorde")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("difficulty")
                        .executes(context -> queryDifficulty(context.getSource()))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(SmartHordeCommands::suggestPresets)
                                .executes(context -> setDifficulty(
                                        context.getSource(), StringArgumentType.getString(context, "preset")))))
                .then(Commands.literal("summon")
                        .executes(context -> summonZombies(context.getSource(), 3))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(context -> summonZombies(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("summon-boss")
                        .executes(context -> summonBoss(context.getSource(), null))
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests(SmartHordeCommands::suggestVariants)
                                .executes(context -> summonBoss(context.getSource(),
                                        StringArgumentType.getString(context, "variant")))))
                .then(Commands.literal("top")
                        .executes(context -> showLeaderboard(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> showStats(context.getSource())))
                .then(Commands.literal("wave")
                        .then(Commands.literal("start")
                                .executes(context -> startWaves(context.getSource(), 5))
                                .then(Commands.argument("waves", IntegerArgumentType.integer(1, 20))
                                        .executes(context -> startWaves(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "waves")))))
                        .then(Commands.literal("stop")
                                .executes(context -> stopWaves(context.getSource())))
                        .then(Commands.literal("info")
                                .executes(context -> waveInfo(context.getSource())))));
    }

    private static CompletableFuture<Suggestions> suggestPresets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(DifficultyManager.Preset.values()).map(Enum::name).map(String::toLowerCase), builder);
    }

    private static CompletableFuture<Suggestions> suggestVariants(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(HordeBoss.Variant.values()).map(v -> v.id), builder);
    }

    private static int queryDifficulty(CommandSourceStack source) {
        DifficultyManager.Preset preset = DifficultyManager.current();
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.difficulty.get",
                preset.name().toLowerCase()), false);
        return 1;
    }

    private static int setDifficulty(CommandSourceStack source, String raw) {
        DifficultyManager.Preset preset;
        try {
            preset = DifficultyManager.Preset.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("commands.smarthorde.difficulty.invalid", raw));
            return 0;
        }
        DifficultyManager.setOverride(preset);
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.difficulty.set",
                preset.name().toLowerCase()), true);
        return 1;
    }

    private static int summonZombies(CommandSourceStack source, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        int spawned = HordeWaveSpawner
                .spawnWave(level, player.blockPosition(), 0, count, 6.0, 12.0)
                .size();
        final int finalSpawned = spawned;
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.summon.success", finalSpawned), true);
        return spawned;
    }

    private static int summonBoss(CommandSourceStack source, @Nullable String variantId) throws CommandSyntaxException {
        if (!SmartHordeConfig.BOSS_ENABLED.get()) {
            source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.disabled"));
            return 0;
        }
        HordeBoss.Variant variant;
        if (variantId == null) {
            variant = HordeBoss.Variant.random(source.getServer().overworld().getRandom());
        } else {
            try {
                variant = HordeBoss.Variant.byId(variantId);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.invalid_variant", variantId));
                return 0;
            }
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        EntityType<HordeBoss> type = ModEntities.HORDE_BOSS.get();

        HordeBoss boss = type.create(level);
        if (boss == null) {
            source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.disabled"));
            return 0;
        }
        boss.setVariant(variant);
        Vec3 ahead = player.position().add(player.getLookAngle().scale(5));
        boss.moveTo(ahead.x, ahead.y, ahead.z, player.getYRot() + 180.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);
        final String variantName = variant.id;
        source.sendSuccess(() -> Component.translatable(
                "commands.smarthorde.summon_boss.success_variant", Component.translatable(variant.nameKey())), true);
        return 1;
    }

    /** 尸潮排行榜（在线玩家，按通关数+清波数排序）。 */
    private static int showLeaderboard(CommandSourceStack source) {
        List<ServerPlayer> players = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
        players.sort(Comparator.comparingLong(HordeStats::leaderboardScore).reversed());
        if (players.isEmpty()) {
            source.sendFailure(Component.translatable("commands.smarthorde.top.empty"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.top.header"), false);
        final List<ServerPlayer> ranked = players;
        int rank = 1;
        for (ServerPlayer player : ranked.stream().limit(10).toList()) {
            final int displayRank = rank++;
            final ServerPlayer entry = player;
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.top.line",
                    displayRank, entry.getGameProfile().getName(),
                    HordeStats.get(entry, HordeStats.HORDES_COMPLETED),
                    HordeStats.get(entry, HordeStats.WAVES_CLEARED),
                    HordeStats.get(entry, HordeStats.BOSSES_KILLED)), false);
        }
        return ranked.size();
    }

    private static int showStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.stats.line",
                HordeStats.get(player, HordeStats.HORDES_COMPLETED),
                HordeStats.get(player, HordeStats.WAVES_CLEARED),
                HordeStats.get(player, HordeStats.ZOMBIES_KILLED),
                HordeStats.get(player, HordeStats.BOSSES_KILLED)), false);
        return 1;
    }

    private static int startWaves(CommandSourceStack source, int waves) throws CommandSyntaxException {
        if (!SmartHordeConfig.HORDE_ENABLED.get()) {
            source.sendFailure(Component.translatable("commands.smarthorde.wave.disabled"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (!HordeWaveManager.start(player, waves)) {
            source.sendFailure(Component.translatable("commands.smarthorde.wave.already_active"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.wave.started", waves), true);
        return 1;
    }

    private static int stopWaves(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (HordeWaveManager.stop(player)) {
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.wave.stopped"), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.smarthorde.wave.no_session"));
        return 0;
    }

    private static int waveInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HordeWaveManager.WaveSession session = HordeWaveManager.sessionOf(player.getUUID());
        if (session == null) {
            source.sendFailure(Component.translatable("commands.smarthorde.wave.no_session"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.wave.info",
                session.currentWave + 1, session.totalWaves, session.state.name(), session.remainingMobs()), false);
        return 1;
    }
}
