# SmartHorde —— 让怪物不再"傻站着挨打"

**让怪物不再"傻站着挨打"，而是像真正的敌人一样思考、协作、翻墙、闪避、包抄你。**

SmartHorde 是一个面向 Minecraft 1.21.1（NeoForge）的怪物 AI 增强模组。它为僵尸类怪物注入了一套完整的攻防走位 AI 体系，并提供了尸潮波次系统、Boss 战和难度分级。

## 核心特性

### 智能僵尸（SmartZombie）

一只拥有完整战术 AI 的僵尸，具备以下行为：

- **招式化攻击**：轻击、横扫、重击、突刺四种招式，每招有独立的前摇蓄力、攻击范围、扇形角度和冷却时间
- **绕侧包抄**：正面面对时会向你的侧后方移动，迫使你转身或暴露背面
- **距离管理**：太近会后退到舒适距离再出招；被风筝时会斜向切入而非直线追
- **分离防挤团**：多只怪不会叠在一起，自动散开形成包围圈
- **翻墙系统**：蜘蛛式贴墙攀爬任意高墙（上限 6 格，单只即可独立翻越），遇到木门会自动打开
- **三源闪避**：受击后闪避、被弓瞄准时侧跳、被准心直指（近战贴脸瞄准同样触发）时侧跳、检测箭矢飞来时冲刺闪避（每次闪避有冷却）
- **智能选目标**：优先攻击距离最近、血量最低、无护甲的玩家

### 原版僵尸增强（默认开启）

安装即用——所有自然生成的原版僵尸自动获得上述战术 AI，无需额外配置。首次注入时日志会输出 `[SmartHorde] Vanilla zombie enhanced...` 确认生效。

### 尸潮波次系统

一键开启生存压力测试：

- `/smarthorde wave start 5` 启动 5 波尸潮
- 波次递进：第 1 波 7 只，第 2 波 10 只，第 3 波 13 只……越往后越多
- 环形生成：怪物在你周围 24~32 格环形区域刷出，不会卡墙不会悬空
- 每波之间有 5 秒准备时间，聊天栏实时倒计时
- 清波奖励绿宝石+经验，全部通关给钻石大奖
- 屏幕顶部 HUD 实时显示波次进度条、存活数、阶段状态

### Boss：尸潮领主

- 200 血量 / 10 伤害 / 0.8 击退抗性 / 8 护甲
- 血量降至 75%/50%/25% 时自动进入下一阶段（血条颜色：白→蓝→紫→红）
- 每阶段攻击速度 +25%、移动速度加快
- 切阶段时召唤仆从小尸潮（6/8/10 只）
- `/smarthorde summon-boss` 召唤（不带参数随机变体：蛮横/瘟疫/寒霜/炼狱）

### 四档难度

| 难度 | 生命 | 伤害 | 速度 | 攻速 | 闪避冷却 |
|------|------|------|------|------|----------|
| Easy | ×0.75 | ×0.70 | ×0.90 | ×0.75 | 2.25s |
| Normal | ×1.00 | ×1.00 | ×1.00 | ×1.00 | 1.5s |
| Hard | ×1.35 | ×1.25 | ×1.10 | ×1.25 | 1.1s |
| Nightmare | ×1.75 | ×1.50 | ×1.25 | ×1.50 | 0.7s |

`/smarthorde difficulty nightmare` 切换；修改 `smarthorde-server.toml` 后由 NeoForge 内建文件监视器热重载。

## 音效与特效

- 攻击前摇：喉音咆哮 + 骨裂脆响
- 闪避：倒吸凉气骤停
- 尸潮来袭：心跳渐密 + 小二度不协和战号 + 耳语声浪 + 高频诡异滑音
- Boss 狂暴：轰击 + 音调坠落的低吼 + 金属尖啸 + 次声尾巴
- 清波：丧钟余韵 + 寒风退散
- 头顶血量条：打残的怪头顶显示绿→红渐变血条，满血隐藏

