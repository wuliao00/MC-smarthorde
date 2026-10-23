# Changelog

所有显著变更记录于此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循语义化版本。

## [1.1.0]

### 新增

- **多部件立体 3D 建模与全亮度自发光图层**（`ModModelLayers` / `SmartZombieModel` / `HordeBossModel` / `EmissiveGlowLayer`）：
  - 注册独立 `128×128` 骨骼网格 `LayerDefinition`（两套模型合计 **82 个独立立体几何块 / 492 个零重叠 UV 面**），彻底告别原版 6 方块火柴盒模型；
  - **五大智尸兵种全覆盖 3D 部件（39 独立几何体）**：深陷眉弓、战术腰封、双臂护腕、双腿立体护膝胫甲，**标准斥候（`standard`）镂空顶战术头箍+发光侦测镜+左肩轻甲+战术背囊**，以及突袭者镂空兜帽+随风飘带+发光双袖刃、重装先锋条顿面罩头盔+顶冠+重胸甲+护腰裙甲+层叠肩甲、狂血战鬼双弯骨角+尖刺护肩+**双臂对称血骨刃**+背脊骨刺、百夫长鎏金锯齿战冠+横羽冠饰+统帅胸甲+**古罗马分片鎏金护腰战裙（`commander_pteruges`）**+动态飘动披风；
  - **尸潮领主泰坦巨兽 3D 骨架（43 独立几何体）**：11 宽倒三角呼吸起伏魁梧胸腔 + **凸出式脉动元素胸核（`chest_core`）** + **立体兽颅发光腰带扣（`belt_buckle_skull`）** + **背部重装护脊甲板（`dorsal_plate`）** + 宽肩距（±7.0）巨型粉碎铁拳套（含外侧装甲刃脊与掌心握持对齐 `translateToHand`）+ 随迈腿前摆防穿模的铰接护胯战裙 + 护膝胫甲 + **可动怒吼下颚（`jaw`）**，以及四变体独立立体造型（蛮横巨牛角+肩刺、瘟疫双叉腐角+呼吸脉动孢囊、寒霜五塔冰晶王冠+双肩冰棱、炼狱后掠熔岩魔角+烈焰顶冠+背部熔炉排口）；
  - **全亮度自发光图层（`_glow.png`）**：瞳孔、目镜、斥候侦测镜、武器符文、腰扣兽目与领主胸核在黑夜与洞穴中全亮发光，并随狂暴/战吼/Boss 阶段实时脉动。
- **骨骼关键帧动画与接地/乘骑/穿甲防穿模引擎**（`PostureClamp` / `HordeBossModel#copyPropertiesTo`）：
  - 60fps 正序关键帧驱动轻击、重击、横扫、突刺、攀爬、闪避、举盾推进、统领战吼与 Boss 震地施法；
  - 接地刚体运动学耦合：弯腰时上身枢轴自然下沉 `12*(1-cosθ)` 而双脚锁死踩实地面（`y=24`）不浮空，乘船/矿车坐姿自动保留原版 `-81°` 抬腿防止双腿穿透船底，副手持盾时锁止左臂过顶动作防止盾牌穿颅，领主穿戴外部盔甲时自动等比外扩包裹 11 宽泰坦胸腔与巨拳，杜绝裸露穿模。
- **四大精英战术兵种**（`EliteRole`，附独立 `128×128` 立体像素贴图与发光贴图）：
  - `raider`（疾风突袭者）：高机动侧翼切入，短闪避冷却，偏好轻击与突刺；
  - `vanguard`（重装先锋）：高甲高血，副手持盾稳步推进格挡正面箭矢与近战（玩家持斧劈砍可触发 5 秒破盾硬直）；
  - `berserker`（狂血战鬼）：半血以下进入嗜血狂暴，移速与出招频率激增；
  - `commander`（尸潮百夫长）：周期性振臂发出战吼（`war_cry.ogg`），赋予周围友军迅捷与力量增益。
