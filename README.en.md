# SmartHorde — Make Mobs Stop "Standing Still and Taking Hits"

[简体中文](README.md) | [English](README.en.md) | [Русский](README.ru.md)

**Make mobs stop "standing still and taking hits" — instead they think, cooperate, climb walls, dodge, and flank you like real enemies.**

SmartHorde is a mob AI enhancement mod for Minecraft 1.21.1 (NeoForge). It injects a complete combat-and-positioning AI system into zombie-type mobs, and adds a horde wave system, boss fights, and difficulty tiers.

## Core Features

### Smart Zombie (SmartZombie)

A zombie with a full tactical AI, capable of:

- **Combo attacks**: four moves — light hit, sweep, heavy strike, and thrust — each with its own wind-up, range, arc angle, and cooldown
- **Flanking**: when facing you head-on, it moves toward your sides and rear, forcing you to turn or expose your back
- **Distance management**: backs off to a comfortable range when too close before attacking; cuts in diagonally instead of chasing in a straight line when kited
- **Separation**: multiple mobs never stack on the same spot — they spread out to form an encirclement
- **Wall climbing**: spider-style wall climbing up any wall (up to 6 blocks; a single mob can climb independently) and automatically opens wooden doors
- **Three-source dodging**: dodge after being hit, sidestep when aimed at with a bow, sidestep when your crosshair points at it (melee point-blank aiming triggers this too), and dash-dodge when an incoming arrow is detected (each dodge has a cooldown)
- **Smart target selection**: prioritizes the nearest player with the lowest health and no armor

### Vanilla Zombie Enhancement (enabled by default)

Works out of the box — all naturally spawned vanilla zombies automatically gain the tactical AI above, no configuration needed. On first injection the log prints `[SmartHorde] Vanilla zombie enhanced...` to confirm it's active.

### Horde Wave System

One command to start a survival stress test:

- `/smarthorde wave start 5` starts a 5-wave horde
- Scaling waves: wave 1 spawns 7 mobs, wave 2 spawns 10, wave 3 spawns 13... more and more as it goes
- Ring spawning: mobs spawn in a ring 24–32 blocks around you — never stuck in walls or floating in the air
- 5 seconds of prep time between waves, with a live countdown in chat
- Clearing a wave rewards emeralds + XP; clearing them all wins a diamond jackpot
- A top-screen HUD shows live wave progress, remaining mobs, and phase status

### Boss: Horde Lord

- 200 HP / 10 damage / 0.8 knockback resistance / 8 armor
- Automatically enters the next phase at 75%/50%/25% HP (boss bar color: white → blue → purple → red)
- Each phase grants +25% attack speed and faster movement
- Phase transitions summon minion mini-hordes (6/8/10 mobs)
- `/smarthorde summon-boss` summons it (no argument gives a random variant: Brute / Plague / Frost / Inferno)

### Four Difficulty Tiers

| Difficulty | HP | Damage | Speed | Attack Speed | Dodge Cooldown |
|------|------|------|------|------|----------|
| Easy | ×0.75 | ×0.70 | ×0.90 | ×0.75 | 2.25s |
| Normal | ×1.00 | ×1.00 | ×1.00 | ×1.00 | 1.5s |
| Hard | ×1.35 | ×1.25 | ×1.10 | ×1.25 | 1.1s |
| Nightmare | ×1.75 | ×1.50 | ×1.25 | ×1.50 | 0.7s |

Switch with `/smarthorde difficulty nightmare`; edits to `smarthorde-server.toml` hot-reload via NeoForge's built-in file watcher.

## Sounds & Effects

- Attack wind-up: guttural roar + bone-crack crunch
- Dodge: sharp gasp and stop
- Incoming horde: accelerating heartbeat + minor-second dissonant war horn + whispering swell + eerie high-frequency glissando
- Boss enrage: rumble + falling-pitch growl + metal shriek + infrasound tail
- Wave cleared: funeral bell decay + retreating cold wind
- Overhead health bar: wounded mobs show a green→red gradient bar above their heads, hidden at full health

All effects and sounds can be toggled independently in the config.

## Command Reference

| Command | Description |
|------|------|
| `/smarthorde difficulty [preset]` | Switch difficulty (easy/normal/hard/nightmare) |
| `/smarthorde summon [count]` | Manually summon a given number of SmartZombies |
| `/smarthorde summon-boss [variant]` | Summon the Horde Lord (brute/plague/frost/inferno) |
| `/smarthorde wave start [waves]` | Start a horde with the given number of waves |
| `/smarthorde wave stop` | Stop the current horde |
| `/smarthorde wave info` | Show current wave info |
| `/smarthorde top` | Horde leaderboard (online players) |
| `/smarthorde stats` | Personal combat statistics |

## Configuration

The config file lives at `config/smarthorde-server.toml`. Key options:

| Option | Default | Description |
|--------|--------|------|
| `inject.vanillaMobs` | true | Enhance vanilla zombies (on by default, works out of the box) |
| `difficulty.preset` | normal | Difficulty preset |
| `boss.enabled` | true | Allow boss summoning |
| `boss.phaseThresholds` | [0.75, 0.5, 0.25] | Phase transition thresholds |
| `horde.baseCount` | 7 | Mob count in the first wave |
| `horde.countPerWave` | 3 | Increase per wave |
| `effects.particlesEnabled` | true | Particle effects toggle |
| `effects.soundsEnabled` | true | Sound effects toggle |
| `effects.headHealthBar` | true | Overhead health bar toggle |
| `performance.auditEnabled` | false | Performance audit logging (off by default) |

## Troubleshooting

If natural spawning doesn't work, check in order:

1. **Difficulty must not be Peaceful** — mobs don't spawn in Peaceful mode
2. **Light level must be ≤ 7** — SmartZombies only spawn at night or in darkness (caves, interiors)
3. **The MONSTER cap must not be full** — if there are already many mobs nearby, new ones won't spawn
4. **Remove Sodium** — Sodium has compatibility issues with some entity rendering
5. **Verify with a command first** — run `/smarthorde summon 5` and press F3+B to see hitboxes, ruling out spawn-rule issues

### Stuck at the early window on startup with "Timed out trying to setup the Game Window"?

This is the NeoForge early display window timing out while creating an OpenGL context on old GPU drivers (especially common on integrated graphics like Intel HD 2000/2500 that only support GL 4.0) — it is unrelated to this mod. Two steps fix it:

1. Remove Sodium-family mods (they hook into the boot stage and are unfriendly to old GL hardware);
2. Edit `config/fml.toml` in your instance directory, change `earlyWindowControl = true` to `false` to skip the early display window and let the game itself create the window normally.

## Design Principles

- **Zero block manipulation**: never breaks or places any blocks, keeps your world clean
- **Config-driven**: nearly all values and behaviors are adjustable via the config file
- **Performance first**: every AI Goal is cooldown-throttled, particle count is capped, TPS ≥ 18 with 40 mobs on screen
- **Incremental enhancement**: each feature module has its own toggle — use only what you want
- **Works out of the box**: the default config lets you experience all core features with no manual tuning

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.x
- Fully compatible with vanilla zombies (enhancement injection, not replacement)
- Does not modify any vanilla blocks or items

## Development & Build

```bash
# Requires JDK 21; the repo has no gradle wrapper, so install Gradle 8.9+ first
gradle wrapper --gradle-version 8.9
./gradlew build          # output: build/libs/smarthorde-<version>.jar
python tools/gen_assets.py   # optional: regenerate sounds/textures (pip install soundfile pillow numpy)
```

## License

MIT License. Source code: [GitHub](https://github.com/wuliao00/MC-smarthorde)
