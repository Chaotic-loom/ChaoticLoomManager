package com.chaoticloom.clm.client;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;

public class RenderEvents {
    public static final Event<RenderEvent> RENDER =
            EventFactory.createArrayBacked(RenderEvent.class,
                    (listeners) -> (drawContext, tickDelta) -> {
                        for (RenderEvent listener : listeners) {
                            listener.invoke(drawContext, tickDelta);
                        }
                    }
            );

    @FunctionalInterface
    public interface RenderEvent {
        void invoke(GuiGraphics drawContext, float tickDelta);
    }

    public static final Event<VideoFinishedEvent> VIDEO_FINISHED =
            EventFactory.createArrayBacked(VideoFinishedEvent.class,
                    (listeners) -> () -> {
                        for (VideoFinishedEvent listener : listeners) {
                            listener.invoke();
                        }
                    }
            );

    @FunctionalInterface
    public interface VideoFinishedEvent {
        void invoke();
    }

    public static final Event<SoundEngineLoadedEvent> SOUND_ENGINE_LOADED =
            EventFactory.createArrayBacked(SoundEngineLoadedEvent.class,
                    (listeners) -> () -> {
                        for (SoundEngineLoadedEvent listener : listeners) {
                            listener.invoke();
                        }
                    }
            );

    @FunctionalInterface
    public interface SoundEngineLoadedEvent {
        void invoke();
    }
}
