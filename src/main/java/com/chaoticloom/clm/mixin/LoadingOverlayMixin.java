package com.chaoticloom.clm.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit("
                            + "Lnet/minecraft/resources/ResourceLocation;"
                            + "IIIIFFIIII)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(value = "FIELD",
                            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;MOJANG_STUDIOS_LOGO_LOCATION:Lnet/minecraft/resources/ResourceLocation;")
            )

    )
    private void redirectFirstBlit(
            GuiGraphics guiGraphics,
            ResourceLocation tex,
            int x, int y, int w, int h,
            float u, float v, int tw, int th, int texW, int texH
    ) {
        int newW = (int)(w * 4f);
        int newH = (int)(h * 0.9f);

        guiGraphics.blit(tex, x, y, newW, newH, u, v, tw, th, texW, texH);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit("
                            + "Lnet/minecraft/resources/ResourceLocation;"
                            + "IIIIFFIIII)V",
                    ordinal = 1
            ),
            slice = @Slice(
                    from = @At(value = "FIELD",
                            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;MOJANG_STUDIOS_LOGO_LOCATION:Lnet/minecraft/resources/ResourceLocation;")
            )

    )
    private void redirectSecondBlit(
            GuiGraphics guiGraphics,
            ResourceLocation tex,
            int x, int y, int w, int h,
            float u, float v, int tw, int th, int texW, int texH
    ) {
        // Skip second blit
    }
}
