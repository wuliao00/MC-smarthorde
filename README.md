# SmartHorde（智潮尸潮）

Minecraft **1.21.1** / NeoForge **21.1.x** 模组：战术化智能僵尸、阶段化 Boss（四变体）、尸潮波次防守、原版僵尸注入、自绘音画资源、排行榜与成就。

## 功能总览

- **智能僵尸**：招式化攻击（轻击/重击/横扫三招状态机，前摇预警粒子）、三源闪避（受击/被弓瞄准/来袭箭矢）、绕侧包抄、距离管理、分离防挤团、攀爬/叠罗汉翻墙、智能目标选择（优先最近+残血+无甲玩家）
- **尸潮领主（4 变体）**：`brute` 蛮横（重击退）/ `plague` 瘟疫（剧毒+疮斑+更多仆从）/ `frost` 寒霜（减速+高护甲）/ `inferno` 炼狱（点燃+高攻速）；血量阈值切阶段、BossBar 按变体配色、切阶段召唤仆从（冷却 5 秒）
- **尸潮波次**：`/smarthorde wave start [waves]`，状态机 IDLE→COUNTDOWN→ACTIVE→BETWEEN_WAVES→COMPLETE，HUD 进度条 + 波间倒计时，清波奖励绿宝石/经验，通关大奖钻石
- **排行榜与成就**：`/smarthorde top` 排行榜（通关数+清波数排序在线玩家）、`/smarthorde stats` 个人战绩；6 个成就（击杀类数据驱动触发，波次/通关/噩梦类代码授予）
- **自绘资源**：5 个程序化合成的恐怖风 .ogg 音效（心跳+小二度音簇战号、低吼下坠、倒吸凉气、丧钟寒风等；Vorbis 44.1kHz 单声道）+ 5 张 64×64 自绘实体贴图（经典 humanoid UV），可用 `tools/gen_assets.py` 重新生成

## 环境要求

- JDK **21**（构建工具链已由 Gradle 自动解析，`foojay-resolver` 插件会按需下载）
- Gradle **8.9+**（若仓库中无 gradle wrapper，先执行 `gradle wrapper --gradle-version 8.9` 生成，或直接使用本机安装的 Gradle）

> 本代码包不含 gradle wrapper 二进制（`gradle/wrapper/gradle-wrapper.jar`），
> 请用上述命令生成后即可使用 `./gradlew build`（Windows 为 `gradlew.bat build`）。

## 构建与安装

```bash
./gradlew build
# 产物：build/libs/smarthorde-1.0.0.jar
```

将 jar 复制到 `.minecraft/mods/`，**删除 Sodium**（避免实体渲染冲突），启动游戏。

### 游戏内验证

```text
# 1. mod 加载确认
/smarthorde difficulty

# 2. 手动召唤（验证渲染 + AI）
/smarthorde summon 5

# 3. Boss 召唤（验证 BossBar + 阶段 + 变体换肤；不带参数随机变体）
/smarthorde summon-boss
/smarthorde summon-boss frost

# 4. 尸潮（验证 HUD + 波次 + 成就授予）
/smarthorde wave start 3

# 5. 排行榜与个人统计
/smarthorde top
/smarthorde stats

# 6. 原版注入（夜晚自然生成即可观察，无需命令）
```

全部命令需要权限等级 2（OP）。

## 故障排查（FAQ）

### 看不到自然生成？

按以下顺序检查：

1. **游戏难度必须非和平**：怪物类实体在和平难度一律不生成。
2. **时间与光照**：智能僵尸沿用原版僵尸的生成规则 —— 主世界亮度 ≤ 7 才会生成，白天主要在洞穴、夜晚在露天出现。用 F3 调试屏看脚下 Light 值。
3. **生成权重**：生物群系修改器对所有主世界群系注册 weight=100、每组 1~4 只，与原版僵尸同池竞争；如果地形上其他敌对生物密集，MONSTER 刷新上限可能被占满。
4. **快速验证**：直接 `/smarthorde summon 5`（近距环形）或 `/smarthorde wave start 3`（24~32 格环形），先确认实体本身正常再谈自然生成。
5. **Sodium**：请移除 Sodium，它会对自定义实体的模型层/渲染层产生冲突。
6. **数据包加载**：本 mod 的自然生成走 `data/smarthorde/neoforge/biome_modifier/` 数据驱动文件；若你手动改过 world datapacks 或禁用了 mod 数据包，需要重新启用并重启存档。

### 亡灵杀手（Smite）打不出额外伤害？

已在 v1.0.1 修复：1.21 的附魔是数据驱动的，Smite 只对 `#minecraft:sensitive_to_smite` 实体标签生效，而自定义实体类型不会自动进入该标签。现在通过 `data/minecraft/tags/entity_type/sensitive_to_smite.json` 把 `smarthorde:smart_zombie` 与 `smarthorde:horde_boss` 加入其中。

### 原版僵尸好像没被增强？

增强包含两层：属性层（血量 ×1.5、速度 ×1.15，变化相对 subtle）和**行为层** —— 夜晚遇到的原版僵尸现在也会闪避箭矢、绕侧包抄、保持距离、被弓箭手瞄准时横向侧跳，行为差异非常明显。启动日志首次注入时会输出一行：

