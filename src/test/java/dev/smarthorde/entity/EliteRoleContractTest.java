package dev.smarthorde.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精英战术兵种数据契约、128x128 主贴图/发光贴图尺寸、亡灵标签、1.21.1 配方文本组件与三语本地化资源单元测试。
 */
class EliteRoleContractTest {

    @Test
    void allRolesAndBossVariantsHaveValidStatsAndExisting128x128TexturePairs() throws IOException {
        for (EliteRole role : EliteRole.values()) {
            assertTrue(role.healthMul > 0.0, role + " healthMul must be positive");
            assertTrue(role.damageMul > 0.0, role + " damageMul must be positive");
            assertTrue(role.speedMul > 0.0, role + " speedMul must be positive");
            assertTrue(role.dodgeCooldownMul > 0.0, role + " dodgeCooldownMul must be positive");
            assertTrue(role.modelScale >= 0.8F && role.modelScale <= 1.3F, role + " modelScale out of bounds");

            Path textureFile = Path.of("src", "main", "resources", "assets", "smarthorde", role.texturePath());
            Path glowFile = Path.of("src", "main", "resources", "assets", "smarthorde", role.glowTexturePath());
            assertPng128x128(textureFile);
            assertPng128x128(glowFile);
        }

        for (String bossVariant : new String[]{"brute", "plague", "frost", "inferno"}) {
            Path bossTex = Path.of("src", "main", "resources", "assets", "smarthorde",
                    "textures", "entity", "horde_boss_" + bossVariant + ".png");
            Path bossGlow = Path.of("src", "main", "resources", "assets", "smarthorde",
                    "textures", "entity", "horde_boss_" + bossVariant + "_glow.png");
            assertPng128x128(bossTex);
            assertPng128x128(bossGlow);
        }
    }

    @Test
    void undeadTagsRecipesAndTrilingualLangFilesRemainValid() throws IOException {
        for (String tagName : new String[]{"zombies.json", "sensitive_to_smite.json"}) {
            Path tagPath = Path.of("src", "main", "resources", "data", "minecraft",
                    "tags", "entity_type", tagName);
            assertTrue(Files.isRegularFile(tagPath), "Missing entity tag: " + tagPath);
            String json = Files.readString(tagPath);
            assertTrue(json.contains("smarthorde:smart_zombie"), tagName + " must include smart_zombie");
            assertTrue(json.contains("smarthorde:horde_boss"), tagName + " must include horde_boss");
        }

        for (String recipeName : new String[]{"smart_helmet.json", "smart_sword.json", "horde_horn.json"}) {
            Path recipePath = Path.of("src", "main", "resources", "data", "smarthorde", "recipe", recipeName);
            assertTrue(Files.isRegularFile(recipePath), "Missing recipe: " + recipePath);
            String json = Files.readString(recipePath);
            String expectedItemId = "smarthorde:" + recipeName.replace(".json", "");
            assertTrue(json.contains(expectedItemId),
                    recipeName + " must craft registered mod item " + expectedItemId);
        }

        for (String itemTex : new String[]{"horde_horn.png", "smart_helmet.png", "smart_sword.png"}) {
            Path texPath = Path.of("src", "main", "resources", "assets", "smarthorde",
                    "textures", "item", itemTex);
            assertPngDimensions(texPath, 16, 16);
        }
        Path armorLayerTex = Path.of("src", "main", "resources", "assets", "smarthorde",
                "textures", "models", "armor", "smart_tactical_layer_1.png");
        assertPngDimensions(armorLayerTex, 64, 32);

        for (String modelName : new String[]{
                "smart_zombie_spawn_egg.json", "horde_boss_spawn_egg.json",
                "horde_horn.json", "smart_helmet.json", "smart_sword.json"}) {
            Path modelPath = Path.of("src", "main", "resources", "assets", "smarthorde",
                    "models", "item", modelName);
            assertTrue(Files.isRegularFile(modelPath), "Missing item model: " + modelPath);
        }

        for (String lang : new String[]{"zh_cn.json", "en_us.json", "ru_ru.json"}) {
            Path langPath = Path.of("src", "main", "resources", "assets", "smarthorde", "lang", lang);
            assertTrue(Files.isRegularFile(langPath), "Missing lang file: " + langPath);
            String content = Files.readString(langPath);
            for (EliteRole role : EliteRole.values()) {
                assertTrue(content.contains("\"" + role.nameKey() + "\""),
                        lang + " missing key " + role.nameKey());
            }
            for (String requiredKey : new String[]{
                    "commands.smarthorde.status.line",
                    "commands.smarthorde.wave.abandoned",
                    "hud.smarthorde.straggler_glow",
                    "hud.smarthorde.failed_death",
                    "item.smarthorde.smart_helmet",
                    "item.smarthorde.smart_sword",
                    "item.smarthorde.smart_zombie_spawn_egg",
                    "item.smarthorde.horde_boss_spawn_egg",
                    "item.smarthorde.horde_horn",
                    "smarthorde.configuration.title"
            }) {
                assertTrue(content.contains("\"" + requiredKey + "\""),
                        lang + " missing key " + requiredKey);
            }
        }
    }

    @Test
    void lookupAndWeightSelectionAreConsistent() {
        assertFalse(EliteRole.STANDARD.isElite());
        assertTrue(EliteRole.RAIDER.isElite());
        assertTrue(EliteRole.VANGUARD.isElite());
        assertTrue(EliteRole.BERSERKER.isElite());
        assertTrue(EliteRole.COMMANDER.isElite());

        assertEquals(EliteRole.VANGUARD, EliteRole.byId("VANGUARD"));
        assertEquals(EliteRole.COMMANDER, EliteRole.byId("commander"));
        assertThrows(IllegalArgumentException.class, () -> EliteRole.byId("nonexistent"));

        assertEquals(EliteRole.STANDARD, EliteRole.byOrdinal(0));
        assertEquals(EliteRole.COMMANDER, EliteRole.byOrdinal(-1));

        int totalWeight = EliteRole.totalEliteWeight();
        assertTrue(totalWeight > 0);
        for (int roll = 0; roll < totalWeight; roll++) {
            assertTrue(EliteRole.pickEliteByWeightRoll(roll).isElite());
        }
    }

    private static void assertPng128x128(Path pngPath) throws IOException {
        assertPngDimensions(pngPath, 128, 128);
    }

    private static void assertPngDimensions(Path pngPath, int expectedW, int expectedH) throws IOException {
        assertTrue(Files.isRegularFile(pngPath), "Missing PNG file: " + pngPath);
        byte[] header = new byte[24];
        try (InputStream in = Files.newInputStream(pngPath)) {
            assertEquals(24, in.read(header), "PNG header truncated: " + pngPath);
        }
        int width = ByteBuffer.wrap(header, 16, 4).getInt();
        int height = ByteBuffer.wrap(header, 20, 4).getInt();
        assertEquals(expectedW, width, "Unexpected width for " + pngPath);
        assertEquals(expectedH, height, "Unexpected height for " + pngPath);
    }
}
