# SmartHorde

为 Minecraft 1.21.1 打造的智能尸潮模组（NeoForge）。僵尸不再是站桩靶子：它们会连招、闪避、绕后包抄、攀爬翻墙、叠罗汉，并在夜晚以波次形式进攻玩家。所有数值均可通过配置文件调整，无硬编码魔法数字。

## 功能特性

- **智能僵尸（Smart Zombie）**：招式化连招攻击、受击/被瞄准时闪避、绕侧绕后包抄、维持最佳攻击距离、攀爬翻墙与叠罗汉够人、智能目标选择（威胁/仇恨/低血量优先）。
- **自然生成**：智能僵尸会在主世界（`#minecraft:is_overworld` 生物群系）自然生成，权重接近原版僵尸（weight 100，每群 1-4 只）；可通过数据包覆写 biome modifier（`neoforge:biome_modifier/smart_zombie_spawns`）关闭。
- **尸潮领主（Horde Boss）**：多阶段 Boss 战斗，按血量切换激进/防守/狂暴行为，切阶段时召唤仆从尸潮，自带 Boss 血条。
- **尸潮弓手（Horde Archer）**：远程型 Boss，保持距离放箭。
- **尸潮蛮兵（Horde Brute）**：肉盾型 Boss，高血量正面压制。
- **波次系统**：手动或夜间自动触发（自动触发含冷却：上次会话结束后需间隔波次间隔才会再开）；首波倒计时固定 5 秒，波次间倒计时使用波次间隔配置；规模与近战/远程/绕后/指挥构成权重均可配置（远程/绕后为 Boss 级单位，受 Boss 总开关约束）；清波与通关有奖励。
- **四档难度**：easy / normal / hard / nightmare 预设，统一缩放生命、伤害、速度。
- **头顶血条 HUD**：Boss 与精英单位头顶显示血量。
- **原版怪增强**：可按白名单让原版僵尸、骷髅等替换站桩 AI，获得同款战术行为（末影人、监守者默认不在名单内）。
- **附加内容**：排行榜统计玩家战绩，附赠"智潮之刃""智潮头盔"合成配方。

## 命令

以下命令需要管理员权限（权限等级 2）：

| 命令 | 说明 |
| --- | --- |
| `/smarthorde difficulty` | 查看当前难度预设及数值倍率 |
| `/smarthorde difficulty <preset>` | 切换难度预设（easy / normal / hard / nightmare） |
| `/smarthorde summon <count>` | 在执行者周围生成指定数量（1-100）的智能僵尸 |
| `/smarthorde wave start` | 开启尸潮（默认 5 波） |
| `/smarthorde wave start <waves>` | 开启指定波数（1-30）的尸潮 |
| `/smarthorde wave stop` | 终止当前进行中的尸潮 |
| `/smarthorde wave info` | 查看当前尸潮的波次、状态与剩余怪物 |
| `/smarthorde summon-boss` | 召唤尸潮领主 |
| `/smarthorde summon-archer` | 召唤尸潮弓手 |
| `/smarthorde summon-brute` | 召唤尸潮蛮兵 |
| `/smarthorde leaderboard` | 查看本世界的尸潮战绩排行榜 |

## 配置说明

配置为服务端配置（SERVER），生成于存档的 `serverconfig/smarthorde-server.toml`，按分组组织：

- **general**：模组总开关。
- **difficulty.preset**：难度预设，支持配置热重载。
- **combat**：连招攻击、攻击前摇提示、阶段行为、闪避开关/概率/冷却、智能目标选择。
- **movement**：绕后包抄、维持攻击距离、开门、攀爬翻墙、叠罗汉翻墙。
- **horde**：夜间自动尸潮（含触发冷却）、维度白名单、夜晚判定时段、波次间隔（波次间倒计时同用此值）与基础规模、同屏怪物上限、生成点光照上限、波次来袭提示、近战/远程/绕后/指挥构成权重（远程/绕后权重受 `boss.enabled` 约束）。
- **effects**：粒子特效与模组音效总开关。
- **enhance**：原版怪增强开关及实体 id 白名单（白名单制，仅名单内原版实体被增强）。
- **boss**：Boss 总开关（同时约束波次中尸潮弓手/尸潮蛮兵的出现）、生命倍率、阶段切换血量阈值、切阶段召唤尸潮、Boss 血条显示。
- **performance**：重型 AI 决策节流间隔（同时控制瞄准/弹射物扫描与叠罗汉检测的缓存时长）、性能审计日志开关。

## 构建方法

环境要求：**JDK 21**（Minecraft 1.21.1 运行时自带 Java 21）。

```bash
# Windows
gradlew.bat build
# Linux / macOS
./gradlew build
```

构建产物位于 `build/libs/`。开发调试可用 `gradlew.bat runClient` 直接启动游戏客户端。

## 运行环境

- Minecraft：**1.21.1**
- 模组加载器：**NeoForge 21.1.244**（依赖版本范围 `[21.1.244,)`）
- Java：**21**

## 许可证

MIT License，详见 [LICENSE](LICENSE)。
