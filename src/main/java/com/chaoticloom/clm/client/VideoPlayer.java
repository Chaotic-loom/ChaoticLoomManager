package com.chaoticloom.clm.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class VideoPlayer {
    private FFmpegFrameGrabber grabber;
    private DynamicTexture texture;
    private ResourceLocation textureIdentifier;

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private boolean loop = false;

    private int videoWidth;
    private int videoHeight;
    private double frameTime; // Time per frame in seconds
    private long frameTimeNanos; // Time per frame in nanoseconds

    // Threading components
    private Thread decoderThread;
    private final AtomicReference<NativeImage> nextFrameImage = new AtomicReference<>();

    // **NEW**: Timing and synchronization improvements
    private long baseTimeNanos = 0; // Base time for frame scheduling
    private int framesDecoded = 0; // Frame counter for more stable timing
    private volatile boolean needsCatchUp = false;

    public VideoPlayer(String filePath) {
        try {
            System.out.println("Loading video from: " + filePath);
            grabber = new FFmpegFrameGrabber(filePath);
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            grabber.start();

            videoWidth = grabber.getImageWidth();
            videoHeight = grabber.getImageHeight();
            double frameRate = Math.max(grabber.getFrameRate(), 1.0); // Ensure positive frame rate
            frameTime = 1.0 / frameRate;
            frameTimeNanos = (long) (frameTime * 1_000_000_000.0);

            System.out.println("Video loaded: " + videoWidth + "x" + videoHeight +
                    ", FPS: " + frameRate + ", Frame time: " + frameTime + "s");

        } catch (Exception e) {
            System.err.println("Failed to initialize video player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initializeTexture() {
        if (initialized.get() || grabber == null) return;

        Minecraft client = Minecraft.getInstance();
        try {
            NativeImage nativeImage = new NativeImage(videoWidth, videoHeight, true);
            texture = new DynamicTexture(nativeImage);
            textureIdentifier = client.getTextureManager().register("video_frame", texture);
            initialized.set(true);
            System.out.println("Video texture initialized: " + textureIdentifier);
        } catch (Exception e) {
            System.err.println("Failed to initialize video texture: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void play() {
        if (!initialized.get()) {
            System.err.println("Texture not initialized! Call initializeTexture() on render thread first.");
            return;
        }
        if (playing.get() && decoderThread != null && decoderThread.isAlive()) {
            return;
        }

        playing.set(true);
        baseTimeNanos = System.nanoTime();
        framesDecoded = 0;
        needsCatchUp = false;

        decoderThread = new Thread(this::decoderLoop, "Video-Decoder-Thread");
        decoderThread.setDaemon(true);
        decoderThread.start();
        System.out.println("Video playback started!");
    }

    public void pause() {
        playing.set(false);
        joinDecoderThread();
    }

    public void stop() {
        playing.set(false);
        joinDecoderThread();
        try {
            if (grabber != null) {
                grabber.setVideoTimestamp(0);
            }
            NativeImage oldFrame = nextFrameImage.getAndSet(null);
            if (oldFrame != null) {
                oldFrame.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void joinDecoderThread() {
        if (decoderThread != null && decoderThread.isAlive()) {
            try {
                decoderThread.join(1000);
                if (decoderThread.isAlive()) {
                    System.err.println("Decoder thread did not stop, interrupting.");
                    decoderThread.interrupt();
                }
            } catch (InterruptedException e) {
                System.err.println("Interrupted while joining decoder thread.");
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
        }
    }

    // **IMPROVED**: Better timing logic with catch-up mechanism
    private void decoderLoop() {
        System.out.println("Decoder thread started.");

        // Pre-allocate frame buffer to reduce GC pressure
        NativeImage currentFrame = new NativeImage(videoWidth, videoHeight, true);

        try {
            while (playing.get()) {
                long targetTimeNanos = baseTimeNanos + (framesDecoded * frameTimeNanos);
                long currentTimeNanos = System.nanoTime();

                // Calculate how long to wait
                long waitTimeNanos = targetTimeNanos - currentTimeNanos;

                // If we're more than 2 frames behind, we need to catch up
                if (waitTimeNanos < -frameTimeNanos * 2) {
                    needsCatchUp = true;
                    // Skip sleep and decode immediately
                } else if (waitTimeNanos > 1000000) { // Only sleep if more than 1ms
                    // Use parkNanos for more precise timing than Thread.sleep
                    LockSupport.parkNanos(waitTimeNanos);
                }

                Frame frame = grabber.grab();
                if (frame == null) {
                    handleVideoEnd();
                    continue;
                }

                if (frame.image != null) {
                    // Reuse the pre-allocated NativeImage instead of creating new ones
                    convertFrameToNativeImage(frame, currentFrame);

                    // Create a new image only when necessary (when the frame will be used)
                    NativeImage frameToSend = new NativeImage(videoWidth, videoHeight, true);
                    frameToSend.copyFrom(currentFrame);

                    NativeImage oldFrame = nextFrameImage.getAndSet(frameToSend);
                    if (oldFrame != null) {
                        oldFrame.close();
                    }

                    framesDecoded++;

                    // Reset catch-up flag once we're back on schedule
                    if (needsCatchUp && waitTimeNanos >= -frameTimeNanos) {
                        needsCatchUp = false;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            playing.set(false);
        } finally {
            currentFrame.close();
        }
        System.out.println("Decoder thread stopped.");
    }

    private void handleVideoEnd() throws FFmpegFrameGrabber.Exception {
        if (loop) {
            grabber.setVideoTimestamp(0);
            baseTimeNanos = System.nanoTime();
            framesDecoded = 0;
            needsCatchUp = false;
        } else {
            playing.set(false);
        }
    }

    // **IMPROVED**: Reuse existing NativeImage to reduce allocations
    private void convertFrameToNativeImage(Frame frame, NativeImage image) {
        ByteBuffer buffer = (ByteBuffer) frame.image[0];

        // Use bulk operations where possible
        for (int y = 0; y < videoHeight; y++) {
            for (int x = 0; x < videoWidth; x++) {
                int pos = (y * videoWidth + x) * 4;

                int r = buffer.get(pos) & 0xFF;
                int g = buffer.get(pos + 1) & 0xFF;
                int b = buffer.get(pos + 2) & 0xFF;
                int a = buffer.get(pos + 3) & 0xFF;

                int abgrColor = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, abgrColor);
            }
        }
    }

    // **IMPROVED**: More efficient update with better frame management
    public void update() {
        if (!playing.get() || !initialized.get()) return;

        NativeImage imageToUpload = nextFrameImage.getAndSet(null);

        if (imageToUpload != null) {
            try {
                NativeImage textureImage = texture.getPixels();
                if (textureImage != null) {
                    // This is the fast native copy
                    textureImage.copyFrom(imageToUpload);
                    texture.upload();

                    FPSTracker.onFrame();
                    System.out.println(FPSTracker.getFps());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                imageToUpload.close();
            }
        }
    }

    // Getters and Setters
    public ResourceLocation getTexture() {
        return textureIdentifier;
    }

    public int getWidth() {
        return videoWidth;
    }

    public int getHeight() {
        return videoHeight;
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void close() {
        stop();
        try {
            if (grabber != null) {
                grabber.close();
            }
            if (textureIdentifier != null) {
                Minecraft.getInstance().getTextureManager().release(textureIdentifier);
            }
            if (texture != null) {
                texture.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}