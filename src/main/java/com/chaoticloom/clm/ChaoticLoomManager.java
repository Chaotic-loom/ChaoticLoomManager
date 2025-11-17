package com.chaoticloom.clm;

import com.chaoticloom.clm.client.VideoPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.FastColor;

public class ChaoticLoomManager implements ModInitializer {
    public static final String MOD_ID = "clm";
    public static final String MOD_NAME = "Chaotic Loom Manager";

    @Override
    public void onInitialize() {
        System.out.println("//////////////////////////////////////////////////////////////////////777");
        System.out.println(FastColor.ARGB32.color(255, 239, 50, 61));
        System.out.println(FastColor.ARGB32.color(255, 0, 0, 0));
    }
}
