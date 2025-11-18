package com.chaoticloom.clm.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
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

    // Timing and synchronization
    private long baseTimeNanos = 0; // Base time for frame scheduling
    private int framesDecoded = 0; // Frame counter for more stable timing
    private volatile boolean needsCatchUp = false;

    private Path tempFile; // temporary file used when loading from a resource

    // Double buffering, pre-allocate two images
    private NativeImage bufferA;
    private NativeImage bufferB;
    private volatile NativeImage currentDecodeBuffer;
    private final AtomicReference<NativeImage> nextFrameImage = new AtomicReference<>();

    public VideoPlayer(String filePath) {
        loadResource(filePath);
    }

    public VideoPlayer(ResourceLocation resourceLocation) {
        try {
            System.out.println("Loading video resource: " + resourceLocation);

            // Obtain resource input stream from Minecraft's resource manager
            Minecraft client = Minecraft.getInstance();
            Optional<Resource> resource = client.getResourceManager().getResource(resourceLocation);
            if (resource.isEmpty()) {
                System.out.println("Resource not found!");
                return;
            }

            // Copy resource to a temp file (FFmpeg handles file paths reliably)
            try (InputStream is = resource.get().open()) {
                tempFile = Files.createTempFile("clm_video_", ".mp4");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            loadResource(tempFile.toFile().getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to initialize video player from ResourceLocation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadResource(String filePath) {
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
            // Initialize texture
            NativeImage nativeImage = new NativeImage(videoWidth, videoHeight, true);
            texture = new DynamicTexture(nativeImage);
            textureIdentifier = client.getTextureManager().register("video_frame", texture);

            // Pre-allocate decode buffers
            bufferA = new NativeImage(videoWidth, videoHeight, true);
            bufferB = new NativeImage(videoWidth, videoHeight, true);
            currentDecodeBuffer = bufferA;

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
            nextFrameImage.set(null);
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

    private void decoderLoop() {
        System.out.println("Decoder thread started.");

        try {
            while (playing.get()) {
                long targetTimeNanos = baseTimeNanos + (framesDecoded * frameTimeNanos);
                long currentTimeNanos = System.nanoTime();
                long waitTimeNanos = targetTimeNanos - currentTimeNanos;

                if (waitTimeNanos < -frameTimeNanos * 2) {
                    needsCatchUp = true;
                } else if (waitTimeNanos > 1000000) {
                    LockSupport.parkNanos(waitTimeNanos);
                }

                Frame frame = grabber.grab();
                if (frame == null) {
                    handleVideoEnd();
                    continue;
                }

                if (frame.image != null) {
                    // Decode directly into current buffer (no allocation!)
                    convertFrameToNativeImage(frame, currentDecodeBuffer);

                    // Swap buffers - the old nextFrame becomes our new decode target
                    NativeImage previousFrame = nextFrameImage.getAndSet(currentDecodeBuffer);

                    // The buffer we just swapped out becomes our next decode target
                    if (previousFrame != null) {
                        currentDecodeBuffer = previousFrame;
                    } else {
                        // First frame - use the other buffer
                        currentDecodeBuffer = (currentDecodeBuffer == bufferA) ? bufferB : bufferA;
                    }

                    framesDecoded++;

                    if (needsCatchUp && waitTimeNanos >= -frameTimeNanos) {
                        needsCatchUp = false;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            playing.set(false);
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

    private void convertFrameToNativeImage(Frame frame, NativeImage image) {
        ByteBuffer sourceBuffer = (ByteBuffer) frame.image[0];

        // Get direct access to NativeImage's internal buffer
        long imagePointer = image.pixels;

        if (imagePointer != 0) {
            // Direct memory copy - MUCH faster!
            int totalBytes = videoWidth * videoHeight * 4;

            // Ensure buffer is positioned at the start
            sourceBuffer.position(0);

            // Use unsafe memory copy or NIO bulk operations
            // Option 1: Using MemoryUtil from LWJGL
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(sourceBuffer),
                    imagePointer,
                    totalBytes
            );
        } else {
            // Fallback: still faster than individual pixels
            bulkConvertFallback(sourceBuffer, image);
        }
    }

    private void bulkConvertFallback(ByteBuffer sourceBuffer, NativeImage image) {
        sourceBuffer.position(0);
        int totalPixels = videoWidth * videoHeight;

        // Process in batches to reduce method call overhead
        for (int i = 0; i < totalPixels; i++) {
            int r = sourceBuffer.get() & 0xFF;
            int g = sourceBuffer.get() & 0xFF;
            int b = sourceBuffer.get() & 0xFF;
            int a = sourceBuffer.get() & 0xFF;

            int abgrColor = (a << 24) | (b << 16) | (g << 8) | r;

            int x = i % videoWidth;
            int y = i / videoWidth;
            image.setPixelRGBA(x, y, abgrColor);
        }
    }

    public void update() {
        if (!playing.get() || !initialized.get()) return;

        NativeImage imageToUpload = nextFrameImage.get();  // Just read, don't clear
        if (imageToUpload != null) {
            try {
                NativeImage textureImage = texture.getPixels();
                if (textureImage != null) {
                    textureImage.copyFrom(imageToUpload);
                    texture.upload();
                }
            } catch (Exception e) {
                e.printStackTrace();
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

            if (bufferA != null) {
                bufferA.close();
                bufferA = null;
            }
            if (bufferB != null) {
                bufferB.close();
                bufferB = null;
            }

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    tempFile = null;
                } catch (Exception e) {
                    tempFile.toFile().deleteOnExit();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}