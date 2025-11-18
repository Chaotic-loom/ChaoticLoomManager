package com.chaoticloom.clm.mixin;

import com.chaoticloom.clm.client.RenderEvents;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "loadLibrary", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Lorg/slf4j/Marker;Ljava/lang/String;)V"))
    private synchronized void loadLibrary(CallbackInfo ci) {
        RenderEvents.SOUND_ENGINE_LOADED.invoker().invoke();
    }
}