- **尸潮领主变体专属主动战技与反垫高突进**（`BossAbilityGoal`，零方块破坏）：蛮横「震地咆哮」（跳跃可规避半伤）、瘟疫「疫毒迷雾」、寒霜「凛冬霜环」、炼狱「烈焰新星」；面对垫方块逃课（2~5 格高柱）或中距风筝玩家时，蓄力期自动触发碰撞安全校验的「泰坦跃击」与纵向震波。
- **专属智潮战术装备、生存战号、创造刷怪蛋与图形化配置界面**（`ModItems` / `SmartHelmetItem` / `SmartBladeItem` / `HordeHornItem` / `TacticalGearEvents` / `SmartHordeClient`）：
  - **智潮战术头盔（`smarthorde:smart_helmet`）**：独立 `16×16` 图标与 `64×32` 玩家 3D 穿戴装甲贴图（立体绿宝石战术目镜），穿戴后减免来自智尸与领主的 **15% 伤害**，并每 2 秒自动用发光轮廓标出 16 格内潜行绕后的疾风突袭者与视线遮挡伏兵；
  - **智潮破阵之刃（`smarthorde:smart_sword`）**：独立 `16×16` 手持利刃贴图，对精英智尸与尸潮领主造成 **+25% 额外伤害**，且命中举盾重装先锋时可直接击破其盾防 **3.5 秒**；
  - **智潮战号（`smarthorde:horde_horn`）**：生存可合成或由领主战利品掉落，长按右键吹响在无管理员权限的生存模式发起尸潮试炼，潜行+右键查看当前波次进度；
  - 新增 `smart_zombie_spawn_egg` 与 `horde_boss_spawn_egg` 创造模式刷怪蛋；
  - 注册 NeoForge 原生图形化配置界面 `ConfigurationScreen`（主菜单 Mods → SmartHorde → Config），并向普通生存玩家开放只读查询命令（`/smarthorde stats`、`top`、`status`、`wave info`、`difficulty`）。
- **里程碑决战波与防卡关残敌信标**：每第 5 波（或 5+ 波会话最终决战波）自动刷出一位尸潮领主统帅参战；波次怪开启防远程脱战自然消失保护，并在交战逾 30 秒且仅剩 ≤3 只残敌时自动施加发光轮廓，杜绝洞穴残敌卡死波次。
- **命令、三语本地化与 UI 扩展**：新增 `/smarthorde summon <count> <role>`、`/smarthorde wave skip`、`/smarthorde clear`、`/smarthorde status`；补齐俄语本地化 `ru_ru.json`（中/英/俄各 79 键 100% 对齐）；波次 HUD 自动感知并避让多个活跃 BossBar；头顶血量条新增掉血缓冲残影与精英色彩徽记；实体战利品表支持抢夺（`looting`）附魔加成；新增成就 `精锐猎手`（`elite_hunter`）。

### 修复与优化

- **修复玩家阵亡重生或从末地返回后战绩清零的持久化 Bug**（`HordeStats`）：监听 `PlayerEvent.Clone` 将 `smarthorde_stats` 从旧玩家实体完整迁移至新实体，确保个人战绩与排行榜分数跨重生/跨维度永久保留。
- **修复带 NBT `/summon` 实体漏挂难度与兵种属性、侧步撞墙穿模及脱战举手残留 Bug**：`SmartZombie` 与 `HordeBoss` 在首个服务端 tick 自动兜底完成属性与兵种装备初始化（兼容跳过 `finalizeSpawn` 的原版 NBT `/summon`）；`SeparationGoal`、`MaintainDistanceGoal` 与 `FlankGoal` 全面补齐侧向碰撞净空、2 格头顶高度、流体与悬崖落差检测，杜绝走廊混战时肩膀挤压进墙面或跌落深渊；`PeacefulStrollGoal` 在和平漫步启动时自动复位残留的 `Aggressive` 举臂姿态。
- **封堵阵亡远端重生/区块卸载误判通关白嫖钻石漏洞**：`HordeWaveManager` 改用待击败实体集合追踪（仅实体在已加载区块明确阵亡/移除时扣减，区块卸载绝不误判为清波），新增玩家阵亡判负机制（`horde.failOnDeath`）与远离战场 >144 格自动终止清理机制，并在关服/客户端退服时自动清空残留会话与 HUD 缓存。
- **修复内嵌门铰链漏检、1.21 新木门/铜门打不开与乘船穿模问题**：`ClimbOrStackGoal` 同时检测自身所在格与前方格的双层门方块，并改用 `BlockSetType#canOpenByHand()` 支持樱花木、竹木、下界菌木与铜门（铁门依旧无法徒手推开）；`SmartZombieModel` 与 `HordeBossModel` 在乘船/矿车时自动保留坐姿抬腿角度防止双腿刺穿船底。

