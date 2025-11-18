package com.chaoticloom.clm.client;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class AudioPlayerOld {

    private final int alFormat;
    private final int sampleRate;

    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean flushRequest = new AtomicBoolean(false);

    private final Thread audioThread;
    private final BlockingQueue<ByteBuffer> audioQueue = new LinkedBlockingQueue<>(16); // Max 16 audio chunks

    // --- These fields are ONLY touched by the audioThread ---
    private long device;
    private long context;
    private int sourceId;
    private int[] allBuffers;
    private final Queue<Integer> freeBuffers = new LinkedList<>();
    private static final int NUM_BUFFERS = 8;
    // ---

    public AudioPlayerOld(int sampleRate, int channels) {
        this.sampleRate = sampleRate;

        if (channels == 1) {
            this.alFormat = AL_FORMAT_MONO16;
        } else if (channels == 2) {
            this.alFormat = AL_FORMAT_STEREO16;
        } else {
            throw new IllegalArgumentException("Unsupported audio channel count: " + channels);
        }

        // Create the thread but don't start it.
        // We start it in play() to ensure context is ready only when needed.
        this.audioThread = new Thread(this::audioLoop, "Video-Audio-Thread");
        this.audioThread.setDaemon(true);
        System.out.println("AudioPlayer ready.");
    }

    /**
     * The main loop for the dedicated audio thread.
     * This is where all OpenAL calls happen.
     */
    private void audioLoop() {
        // --- 1. Initialize OpenAL Context ---
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null); // Open default device
            if (device == NULL) {
                throw new IllegalStateException("Failed to open OpenAL device.");
            }

            ALCCapabilities deviceCaps = ALC.createCapabilities(device);

            context = ALC10.alcCreateContext(device, (IntBuffer) null);
            if (context == NULL) {
                throw new IllegalStateException("Failed to create OpenAL context.");
            }

            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps); // IMPORTANT!

        } catch (Exception e) {
            System.err.println("AudioPlayer thread failed to initialize OpenAL: " + e.getMessage());
            isClosed.set(true);
            return; // Exit thread
        }

        System.out.println("AudioPlayer thread started, OpenAL context created.");

        // --- 2. Initialize Source and Buffers ---
        try {
            this.sourceId = alGenSources();
            alSourcef(sourceId, AL_GAIN, 1.0f);
            alSourcef(sourceId, AL_PITCH, 1.0f);
            alSource3f(sourceId, AL_POSITION, 0, 0, 0);
            alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_TRUE);

            this.allBuffers = new int[NUM_BUFFERS];
            alGenBuffers(this.allBuffers);
            for (int bufferId : this.allBuffers) {
                this.freeBuffers.offer(bufferId);
            }
        } catch (Exception e) {
            System.err.println("AudioPlayer thread failed to initialize buffers: " + e.getMessage());
            isClosed.set(true);
            // Fall-through to cleanup
        }


        // --- 3. Main Processing Loop ---
        while (!isClosed.get()) {
            try {
                // Handle flushing first
                if (flushRequest.getAndSet(false)) {
                    internalFlush();
                }

                if (isPlaying.get()) {
                    // Reclaim any free buffers
                    updateProcessedBuffers();

                    // Poll for new audio data from the queue
                    ByteBuffer data = audioQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (data != null) {
                        Integer bufferId = freeBuffers.poll();
                        if (bufferId != null) {
                            // We have data and a free buffer, queue it
                            alBufferData(bufferId, alFormat, data, sampleRate);
                            alSourceQueueBuffers(sourceId, bufferId);
                        } else {
                            // No free buffer, put data back (or drop it)
                            // For simplicity, we'll just log and drop it to avoid blocking
                            System.err.println("Audio buffer starvation! Dropping audio packet.");
                        }

                        // Start playing if we're not already
                        int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
                        if (state != AL_PLAYING) {
                            alSourcePlay(sourceId);
                        }
                    }
                    // If data is null, we just loop and wait (buffer underrun)

                } else {
                    // We are paused
                    int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
                    if (state == AL_PLAYING) {
                        alSourcePause(sourceId);
                    }
                    // Wait for a play() or close() command
                    Thread.sleep(50); // Simple sleep, avoids spin-locking
                }

            } catch (InterruptedException e) {
                // Interrupted, likely by close(). Break loop.
                break;
            } catch (Exception e) {
                System.err.println("Error in audio loop: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // --- 4. Cleanup ---
        System.out.println("AudioPlayer thread stopping...");
        try {
            alSourceStop(sourceId);
            alDeleteSources(sourceId);
            alDeleteBuffers(allBuffers);

            ALC10.alcMakeContextCurrent(NULL);
            ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
        } catch (Exception e) {
            System.err.println("Error during OpenAL cleanup: " + e.getMessage());
        }
        System.out.println("AudioPlayer thread stopped.");
    }

    /**
     * Called by the decoder thread to add audio data.
     * This method is thread-safe.
     */
    public void queueAudio(ByteBuffer data) {
        if (isClosed.get() || data == null || !data.hasRemaining()) return;

        try {
            // This will block if the queue is full, providing back-pressure
            // to the decoder thread and keeping audio/video in sync.
            audioQueue.put(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateProcessedBuffers() {
        int processedCount = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED);
        if (processedCount > 0) {
            int[] unqueuedBuffers = new int[processedCount];
            alSourceUnqueueBuffers(sourceId, unqueuedBuffers);
            for (int bufferId : unqueuedBuffers) {
                this.freeBuffers.offer(bufferId);
            }
        }
    }

    /**
     * Flushes all buffers. MUST be called from the audio thread.
     */
    private void internalFlush() {
        // Stop playing
        alSourceStop(sourceId);

        // Clear our internal queue
        audioQueue.clear();
        freeBuffers.clear();

        // Unqueue all OpenAL buffers
        int queuedCount = alGetSourcei(sourceId, AL_BUFFERS_QUEUED);
        if (queuedCount > 0) {
            int[] unqueuedBuffers = new int[queuedCount];
            alSourceUnqueueBuffers(sourceId, unqueuedBuffers);
        }

        // Re-add all buffers to the free queue
        for (int bufferId : this.allBuffers) {
            freeBuffers.offer(bufferId);
        }
        System.out.println("Audio buffers flushed.");
    }

    // --- Public Control Methods (Thread-Safe) ---

    public void play() {
        if (!audioThread.isAlive()) {
            audioThread.start();
        }
        isPlaying.set(true);
    }

    public void pause() {
        isPlaying.set(false);
    }

    public void stop() {
        isPlaying.set(false);
        flushRequest.set(true); // Tell audio thread to flush
    }

    /**
     * Triggers a flush of the audio buffers.
     * Called by VideoPlayer on loop/seek.
     */
    public void flushBuffers() {
        flushRequest.set(true);
    }

    public void close() {
        if (isClosed.getAndSet(true)) return;

        isPlaying.set(false);
        audioQueue.clear();

        if (audioThread.isAlive()) {
            audioThread.interrupt(); // Interrupt sleep/wait/poll
            try {
                audioThread.join(1000); // Wait for thread to die
            } catch (InterruptedException e) {
                System.err.println("Interrupted while closing audio thread.");
            }
        }
    }
}
/*package com.chaoticloom.clm.client;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.bytedeco.javacv.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;
import static org.lwjgl.openal.ALC10.*;

public class AudioPlayer {
    private long device;
    private long context;
    private int source;
    private int[] buffers;

    private final int sampleRate;
    private final int channels;
    private final ConcurrentLinkedQueue<short[]> audioQueue;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicLong samplesPlayed = new AtomicLong(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);

    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 4096 * 4; // Increased buffer size
    private static final int MAX_QUEUE_SIZE = 100; // Prevent memory overflow

    private Thread audioThread;
    private final AtomicBoolean audioThreadRunning = new AtomicBoolean(false);

    public AudioPlayer(int channels, int sampleRate) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.audioQueue = new ConcurrentLinkedQueue<>();

        if (channels > 0 && sampleRate > 0) {
            initOpenAL();
        } else {
            System.err.println("Invalid audio parameters: channels=" + channels + ", sampleRate=" + sampleRate);
        }
    }

    private void initOpenAL() {
        try {
            System.out.println("Initializing OpenAL for " + channels + " channels @ " + sampleRate + "Hz");

            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                throw new IllegalStateException("Failed to open the default OpenAL device.");
            }

            context = alcCreateContext(device, (int[]) null);
            if (context == 0) {
                throw new IllegalStateException("Failed to create OpenAL context.");
            }

            alcMakeContextCurrent(context);
            AL.createCapabilities(ALC.createCapabilities(device));

            // Generate source and buffers
            source = alGenSources();
            buffers = new int[BUFFER_COUNT];
            alGenBuffers(buffers);

            // Check for errors
            checkALError("Initialization");

            System.out.println("OpenAL initialized successfully");

        } catch (Exception e) {
            System.err.println("Failed to initialize OpenAL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addAudioFrame(Frame frame) {
        if (frame == null || frame.samples == null || !playing.get()) return;

        try {
            ShortBuffer shortBuffer = (ShortBuffer) frame.samples[0];
            if (shortBuffer == null) return;

            short[] audioData = new short[shortBuffer.remaining()];
            shortBuffer.get(audioData);

            // Limit queue size to prevent memory issues
            synchronized (audioQueue) {
                if (audioQueue.size() < MAX_QUEUE_SIZE) {
                    audioQueue.offer(audioData);
                } else {
                    System.err.println("Audio queue full, dropping frame");
                }
            }

        } catch (Exception e) {
            System.err.println("Error adding audio frame: " + e.getMessage());
        }
    }

    private void startAudioThread() {
        if (audioThreadRunning.get()) return;

        audioThreadRunning.set(true);
        audioThread = new Thread(() -> {
            System.out.println("Audio thread started");

            // Pre-fill buffers
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (!fillAndQueueBuffer(buffers[i])) {
                    break;
                }
            }

            // Start playback
            alSourcePlay(source);
            checkALError("Starting playback");

            lastUpdateTime.set(System.currentTimeMillis());

            while (audioThreadRunning.get()) {
                try {
                    if (playing.get()) {
                        updateAudioStream();
                    }
                    Thread.sleep(5); // Small sleep to prevent busy waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in audio thread: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }

            System.out.println("Audio thread stopped");
        }, "Audio Stream Thread");

        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void updateAudioStream() {
        int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);

        for (int i = 0; i < processed; i++) {
            int buffer = alSourceUnqueueBuffers(source);
            if (buffer != 0) {
                if (!fillAndQueueBuffer(buffer)) {
                    // No more data, might need to stop
                    if (audioQueue.isEmpty()) {
                        // Could implement underrun handling here
                    }
                }
            }
        }

        // Ensure source is still playing
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        if (state != AL_PLAYING && !audioQueue.isEmpty()) {
            // Restart playback if we have data but source stopped
            alSourcePlay(source);
        }

        checkALError("Stream update");
    }

    private boolean fillAndQueueBuffer(int buffer) {
        short[] audioData = null;

        synchronized (audioQueue) {
            audioData = audioQueue.poll();
        }

        if (audioData == null) {
            return false;
        }

        // Convert to ByteBuffer in native order
        ByteBuffer pcm = ByteBuffer.allocateDirect(audioData.length * 2)
                .order(ByteOrder.nativeOrder());

        ShortBuffer shortBuffer = pcm.asShortBuffer();
        shortBuffer.put(audioData);
        pcm.limit(shortBuffer.position() * 2);

        // Determine format
        int format;
        if (channels == 1) {
            format = AL_FORMAT_MONO16;
        } else if (channels == 2) {
            format = AL_FORMAT_STEREO16;
        } else {
            System.err.println("Unsupported channel count: " + channels);
            return false;
        }

        // Upload to buffer
        alBufferData(buffer, format, pcm, sampleRate);

        // Queue buffer
        alSourceQueueBuffers(source, buffer);

        // Update sample count
        samplesPlayed.addAndGet(audioData.length / channels);

        return true;
    }

    public void start() {
        if (playing.get()) return;

        System.out.println("Starting audio playback");
        playing.set(true);

        if (!audioThreadRunning.get()) {
            startAudioThread();
        } else {
            alSourcePlay(source);
        }
    }

    public void pause() {
        if (!playing.get()) return;

        System.out.println("Pausing audio playback");
        playing.set(false);
        alSourcePause(source);
        checkALError("Pausing");
    }

    public void stop() {
        System.out.println("Stopping audio playback");
        playing.set(false);
        alSourceStop(source);

        // Clear queue
        synchronized (audioQueue) {
            audioQueue.clear();
        }

        // Clear processed buffers
        int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            alSourceUnqueueBuffers(source);
        }

        samplesPlayed.set(0);
        checkALError("Stopping");
    }

    public void setTime(double time) {
        stop();
        samplesPlayed.set((long)(time * sampleRate * channels));

        // Clear any pending audio data
        synchronized (audioQueue) {
            audioQueue.clear();
        }
    }

    public double getCurrentTime() {
        if (sampleRate == 0 || channels == 0) return 0.0;

        // Calculate time based on samples played
        long samples = samplesPlayed.get();

        // Account for buffers currently in OpenAL
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        if (state == AL_PLAYING || state == AL_PAUSED) {
            int offset = alGetSourcei(source, AL_SAMPLE_OFFSET);
            samples += offset;
        }

        return (double) samples / (sampleRate * channels);
    }

    private void checkALError(String operation) {
        int error = alGetError();
        if (error != AL_NO_ERROR) {
            String errorMsg;
            switch (error) {
                case AL_INVALID_NAME: errorMsg = "Invalid name"; break;
                case AL_INVALID_ENUM: errorMsg = "Invalid enum"; break;
                case AL_INVALID_VALUE: errorMsg = "Invalid value"; break;
                case AL_INVALID_OPERATION: errorMsg = "Invalid operation"; break;
                case AL_OUT_OF_MEMORY: errorMsg = "Out of memory"; break;
                default: errorMsg = "Unknown error"; break;
            }
            System.err.println("OpenAL error during " + operation + ": " + errorMsg + " (0x" + Integer.toHexString(error) + ")");
        }
    }

    public void close() {
        System.out.println("Closing audio player");

        playing.set(false);
        audioThreadRunning.set(false);

        if (audioThread != null) {
            audioThread.interrupt();
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        stop();

        if (source != 0) {
            alDeleteSources(source);
            source = 0;
        }

        if (buffers != null) {
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != 0) {
                    alDeleteBuffers(buffers[i]);
                    buffers[i] = 0;
                }
            }
        }

        if (context != 0) {
            alcDestroyContext(context);
            context = 0;
        }

        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }

        synchronized (audioQueue) {
            audioQueue.clear();
        }

        System.out.println("Audio player closed");
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public int getQueueSize() {
        synchronized (audioQueue) {
            return audioQueue.size();
        }
    }
}*/