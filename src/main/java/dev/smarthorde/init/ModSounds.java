package dev.smarthorde.init;

import dev.smarthorde.SmartHordeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 音效注册。实际音频在 assets/smarthorde/sounds.json 中引用 minecraft: 命名空间占位音效，
 * 后续替换自定义 .ogg 时只需修改 sounds.json。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SmartHordeMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HORDE_WAVE_START =
            register("horde_wave_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORDE_WAVE_CLEAR =
            register("horde_wave_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE_CHANGE =
            register("boss_phase_change");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMART_DODGE =
            register("smart_dodge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMART_ATTACK =
            register("smart_attack");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
