package com.chaoticloom.clm;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaoticLoomManager implements ModInitializer {
    public static final String MOD_ID = "clm";
    public static final String MOD_NAME = "Chaotic Loom Manager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final String SERVER_IP = "127.0.0.1";
    public static final int SERVER_PORT = 25565;

    public static final ResourceLocation TRAILER = new ResourceLocation(MOD_ID, "videos/trailer.mp4");

    @Override
    public void onInitialize() {

    }
}
