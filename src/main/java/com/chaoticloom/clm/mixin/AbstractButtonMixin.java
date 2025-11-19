package com.chaoticloom.clm.mixin;

import com.chaoticloom.clm.ChaoticLoomManager;
import com.chaoticloom.clm.client.CustomButtonsHolder;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public class AbstractButtonMixin {
    @Unique
    private static final ResourceLocation CUSTOM_BUTTONS = new ResourceLocation(ChaoticLoomManager.MOD_ID, "textures/gui/buttons.png");

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitNineSliced(Lnet/minecraft/resources/ResourceLocation;IIIIIIIIII)V"))
    protected void blit(GuiGraphics instance, ResourceLocation resourceLocation, int x, int y, int width, int height, int sliceWidth, int sliceHeight, int uWidth, int vHeight, int u, int v) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof TitleScreen) {
            AbstractButton abstractButton = (AbstractButton) (Object) this;

            if (abstractButton == CustomButtonsHolder.play) {
                instance.setColor(0.996F, 0.557F, 0.757F, 1.0F); // R
            }

            if (abstractButton == CustomButtonsHolder.options || abstractButton == CustomButtonsHolder.trailer) {
                instance.setColor(0.686F, 0.925F, 0.561F, 1.0F); // G
            }

            if (abstractButton == CustomButtonsHolder.quit) {
                instance.setColor(0.596F, 0.686F, 0.992F, 1.0F); // B
            }

            instance.blitNineSliced(CUSTOM_BUTTONS, x, y, width, height, sliceWidth, sliceHeight, uWidth, vHeight, u, v);
        } else {
            instance.blitNineSliced(resourceLocation, x, y, width, height, sliceWidth, sliceHeight, uWidth, vHeight, u, v);
        }
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractButton;renderString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"))
    protected void renderString(AbstractButton instance, GuiGraphics guiGraphics, Font font, int i) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof TitleScreen) {
            AbstractButton abstractButton = (AbstractButton) (Object) this;

            //guiGraphics.setColor(0F, 0F, 0F, 1.0F);
            int k = abstractButton.active ? 16777215 : 10526880;

            int x = abstractButton.getX() + abstractButton.getWidth() / 2;
            int y = abstractButton.getX() + abstractButton.getHeight() / 2;
            guiGraphics.drawString(font, abstractButton.getMessage(), x, y, k | Mth.ceil(abstractButton.alpha * 255.0F) << 24, false);
        } else {
            instance.renderString(guiGraphics, font,  i);
        }
    }
}