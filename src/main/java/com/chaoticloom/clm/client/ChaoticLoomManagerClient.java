package com.chaoticloom.clm.client;

import com.chaoticloom.clm.ChaoticLoomManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChaoticLoomManagerClient implements ClientModInitializer {

    // We use this to stop checking once we've made a decision (play or don't play)
    private boolean startupLogicComplete = false;

    @Override
    public void onInitializeClient() {
        // Define the path to the flag file (e.g., run/config/clm_intro_played.dat)
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path flagFile = configDir.resolve("clm_intro_played.dat");

        RenderEvents.SOUND_ENGINE_LOADED.register(() -> {
            VideoPlayerController.initialize();
            System.out.println("Video Player initialized!");

            if (!startupLogicComplete) {

                // 1. Check if the file already exists
                if (Files.exists(flagFile)) {
                    System.out.println("Intro video already played in a previous session.");
                    startupLogicComplete = true;
                    return;
                }

                // 2. If file does not exist, proceed to play video
                System.out.println("First time setup detected. Preparing video.");

                Minecraft.getInstance().execute(() -> {
                    try {
                        // Small delay to ensure everything is loaded
                        Thread.sleep(1000);

                        VideoPlayerController.playVideo(ChaoticLoomManager.TRAILER);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

                startupLogicComplete = true;
            }
        });

        RenderEvents.VIDEO_FINISHED.register(() -> {
            System.out.println("Video ended!");
            try {
                Files.createFile(flagFile);
                System.out.println("Intro flag file created successfully.");
            } catch (IOException e) {
                System.err.println("Failed to create intro flag file: " + e.getMessage());
            }
        });
    }
}