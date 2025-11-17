package com.chaoticloom.clm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ChaoticLoomManagerClient implements ClientModInitializer {
    private boolean videoStarted = false;

    @Override
    public void onInitializeClient() {
        VideoPlayerController.initialize();
        System.out.println("Video Player initialized!");

        // Wait for the game to fully load before playing video
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!videoStarted) {
                System.out.println("Video time");
                // Small delay to ensure everything is loaded
                client.execute(() -> {
                    try {
                        Thread.sleep(1000); // Wait 1 second after world load
                        VideoPlayerController.playVideo("/home/restonic4/test.mp4");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                videoStarted = true;
            }
        });
    }
}
