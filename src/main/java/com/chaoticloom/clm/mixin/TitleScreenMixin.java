package com.chaoticloom.clm.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        ConnectScreen.connect(this, this.minecraft, serverData);
    }

    @Inject(method = "createNormalMenuOptions", at = @At("HEAD"), cancellable = true)
    private void createNormalMenuOptions(int startingYPos, int verticalSpacingBetweenButtons, CallbackInfo ci) {
        Button singlePlayerButton = createButton(
                Component.translatable("menu.singleplayer"),
                0,
                startingYPos, verticalSpacingBetweenButtons,
                button -> this.minecraft.setScreen(new SelectWorldScreen(this))
        );

        ci.cancel();
    }
}
