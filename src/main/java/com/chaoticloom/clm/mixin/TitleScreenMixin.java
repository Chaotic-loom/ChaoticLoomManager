package com.chaoticloom.clm.mixin;

import com.chaoticloom.clm.ChaoticLoomManager;
import com.chaoticloom.clm.client.RenderEvents;
import com.chaoticloom.clm.client.VideoPlayer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component component) {
        super(component);
    }

    @Unique int buttonWidth = 200;
    @Unique int buttonHeight = 20;

    @Unique
    private Button createButton(MutableComponent text, int index, int startingYPos, int verticalSpacingBetweenButtons, Consumer<Button> onPressAction) {
        Button newButton = Button.builder(
                        text,
                        onPressAction::accept
                )
                .bounds(this.width / 2 - 100, startingYPos + verticalSpacingBetweenButtons * index, buttonWidth, buttonHeight)
                .build();

        this.addRenderableWidget(newButton);

        return newButton;
    }

    @Unique
    private void joinServer(String ip, int port) {
        ServerAddress serverAddress = ServerAddress.parseString(ip + ":" + port);
        ServerData serverData = new ServerData("My Server", serverAddress.getHost(), false);
        ConnectScreen.startConnecting(this, this.minecraft, serverAddress, serverData, true);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener init(TitleScreen instance, GuiEventListener guiEventListener) {
        return guiEventListener;
    }

    /*@Inject(method = "createNormalMenuOptions", at = @At("HEAD"), cancellable = true)
    private void createNormalMenuOptions(int startingYPos, int verticalSpacingBetweenButtons, CallbackInfo ci) {
        Button serverButton = createButton(
                Component.translatable("clm.title-screen.join-event"),
                0,
                startingYPos, verticalSpacingBetweenButtons,
                button -> joinServer("127.0.0.1", 25565)
        );

        Button optionsButton = createButton(
                Component.translatable("menu.options"),
                1,
                startingYPos, verticalSpacingBetweenButtons,
                button -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))
        );

        Button quitButton = createButton(
                Component.translatable("menu.quit"),
                2,
                startingYPos, verticalSpacingBetweenButtons,
                button -> this.minecraft.stop()
        );

        ci.cancel();
    }*/

    @Unique
    private static final ResourceLocation BRAND_TEXTURE = new ResourceLocation("clm", "textures/gui/chaoticloom.png");

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"
            )
    )
    private void redirectPanoramaRender(PanoramaRenderer instance, float f, float g) {
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"
            )
    )
    private void splash(SplashRenderer instance, GuiGraphics guiGraphics, int i, Font font, int j) {
    }

    @Unique
    int backgroundColor = FastColor.ARGB32.color(255, 11, 25, 38);

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"
            )
    )
    private void redirectOverlayBlit(GuiGraphics guiGraphics, ResourceLocation resourceLocation, int i, int j, int k, int l, float f, float g, int m, int n, int o, int p) {
        guiGraphics.fill(0, 0, this.width, this.height, backgroundColor);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Diamond pickaxe texture is 16×16
        int baseW = 932;
        int baseH = 1036;

        // Get current screen dimensions
        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();

        // Determine scale factor based on window size
        // This keeps aspect ratio proportional
        float scaleFactor = Math.min(screenW / (float)baseW, screenH / (float)baseH) * 2.15f;
        // multiply by 0.5 to keep it reasonably smaller than full screen, adjust as needed

        // Calculate scaled size
        int scaledW = Math.round(baseW * scaleFactor);
        int scaledH = Math.round(baseH * scaleFactor);

        // Properly center on screen
        int centeredX = (screenW - scaledW) / 2;
        int centeredY = (screenH - scaledH) / 2;

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
}
