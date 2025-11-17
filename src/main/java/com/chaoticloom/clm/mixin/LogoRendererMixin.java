package com.chaoticloom.clm.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.minecraft.client.gui.components.LogoRenderer.*;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    @Final @Shadow private boolean keepLogoThroughFade;

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    public void renderLogo(GuiGraphics guiGraphics, int screenWidth, float alpha, int yPosition, CallbackInfo ci) {
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.keepLogoThroughFade ? 1.0F : alpha);

        int logoX = screenWidth / 2 - 128;
        guiGraphics.blit(MINECRAFT_LOGO, logoX, yPosition, 0.0F, 0.0F, 256, 44, 256, 64);

        int editionX = screenWidth / 2 - 64;
        int editionY = yPosition + 44 - 7;
        guiGraphics.blit(MINECRAFT_EDITION, editionX, editionY, 0.0F, 0.0F, 128, 14, 128, 16);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        ci.cancel();
    }
}
