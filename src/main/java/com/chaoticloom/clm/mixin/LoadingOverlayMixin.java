package com.chaoticloom.clm.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Unique
    private static final ResourceLocation DIAMOND_PICKAXE = new ResourceLocation("clm", "textures/gui/chaoticloom.png");

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
        // Diamond pickaxe texture is 16×16
        int baseW = 1252;
        int baseH = 1252;

        // Scale down
        int scaledW = baseW / 10;
        int scaledH = baseH / 10;

        // Properly center on screen
        int screenCenterX = guiGraphics.guiWidth() / 2;
        int screenCenterY = guiGraphics.guiHeight() / 2;

        int centeredX = screenCenterX - (scaledW / 2);
        int centeredY = screenCenterY - (scaledH / 2);

        // Fix blending
        RenderSystem.defaultBlendFunc();
        //guiGraphics.setColor(1f, 1f, 1f, 1f);

        // Draw the texture
        guiGraphics.blit(
                DIAMOND_PICKAXE,
                centeredX, centeredY,
                scaledW, scaledH,
                0, 0,    // UV origin
                baseW, baseH, // region width/height
                baseW, baseH  // texture size
        );
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
