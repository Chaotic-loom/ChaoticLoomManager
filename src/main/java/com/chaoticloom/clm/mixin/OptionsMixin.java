package com.chaoticloom.clm.mixin;

import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class OptionsMixin {
    @Inject(method = "getSoundSourceVolume", at = @At("RETURN"), cancellable = true)
    public final void getSoundSourceVolume(SoundSource soundSource, CallbackInfoReturnable<Float> cir) {
        if (soundSource == SoundSource.MUSIC) {
            cir.setReturnValue((float) 0);
            cir.cancel();
        }
    }
}
