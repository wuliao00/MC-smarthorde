# Changelog

所有显著变更记录于此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循语义化版本。

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
