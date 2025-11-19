package com.chaoticloom.clm.mixin;

import com.chaoticloom.clm.ChaoticLoomManager;
import com.chaoticloom.clm.client.CustomButtonsHolder;
import com.chaoticloom.clm.client.VideoPlayerController;
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

    @Unique private static final int ICON_WIDTH = 932;
    @Unique private static final int ICON_HEIGHT = 1036;
    @Unique private static final int BUTTON_WIDTH = 200;
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final ResourceLocation BRAND_TEXTURE = new ResourceLocation("clm", "textures/gui/chaoticloom.png");
    @Unique private static final int BACKGROUND_COLOR = FastColor.ARGB32.color(255, 11, 25, 38);

    /*
        Create custom buttons
     */
    @Inject(method = "createNormalMenuOptions", at = @At("HEAD"), cancellable = true)
    private void createNormalMenuOptions(int startingYPos, int verticalSpacingBetweenButtons, CallbackInfo ci) {
        CustomButtonsHolder.play = createButton(
                Component.translatable("clm.title-screen.join-event"),
                0,
                startingYPos, verticalSpacingBetweenButtons,
                button -> joinServer(ChaoticLoomManager.SERVER_IP, ChaoticLoomManager.SERVER_PORT)
        );

        CustomButtonsHolder.options = createButton(
                Component.translatable("menu.options"),
                1,
                startingYPos, verticalSpacingBetweenButtons,
                button -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))
        );

        CustomButtonsHolder.trailer = createButton(
                Component.translatable("clm.title-screen.trailer"),
                2,
                startingYPos, verticalSpacingBetweenButtons,
                button -> VideoPlayerController.playVideo(ChaoticLoomManager.TRAILER)
        );

        CustomButtonsHolder.quit = createButton(
                Component.translatable("menu.quit"),
                3,
                startingYPos, verticalSpacingBetweenButtons,
                button -> this.minecraft.stop()
        );

        ci.cancel();
    }

    /*
        Render custom background
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private void redirectOverlayBlit(GuiGraphics guiGraphics, ResourceLocation resourceLocation, int i, int j, int k, int l, float f, float g, int m, int n, int o, int p) {
        guiGraphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Get current screen dimensions
        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();

        // Determine scale factor based on window size
        // This keeps aspect ratio proportional
        float scaleFactor = Math.min(screenW / (float)ICON_WIDTH, screenH / (float)ICON_HEIGHT) * 2.15f;
        // multiply by 0.5 to keep it reasonably smaller than full screen, adjust as needed

        // Calculate scaled size
        int scaledW = Math.round(ICON_WIDTH * scaleFactor);
        int scaledH = Math.round(ICON_HEIGHT * scaleFactor);

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
                ICON_WIDTH, ICON_HEIGHT, // region width/height
                ICON_WIDTH, ICON_HEIGHT  // texture size
        );
    }

    /*
        Cancel the whole rendering if video playing
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        if (VideoPlayerController.isVideoPlaying()) {
            ci.cancel();
        }
    }

    /*
        Cancel default buttons
     */
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener init(TitleScreen instance, GuiEventListener guiEventListener) {
        return guiEventListener;
    }

    /*
        Cancel panorama rendering
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void redirectPanoramaRender(PanoramaRenderer instance, float f, float g) {}

    /*
        Cancel splash rendering
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
    private void splash(SplashRenderer instance, GuiGraphics guiGraphics, int i, Font font, int j) {}

    /*
        Unique methods
     */

    @Unique
    private Button createButton(MutableComponent text, int index, int startingYPos, int verticalSpacingBetweenButtons, Consumer<Button> onPressAction) {
        Button newButton = Button.builder(
                        text,
                        onPressAction::accept
                )
                .bounds(this.width / 2 - 100, startingYPos + verticalSpacingBetweenButtons * index, BUTTON_WIDTH, BUTTON_HEIGHT)
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
}
