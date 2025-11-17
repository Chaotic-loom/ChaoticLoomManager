package com.chaoticloom.clm.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Unique
    private static final ResourceLocation BRAND_TEXTURE = new ResourceLocation("clm", "textures/gui/chaoticloom.png");

    @Shadow
    @Final
    @Mutable
    private static int LOGO_BACKGROUND_COLOR;

    @Shadow
    @Final
    @Mutable
    private static int LOGO_BACKGROUND_COLOR_DARK;

    static {
        // set new values
        LOGO_BACKGROUND_COLOR = ARGB32.color(255, 11, 25, 38);
        LOGO_BACKGROUND_COLOR_DARK = ARGB32.color(255, 11, 25, 38);
    }

    //Lnet/minecraft/client/gui/screens/LoadingOverlay;LOGO_BACKGROUND_COLOR_DARK:I
    // Change LOGO_BACKGROUND_COLOR
    //@ModifyConstant(method = "<init>", constant = @Constant(intValue = -1101251))
    /*@ModifyConstant(method = "<init>", constant = @Constant(intValue = 0xFFEF323D))
    private int changeLogoBackgroundColor(int original) {
        return ARGB32.color(255, 11, 25, 38);
    }*/

    // Change LOGO_BACKGROUND_COLOR_DARK
    /*@ModifyConstant(method = "<init>", constant = @Constant(intValue = -16777216))
    private int changeLogoBackgroundColorDark(int original) {
        return ARGB32.color(255, 11, 25, 38);
    }*/

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
        int baseW = 932;
        int baseH = 1036;

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

        // Draw the texture
        guiGraphics.blit(
                BRAND_TEXTURE,
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
