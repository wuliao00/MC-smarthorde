package dev.smarthorde.config;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 难度管理器（轮6）。
 * 全局单例当前难度；applyTo() 出生时按难度缩放属性。
 * 难度变更只影响之后新生成的实体，已存在实体保持旧值。
 */
public final class DifficultyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");

    // 1.21.1 AttributeModifier 用 ResourceLocation 作为唯一标识
    private static final ResourceLocation HEALTH_MOD_ID = ResourceLocation.fromNamespaceAndPath("smarthorde", "difficulty_health");
    private static final ResourceLocation DAMAGE_MOD_ID = ResourceLocation.fromNamespaceAndPath("smarthorde", "difficulty_damage");
    private static final ResourceLocation SPEED_MOD_ID  = ResourceLocation.fromNamespaceAndPath("smarthorde", "difficulty_speed");

    private static volatile DifficultyPreset current = DifficultyPreset.NORMAL;

    private DifficultyManager() {}

    public static DifficultyPreset get() { return current; }

    public static void set(DifficultyPreset preset) {
        if (preset == null) preset = DifficultyPreset.NORMAL;
        current = preset;
        LOGGER.info("[SmartHorde] 难度已切换为: {}", preset.getId());
    }

    public static void reloadFromConfig() {
        try {
            String id = SmartHordeConfig.DIFFICULTY_PRESET.get();
            set(DifficultyPreset.byId(id));
        } catch (Exception e) {
            LOGGER.warn("[SmartHorde] 读取难度配置失败，回退 normal", e);
            current = DifficultyPreset.NORMAL;
        }
    }

    public static void applyTo(LivingEntity entity) {
        DifficultyPreset p = get();
        applyModifier(entity, Attributes.MAX_HEALTH,    HEALTH_MOD_ID, p.getHealthMultiplier());
        applyModifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_MOD_ID, p.getDamageMultiplier());
        applyModifier(entity, Attributes.MOVEMENT_SPEED, SPEED_MOD_ID,  p.getSpeedMultiplier());
        entity.setHealth(entity.getMaxHealth());
    }

    private static void applyModifier(LivingEntity entity, Holder<Attribute> attr, ResourceLocation modId, double multiplier) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(modId);
        if (multiplier != 1.0D) {
            inst.addPermanentModifier(new AttributeModifier(
                    modId, multiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