```text
[SmartHorde] Vanilla zombie enhanced: hp x1.5, speed x1.15 + tactical AI goals installed
```

见不到这行说明：`inject.vanillaMobs` 配置被关了，或者进服时还没有原版僵尸进入世界（可等到夜晚）。已存在的僵尸只注入一次（NBT 标记防重复）；旧存档中早已加载的僵尸会在区块重新读取后获得增强。

## 与《SmartHorde 完整文件清单》的偏差说明

清单是设计蓝图，以下按 1.21.1 实际 API 做了必要调整：

| 清单原文 | 实现方式 | 原因 |
|---|---|---|
| `SmartZombie 继承 Monster` | 继承 `Zombie`（间接仍是 Monster） | 清单要求复用 `ZombieRenderer`，该渲染器要求实体为 `Zombie` 实例，否则运行时 ClassCastException |
| `data/.../loot_tables/`、`recipes/` | `loot_table/`、`recipe/`（单数） | 1.21+ 数据包格式（pack format 48）目录已改名，复数目录不会加载 |
| `AddSpawnEvent 或 BiomeLoadingEvent 添加生成规则` | `data/smarthorde/neoforge/biome_modifier/smart_zombie_spawns.json`（`neoforge:add_spawns`，weight=100，每组 1~4 只） | 1.21.1 推荐数据驱动方式，效果等价且无需代码 |
| DodgeGoal "冷却从配置读取" | 新增配置键 `ai.dodgeCooldownTicks`（默认 90） | 清单默认值速查表未包含该键，但实现要求从配置读取 |
| 配方"改名智潮头盔/智潮之刃" | 配方 result 使用 `minecraft:custom_name` 组件 | 1.20.5+ 物品组件原生支持，纯 JSON 即可改名 |

其他说明：

- **音效已自绘**：`assets/smarthorde/sounds/*.ogg` 为程序化合成的 Vorbis 音频（44.1kHz 单声道），`sounds.json` 指向 `smarthorde:` 自有文件；想换风格可替换 .ogg 或改 `tools/gen_assets.py` 参数后重新运行。
- **渲染已使用专属渲染器**：`SmartZombieRenderer`/`HordeBossRenderer` 基于僵尸姿态模型（含内外盔甲层），配自绘贴图；Boss 按 1.6 倍缩放贴合 1.2×3.2 碰撞箱，四变体各自换肤。
- **成就/排行榜数据**：统计存于玩家 PersistentData（随世界存档持久化）；排行榜仅统计在线玩家。波次/通关/噩梦成就在对应时机由代码授予，击杀类成就由原版 `player_killed_entity` 触发器数据驱动完成。
- **难度命令**：`/smarthorde difficulty <preset>` 为运行时覆盖，不回写配置文件，配置重载后恢复。
- **版本号**：`gradle.properties` 中 `neo_version=21.1.77`、`build.gradle` 中 ModDevGradle `1.0.21`，如解析失败请到 maven.neoforged.net 查询最新 21.1.x 版本后替换。

## 目录结构

与《SmartHorde 完整文件清单》一致，另增：

- `src/main/java/dev/smarthorde/client/renderer/`（专属渲染器 ×2）
- `src/main/java/dev/smarthorde/stats/HordeStats.java`（统计/排行榜/成就授予）
- `src/main/resources/assets/smarthorde/sounds/*.ogg`、`textures/entity/*.png`（自绘资源）
- `src/main/resources/data/smarthorde/advancement/*.json`（成就 ×6）
- `src/main/resources/data/smarthorde/neoforge/biome_modifier/smart_zombie_spawns.json`（自然生成，见上表）
- `tools/gen_assets.py`（音效/贴图程序化生成脚本）、`README.md`（本文件）

## 配置速查（config/smarthorde-common.toml）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| difficulty.preset | normal | 难度预设 |
| inject.vanillaMobs | true | 增强原版僵尸 |
| boss.enabled | true | 允许 Boss 召唤 |
| boss.bossBarEnabled | true | 显示 BossBar |
| boss.healthMultiplier | 1.0 | Boss 血量倍率 |
| boss.summonsHordeOnPhase | true | 切阶段召唤仆从 |
| boss.phaseThresholds | [0.75, 0.5, 0.25] | 阶段切换阈值 |
| horde.enabled | true | 尸潮系统开关 |
| horde.baseCount | 7 | 首波怪物数量 |
| horde.countPerWave | 3 | 每波递增数量 |
| horde.countdownSeconds | 5 | 波间准备时间 |
| effects.particlesEnabled | true | 粒子特效开关 |
| effects.soundsEnabled | true | 音效开关 |
| effects.headHealthBar | true | 头顶血量条开关 |
| performance.auditEnabled | false | 性能审计（60 秒一次，输出到日志） |
| performance.maxParticles | 48 | 单次粒子上限 |
| ai.dodgeCooldownTicks | 90 | 闪避基础冷却（tick，受难度影响） |
