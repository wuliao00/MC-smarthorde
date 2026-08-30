package dev.smarthorde.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.DifficultyPreset;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.horde.HordeLeaderboard;
import dev.smarthorde.horde.HordeWaveManager;
import dev.smarthorde.horde.HordeWaveSpawner;
import dev.smarthorde.init.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /smarthorde 命令集（轮6+7）。
 */
public final class SmartHordeCommands {

    private SmartHordeCommands() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("smarthorde")
                .requires(src -> src.hasPermission(2));

        // ===== difficulty =====
        root.then(Commands.literal("difficulty")
                .executes(ctx -> {
                    DifficultyPreset p = DifficultyManager.get();
                    String msg = "当前难度: " + p.getId()
                            + "（生命x" + p.getHealthMultiplier()
                            + "，伤害x" + p.getDamageMultiplier()
                            + "，速度x" + p.getSpeedMultiplier() + "）";
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                })
                .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(DifficultyPreset.values()).map(DifficultyPreset::getId),
                                builder))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "preset");
                            DifficultyPreset preset = DifficultyPreset.byId(id);
                            DifficultyManager.set(preset);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "难度已切换为: " + preset.getId() + "（对新刷怪生效）"), true);
                            return 1;
                        })));

        // ===== summon =====
        root.then(Commands.literal("summon")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int count = IntegerArgumentType.getInteger(ctx, "count");
                            var spawned = HordeWaveSpawner.spawnWave(
                                    player.serverLevel(), player.blockPosition(), 0, count);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "已生成 " + spawned.size() + "/" + count + " 只尸潮怪物")
                                    .withStyle(ChatFormatting.AQUA), true);
                            return 1;
                        })));

        // ===== wave =====
        root.then(Commands.literal("wave")
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            HordeWaveManager.getInstance().startWave(player, 5);
                            return 1;
                        })
                        .then(Commands.argument("waves", IntegerArgumentType.integer(1, 30))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int waves = IntegerArgumentType.getInteger(ctx, "waves");
                                    HordeWaveManager.getInstance().startWave(player, waves);
                                    return 1;
                                })))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean ok = HordeWaveManager.getInstance().stopWave(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    ok ? "尸潮已终止" : "当前无进行中的尸潮")
                                    .withStyle(ok ? ChatFormatting.YELLOW : ChatFormatting.RED), true);
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String status = HordeWaveManager.getInstance().getStatus(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(status)
                                    .withStyle(ChatFormatting.GRAY), false);
                            return 1;
                        })));

        // ===== summon-boss 子命令 [轮10] =====
        root.then(Commands.literal("summon-boss")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HordeBoss boss = ModEntities.HORDE_BOSS.get().create(player.serverLevel());
                    if (boss == null) {
                        ctx.getSource().sendFailure(Component.literal("Boss 实体创建失败"));
                        return 0;
                    }
                    boss.setPos(player.getX(), player.getY() + 2.0D, player.getZ());
                    boss.finalizeSpawn(player.serverLevel(),
                            player.serverLevel().getCurrentDifficultyAt(player.blockPosition()),
                            MobSpawnType.EVENT, null);
                    player.serverLevel().addFreshEntity(boss);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "☠ 尸潮领主已降临！").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    return 1;
                }));

        // ===== summon-archer 子命令 [轮12] =====
        root.then(Commands.literal("summon-archer")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    dev.smarthorde.entity.HordeArcher archer = ModEntities.HORDE_ARCHER.get().create(player.serverLevel());
                    if (archer == null) { ctx.getSource().sendFailure(Component.literal("弓手创建失败")); return 0; }
                    archer.setPos(player.getX(), player.getY() + 2.0D, player.getZ());
                    archer.finalizeSpawn(player.serverLevel(),
                            player.serverLevel().getCurrentDifficultyAt(player.blockPosition()),
                            MobSpawnType.EVENT, null);
                    player.serverLevel().addFreshEntity(archer);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "🏹 尸潮弓手已降临！").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                    return 1;
                }));

        // ===== summon-brute 子命令 [轮12] =====
        root.then(Commands.literal("summon-brute")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    dev.smarthorde.entity.HordeBrute brute = ModEntities.HORDE_BRUTE.get().create(player.serverLevel());
                    if (brute == null) { ctx.getSource().sendFailure(Component.literal("蛮兽创建失败")); return 0; }
                    brute.setPos(player.getX(), player.getY() + 2.0D, player.getZ());
                    brute.finalizeSpawn(player.serverLevel(),
                            player.serverLevel().getCurrentDifficultyAt(player.blockPosition()),
                            MobSpawnType.EVENT, null);
                    player.serverLevel().addFreshEntity(brute);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "🛡 尸潮蛮兽已降临！").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    return 1;
                }));

        // ===== leaderboard 子命令 [轮12] =====
        root.then(Commands.literal("leaderboard")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HordeLeaderboard board = HordeLeaderboard.get(player.serverLevel());
                    board.sendReport(player);
                    return 1;
                }));

        event.getDispatcher().register(root);
    }
}