- **修复近战追击死区发呆、交战散步与客户端警戒闪跳**：消除 `SmartMeleeAttackGoal` 在 3.3~3.5 格区间的起手死区与每 tick 重复 A* 重算；新增 `PeacefulStrollGoal` 禁止交战中随机漫步；服务端持续同步 `isAggressive` 彻底消除客户端手臂闪跳与扭头穿模；补齐智尸 8 tick 战术侧扑闪避收身动画；修复挥砍判定仅命中玩家导致对铁傀儡/村民零伤害的 Bug。
- **修复 MC 1.21.1 配方文本组件解析异常、亡灵标签缺失与元素领主自伤 Bug**：将 `smart_helmet.json` / `smart_sword.json` 升级为符合 1.21.1 `FLAT_CODEC` 规范的可本地化 `minecraft:item_name` 组件（修复开服配方加载 `JsonSyntaxException`）；新增 `data/minecraft/tags/entity_type/zombies.json` 使智尸与领主完整继承 1.21.1 亡灵药水反转、免疫剧毒（不再被瘟疫领主毒雾误伤）、水下免溺水与凋灵友军机制；炼狱领主新增火焰/岩浆免疫，寒霜领主新增细雪冻伤免疫。
- **修复重装先锋原版双层免伤与爬墙悬空 Bug**：举盾改由纯自定义同步驱动，消除原版 `startUsingItem` 叠加 100% 免伤及 80% 减速的缺陷；`ClimbOrStackGoal` 支持圆石墙/栅栏/楼梯攀爬，检测头顶屋檐净空，并在被击退脱离墙面后立即终止攀爬防止半空悬停。
- **修复洞穴/室内波次刷到地表、间接击杀漏计与配置生命周期异常**：`HordeWaveSpawner` 优先在玩家同层高度搜寻安全地板并在狭窄洞穴自动收缩半径；修复 `HordeWaveManager` 生成失败时无限重置重试计数刷屏或误发通关钻石的 Bug；`HordeStats` 补齐 `getKillCredit()` 间接击杀（地形击落/火焰）归属统计；全工程服务端与客户端路径统一接入 `SmartHordeConfig.SPEC.isLoaded()` 安全守卫。

## [1.0.1]

### 修复

- 亡灵杀手（Smite）附魔对 `smarthorde:smart_zombie` / `smarthorde:horde_boss` 不生效：1.21 附魔为数据驱动，仅对 `#minecraft:sensitive_to_smite` 标签内实体生效；现已通过 `data/minecraft/tags/entity_type/sensitive_to_smite.json` 将两类实体加入该标签。

## [1.0.0]

### 新增

- 战术化智能僵尸：招式化攻击（轻击/重击/横扫，前摇预警粒子）、三源闪避（受击/被弓瞄准/来袭箭矢）、绕侧包抄、距离管理、分离防挤团、攀爬/叠罗汉翻墙、智能目标选择（优先最近 + 残血 + 无甲玩家）。
- 尸潮领主（4 变体）：`brute` / `plague` / `frost` / `inferno`，血量阈值切阶段、BossBar 按变体配色、切阶段召唤仆从。
- 尸潮波次防守：`/smarthorde wave start [waves]`，状态机驱动的多波进攻、HUD 进度条与波间倒计时、清波奖励与通关大奖。
- 排行榜与成就：`/smarthorde top` / `/smarthorde stats`，6 个成就（击杀类数据驱动 + 波次/通关/噩梦类代码授予）。
- 原版僵尸注入：属性强化（血量 ×1.5、速度 ×1.15）+ 战术 AI 注入，NBT 标记防重复。
- 自绘资源：5 个程序化合成 .ogg 音效 + 5 张 64×64 实体贴图，附 `tools/gen_assets.py` 再生成脚本。
- 难度系统：四档预设（easy / normal / hard / nightmare），`/smarthorde difficulty` 运行时覆盖。
