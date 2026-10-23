package dev.smarthorde.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.horde.HordeWaveManager;
import dev.smarthorde.horde.HordeWaveSpawner;
import dev.smarthorde.init.ModEntities;
import dev.smarthorde.stats.HordeStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 命令注册（权限等级 2）：
 * <ul>
 *   <li>{@code /smarthorde difficulty [preset]}</li>
 *   <li>{@code /smarthorde summon [count] [role]}（支持指定兵种或 random）</li>
 *   <li>{@code /smarthorde summon-boss [variant]}（安全地面无穿模召唤）</li>
 *   <li>{@code /smarthorde wave start [waves] | stop | skip | info}</li>
 *   <li>{@code /smarthorde clear}（一键清理当前维度所有 SmartZombie / HordeBoss）</li>
 *   <li>{@code /smarthorde top} / {@code /smarthorde stats}</li>
 * </ul>
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class SmartHordeCommands {

    private SmartHordeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("smarthorde")
                .then(Commands.literal("difficulty")
                        .executes(context -> queryDifficulty(context.getSource()))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .requires(source -> source.hasPermission(2))
                                .suggests(SmartHordeCommands::suggestPresets)
                                .executes(context -> setDifficulty(
                                        context.getSource(), StringArgumentType.getString(context, "preset")))))
                .then(Commands.literal("summon")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> summonZombies(context.getSource(), 3, null))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
                                .executes(context -> summonZombies(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count"), null))
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(SmartHordeCommands::suggestRoles)
                                        .executes(context -> summonZombies(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                StringArgumentType.getString(context, "role"))))))
                .then(Commands.literal("summon-boss")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> summonBoss(context.getSource(), null))
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests(SmartHordeCommands::suggestVariants)
                                .executes(context -> summonBoss(context.getSource(),
                                        StringArgumentType.getString(context, "variant")))))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clearModEntities(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("top")
                        .executes(context -> showLeaderboard(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> showStats(context.getSource())))
                .then(Commands.literal("wave")
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> startWaves(context.getSource(), 5))
                                .then(Commands.argument("waves", IntegerArgumentType.integer(1, 20))
                                        .executes(context -> startWaves(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "waves")))))
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> stopWaves(context.getSource())))
                        .then(Commands.literal("skip")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> skipWave(context.getSource())))
                        .then(Commands.literal("info")
                                .executes(context -> waveInfo(context.getSource())))));
    }

    private static CompletableFuture<Suggestions> suggestPresets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(DifficultyManager.Preset.values()).map(Enum::name).map(String::toLowerCase), builder);
    }

    private static CompletableFuture<Suggestions> suggestRoles(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Stream<String> roles = Stream.concat(
                Stream.of("random"),
                Arrays.stream(EliteRole.values()).map(r -> r.id));
        return SharedSuggestionProvider.suggest(roles, builder);
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

    private static int summonZombies(CommandSourceStack source, int count, @Nullable String roleId)
            throws CommandSyntaxException {
        EliteRole forcedRole = null;
        if (roleId != null && !"random".equalsIgnoreCase(roleId)) {
            try {
                forcedRole = EliteRole.byId(roleId);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable("commands.smarthorde.summon.invalid_role", roleId));
                return 0;
            }
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        int spawned = HordeWaveSpawner
                .spawnWave(level, player.blockPosition(), 0, count, 6.0, 12.0, forcedRole, null)
                .size();
        final int finalSpawned = spawned;
        if (forcedRole != null) {
            final EliteRole displayRole = forcedRole;
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.summon.success_role",
                    finalSpawned, Component.translatable(displayRole.nameKey())), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.summon.success", finalSpawned), true);
        }
        return spawned;
    }

    private static int summonBoss(CommandSourceStack source, @Nullable String variantId) throws CommandSyntaxException {
        if (!SmartHordeConfig.isBossEnabledSafe()) {
            source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.disabled"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        HordeBoss.Variant variant;
        if (variantId == null) {
            variant = HordeBoss.Variant.random(level.getRandom());
        } else {
            try {
                variant = HordeBoss.Variant.byId(variantId);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.invalid_variant", variantId));
                return 0;
            }
        }
        EntityType<HordeBoss> type = ModEntities.HORDE_BOSS.get();

        HordeBoss boss = type.create(level);
        if (boss == null) {
            source.sendFailure(Component.translatable("commands.smarthorde.summon_boss.disabled"));
            return 0;
        }
        boss.setVariant(variant);

        Vec3 lookFlat = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        Vec3 forward = lookFlat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : lookFlat.normalize();
        Vec3 preferred = player.position().add(forward.scale(5.5));
        Vec3 safePos = HordeWaveSpawner.findSafeBossSpawnPos(level, boss, preferred, player.blockPosition());

        boss.moveTo(safePos.x, safePos.y, safePos.z, player.getYRot() + 180.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(safePos)), MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);

        source.sendSuccess(() -> Component.translatable(
                "commands.smarthorde.summon_boss.success_variant", Component.translatable(variant.nameKey())), true);
        return 1;
    }

    private static int clearModEntities(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer caller = source.getPlayer();
        if (caller != null) {
            HordeWaveManager.stop(caller);
        }
        List<SmartZombie> zombies = new ArrayList<>(
                level.getEntities(EntityTypeTest.forClass(SmartZombie.class), SmartZombie::isAlive));
        List<HordeBoss> bosses = new ArrayList<>(
                level.getEntities(EntityTypeTest.forClass(HordeBoss.class), HordeBoss::isAlive));

        zombies.forEach(SmartZombie::discard);
        bosses.forEach(b -> {
            b.getBossBar().removeAllPlayers();
            b.discard();
        });
        int removed = zombies.size() + bosses.size();
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.clear.success",
                zombies.size(), bosses.size()), true);
        return removed;
    }

    private static int showLeaderboard(CommandSourceStack source) {
        List<ServerPlayer> players = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
        players.sort(Comparator.comparingLong(HordeStats::leaderboardScore).reversed());
        if (players.isEmpty()) {
            source.sendFailure(Component.translatable("commands.smarthorde.top.empty"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.top.header"), false);
        int rank = 1;
        for (ServerPlayer player : players.stream().limit(10).toList()) {
            final int displayRank = rank++;
            final ServerPlayer entry = player;
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.top.line",
                    displayRank, entry.getGameProfile().getName(),
                    HordeStats.get(entry, HordeStats.HORDES_COMPLETED),
                    HordeStats.get(entry, HordeStats.WAVES_CLEARED),
                    HordeStats.get(entry, HordeStats.BOSSES_KILLED)), false);
        }
        return players.size();
    }

    private static int showStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.stats.line",
                HordeStats.get(player, HordeStats.HORDES_COMPLETED),
                HordeStats.get(player, HordeStats.WAVES_CLEARED),
                HordeStats.get(player, HordeStats.ZOMBIES_KILLED),
                HordeStats.get(player, HordeStats.ELITES_KILLED),
                HordeStats.get(player, HordeStats.BOSSES_KILLED)), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<SmartZombie> zombies = level.getEntities(EntityTypeTest.forClass(SmartZombie.class), SmartZombie::isAlive);
        long elites = zombies.stream().filter(z -> z.getEliteRole().isElite()).count();
        int bosses = level.getEntities(EntityTypeTest.forClass(HordeBoss.class), HordeBoss::isAlive).size();
        String preset = DifficultyManager.current().name().toLowerCase();
        int sessions = HordeWaveManager.getActiveSessionCount();
        source.sendSuccess(() -> Component.translatable("commands.smarthorde.status.line",
                preset, zombies.size(), elites, bosses, sessions), false);
        return 1;
    }

    private static int startWaves(CommandSourceStack source, int waves) throws CommandSyntaxException {
        if (!SmartHordeConfig.isHordeEnabledSafe()) {
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

    private static int skipWave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (HordeWaveManager.skipCurrentWave(player)) {
            source.sendSuccess(() -> Component.translatable("commands.smarthorde.wave.skipped"), true);
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
