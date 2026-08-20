package dev.smarthorde.init;

import dev.smarthorde.SmartHorde;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 音效注册表（轮9填充）。
 * 当前占位引用原版音，正式版替换为自绘 .ogg 即可，代码不动。
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SmartHorde.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWING_WINDUP = register("swing_windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKE_HIT   = register("strike_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> EVADE_WHOOSH = register("evade_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE_HORN    = register("wave_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE_CLEAR   = register("wave_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> VICTORY      = register("victory");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(SmartHorde.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }

    private ModSounds() {}
}
