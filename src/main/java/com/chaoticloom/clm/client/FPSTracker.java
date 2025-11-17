package com.chaoticloom.clm.client;


public class FPSTracker {
    private static long lastTime = System.currentTimeMillis();
    private static int frames;
    private static int currentFps;

    public static void onFrame() {
        frames++;
        long now = System.currentTimeMillis();

        if (now - lastTime >= 1000) {
            currentFps = frames;
            frames = 0;
            lastTime = now;
        }
    }

    public static int getFps() {
        return currentFps;
    }
}
