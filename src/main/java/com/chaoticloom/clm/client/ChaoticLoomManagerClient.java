package com.chaoticloom.clm.client;

import com.chaoticloom.clm.ChaoticLoomManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChaoticLoomManagerClient implements ClientModInitializer {

    // We use this to stop checking once we've made a decision (play or don't play)
    private boolean startupLogicComplete = false;

    @Override
    public void onInitializeClient() {
        VideoPlayerController.initialize();
        System.out.println("Video Player initialized!");

        // Define the path to the flag file (e.g., run/config/clm_intro_played.dat)
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path flagFile = configDir.resolve("clm_intro_played.dat");

        // Wait for the game to fully load before playing video
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!startupLogicComplete) {

                // 1. Check if the file already exists
                if (Files.exists(flagFile)) {
                    System.out.println("Intro video already played in a previous session.");
                    startupLogicComplete = true;
                    return;
                }

                // 2. If file does not exist, proceed to play video
                System.out.println("First time setup detected. Preparing video.");

                client.execute(() -> {
                    try {
                        // Small delay to ensure everything is loaded
                        Thread.sleep(1000);

                        VideoPlayerController.playVideo(ChaoticLoomManager.TRAILER);

                        // 3. Create the file immediately so it doesn't play next time
                        try {
                            Files.createFile(flagFile);
                            System.out.println("Intro flag file created successfully.");
                        } catch (IOException e) {
                            System.err.println("Failed to create intro flag file: " + e.getMessage());
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

                startupLogicComplete = true;
            }
        });
    }
}