全部特效和音效可通过配置独立开关。

## 命令一览

| 命令 | 说明 |
|------|------|
| `/smarthorde difficulty [preset]` | 切换难度（easy/normal/hard/nightmare） |
| `/smarthorde summon [count]` | 手动召唤指定数量的 SmartZombie |
| `/smarthorde summon-boss [variant]` | 召唤尸潮领主（可指定 brute/plague/frost/inferno） |
| `/smarthorde wave start [waves]` | 启动指定波数的尸潮 |
| `/smarthorde wave stop` | 终止当前尸潮 |
| `/smarthorde wave info` | 查看当前波次信息 |
| `/smarthorde top` | 尸潮排行榜（在线玩家） |
| `/smarthorde stats` | 个人战绩统计 |

## 配置

配置文件位于 `config/smarthorde-server.toml`，主要选项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `inject.vanillaMobs` | true | 增强原版僵尸（默认开启，开箱即用） |
| `difficulty.preset` | normal | 难度预设 |
| `boss.enabled` | true | 允许 Boss 召唤 |
| `boss.phaseThresholds` | [0.75, 0.5, 0.25] | 阶段切换阈值 |
| `horde.baseCount` | 7 | 首波怪物数量 |
| `horde.countPerWave` | 3 | 每波递增数量 |
| `effects.particlesEnabled` | true | 粒子特效开关 |
| `effects.soundsEnabled` | true | 音效开关 |
| `effects.headHealthBar` | true | 头顶血量条开关 |
| `performance.auditEnabled` | false | 性能审计日志（默认关闭） |

## 故障排查

如果自然生成不生效，请依次检查：

1. **难度不能是和平模式**——和平模式下怪物不会生成
2. **亮度必须 ≤ 7**——SmartZombie 只在夜晚或黑暗处生成（洞穴、室内）
3. **MONSTER 上限未被占满**——如果周围已有大量怪物，新怪物不会刷出
4. **移除 Sodium**——Sodium 与部分实体渲染存在兼容性问题
5. **先用命令验证**——执行 `/smarthorde summon 5`，按 F3+B 查看碰撞箱，排除生成规则问题

### 启动时卡在早期窗口并报 "Timed out trying to setup the Game Window"？

这是 NeoForge 早期显示窗口在老旧显卡驱动上创建 OpenGL 上下文超时（Intel HD 2000/2500 等仅支持 GL 4.0 的核显尤其常见），与本模组无关。两步解决：

1. 移除 Sodium 系模组（它们挂载在启动引导阶段且对老 GL 硬件不友好）；
2. 编辑实例目录下 `config/fml.toml`，把 `earlyWindowControl = true` 改为 `false`，跳过早期显示窗口，由游戏本体正常创建窗口。

## 设计原则

- **零方块操作**：全程不破坏/不放置任何方块，不污染世界
- **配置驱动**：几乎所有数值和行为都可通过配置文件调整
- **性能优先**：所有 AI Goal 有冷却节流，粒子数量封顶，40 只同屏 TPS ≥ 18
- **渐进增强**：每个功能模块独立开关，可以只用其中一部分
- **开箱即用**：默认配置即可体验全部核心功能，无需手动调整

## 兼容性

- Minecraft 1.21.1
- NeoForge 21.1.x
- 与原版僵尸完全兼容（注入增强而非替换）
- 不修改任何原版方块或物品

## 开发与构建

```bash
# 需要 JDK 21；仓库不含 gradle wrapper，先安装 Gradle 8.9+
gradle wrapper --gradle-version 8.9
./gradlew build          # 产物 build/libs/smarthorde-<version>.jar
python tools/gen_assets.py   # 可选：重新生成音效/贴图（pip install soundfile pillow numpy）
```

## 许可

MIT License。源代码：[GitHub](https://github.com/wuliao00/MC-smarthorde)
