package com.chaoticloom.clm.mixin;

import com.chaoticloom.clm.client.VideoPlayerController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class DisableInputMixin {
    @Mixin(KeyboardHandler.class)
    public static class Keyboard {
        @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
        private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
            if (VideoPlayerController.isVideoPlaying()) {
                ci.cancel();
            }
        }

        @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
        private void onCharTyped(long window, int codepoint, int modifiers, CallbackInfo ci) {
            if (VideoPlayerController.isVideoPlaying()) {
                ci.cancel();
            }
        }
    }

    @Mixin(MouseHandler.class)
    public static class Mouse {
        @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
        private void onMousePress(long window, int button, int action, int modifiers, CallbackInfo ci) {
            if (VideoPlayerController.isVideoPlaying()) {
                ci.cancel();
            }
        }

        @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
        private void onMouseScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
            if (VideoPlayerController.isVideoPlaying()) {
                ci.cancel();
            }
        }

        @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
        private void onCursorPos(long window, double xpos, double ypos, CallbackInfo ci) {
            if (VideoPlayerController.isVideoPlaying()) {
                ci.cancel();
            }
        }
    }
}
