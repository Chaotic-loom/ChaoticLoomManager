package com.chaoticloom.clm.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static com.chaoticloom.clm.ChaoticLoomManager.LOGGER;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;


public class AudioPlayer {
    private static final int BUFFER_COUNT = 8; // Increased buffer count
    private static final int SAMPLES_PER_BUFFER = 8192; // Samples per channel per buffer

    private FFmpegFrameGrabber audioGrabber;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // OpenAL objects
    private long device;
    private long context;
    private int source;
    private int[] buffers;

    // Audio properties
    private int sampleRate;
    private int channels;
    private int alFormat;

    // Threading
    private Thread audioThread;

    // Synchronization
    private volatile boolean shouldStop = false;
    private boolean looping = false;

    public AudioPlayer(String filePath) {
        try {
            LOGGER.info("Initializing audio player for: {}", filePath);

            // Initialize audio grabber
            audioGrabber = new FFmpegFrameGrabber(filePath);
            // Set audio format to signed 16-bit PCM
            audioGrabber.setSampleFormat(org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16);
            audioGrabber.start();

            // Get audio properties
            sampleRate = audioGrabber.getSampleRate();
            channels = audioGrabber.getAudioChannels();

            if (sampleRate <= 0) {
                LOGGER.warn("No audio stream found in video");
                audioGrabber.close();
                return;
            }

            LOGGER.info("Audio properties - Sample Rate: {}Hz, Channels: {}, Format: S16", sampleRate, channels);

            // Determine OpenAL format
            if (channels == 1) {
                alFormat = AL_FORMAT_MONO16;
            } else if (channels == 2) {
                alFormat = AL_FORMAT_STEREO16;
            } else {
                throw new IllegalStateException("Unsupported channel count: " + channels);
            }

            // Initialize OpenAL
            initializeOpenAL();

            initialized.set(true);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize audio player", e);
            cleanup();
        }
    }

    private void initializeOpenAL() {
        try {
            // Open default audio device
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                throw new IllegalStateException("Failed to open OpenAL device");
            }

            // Create context
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0) {
                throw new IllegalStateException("Failed to create OpenAL context");
            }

            alcMakeContextCurrent(context);
            AL.createCapabilities(ALC.createCapabilities(device));

            // Generate source
            source = alGenSources();
            checkALError("Generate source");

            // Configure source
            alSourcef(source, AL_PITCH, 1.0f);
            alSourcef(source, AL_GAIN, 1.0f);
            alSource3f(source, AL_POSITION, 0, 0, 0);
            alSource3f(source, AL_VELOCITY, 0, 0, 0);
            alSourcei(source, AL_LOOPING, AL_FALSE);

            // Generate buffers
            buffers = new int[BUFFER_COUNT];
            for (int i = 0; i < BUFFER_COUNT; i++) {
                buffers[i] = alGenBuffers();
            }
            checkALError("Generate buffers");

            LOGGER.info("OpenAL initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize OpenAL", e);
            throw new RuntimeException(e);
        }
    }

    public void play() {
        if (!initialized.get()) {
            LOGGER.warn("Audio player not initialized - video may not have audio track");
            return;
        }

        if (playing.get() && audioThread != null && audioThread.isAlive()) {
            return;
        }

        playing.set(true);
        shouldStop = false;

        audioThread = new Thread(this::audioLoop, "Audio-Decoder-Thread");
        audioThread.setDaemon(true);
        audioThread.start();

        LOGGER.info("Audio playback started");
    }

    public void pause() {
        if (playing.get()) {
            playing.set(false);
            if (source != 0) {
                alSourcePause(source);
            }
            joinAudioThread();
            LOGGER.info("Audio paused");
        }
    }

    public void stop() {
        playing.set(false);
        shouldStop = true;

        if (source != 0) {
            alSourceStop(source);
        }

        joinAudioThread();

        try {
            if (audioGrabber != null) {
                audioGrabber.setTimestamp(0);
            }
        } catch (Exception e) {
            LOGGER.error("Error resetting audio position", e);
        }

        LOGGER.info("Audio stopped");
    }

    private void joinAudioThread() {
        if (audioThread != null && audioThread.isAlive()) {
            try {
                audioThread.join(1000);
                if (audioThread.isAlive()) {
                    LOGGER.warn("Audio thread did not stop in time, interrupting.");
                    audioThread.interrupt();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while joining audio thread", e);
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }
    }

    private void audioLoop() {
        LOGGER.debug("Audio decoder thread started");

        try {
            // Fill initial buffers
            int bufferedCount = 0;
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (streamBuffer(buffers[i])) {
                    bufferedCount++;
                } else {
                    break;
                }
            }

            if (bufferedCount == 0) {
                LOGGER.error("Failed to buffer any audio data");
                playing.set(false);
                return;
            }

            // Queue all filled buffers to source
            if (bufferedCount == buffers.length) {
                // Queue all buffers at once
                alSourceQueueBuffers(source, buffers);
            } else {
                // Queue only the filled buffers
                int[] filledBuffers = new int[bufferedCount];
                System.arraycopy(buffers, 0, filledBuffers, 0, bufferedCount);
                alSourceQueueBuffers(source, filledBuffers);
            }
            checkALError("Queue initial buffers");

            // Start playback
            alSourcePlay(source);
            checkALError("Start playback");

            LOGGER.debug("Audio playback started with {} buffers", bufferedCount);

            // Streaming loop
            while (playing.get() && !shouldStop) {
                // Check how many buffers have been processed
                int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);

                // Refill processed buffers
                while (processed > 0 && playing.get()) {
                    int buffer = alSourceUnqueueBuffers(source);
                    checkALError("Unqueue buffer");

                    if (streamBuffer(buffer)) {
                        alSourceQueueBuffers(source, buffer);
                        checkALError("Queue buffer");
                    } else {
                        // End of stream
                        if (looping) {
                            try {
                                audioGrabber.setTimestamp(0);
                                if (streamBuffer(buffer)) {
                                    alSourceQueueBuffers(source, buffer);
                                }
                            } catch (Exception e) {
                                LOGGER.error("Error looping audio", e);
                                playing.set(false);
                            }
                        } else {
                            // Don't re-queue, let it finish
                            LOGGER.debug("Audio stream ended");
                        }
                    }

                    processed--;
                }

                // Ensure source is still playing (recover from underrun)
                int state = alGetSourcei(source, AL_SOURCE_STATE);
                if (state != AL_PLAYING && playing.get()) {
                    int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
                    if (queued > 0) {
                        alSourcePlay(source);
                        LOGGER.debug("Restarted audio playback (buffer underrun recovery)");
                    } else {
                        // No more buffers, end of stream
                        playing.set(false);
                        LOGGER.debug("Audio playback finished");
                    }
                }

                // Sleep briefly to avoid busy waiting
                LockSupport.parkNanos(10_000_000); // 10ms
            }

        } catch (Exception e) {
            LOGGER.error("Exception in audio decoder loop", e);
        } finally {
            // Stop source and clear buffers
            if (source != 0) {
                alSourceStop(source);

                // Unqueue all remaining buffers
                int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
                if (queued > 0) {
                    int[] tempBuffers = new int[queued];
                    for (int i = 0; i < queued; i++) {
                        tempBuffers[i] = alSourceUnqueueBuffers(source);
                    }
                }
            }
        }

        LOGGER.debug("Audio decoder thread stopped");
    }

    private boolean streamBuffer(int buffer) {
        try {
            // Calculate total samples needed (per channel)
            int totalSamplesNeeded = SAMPLES_PER_BUFFER;

            // Allocate buffer for audio data (16-bit samples, so 2 bytes per sample)
            // Total size = samples * channels * 2 bytes per sample
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(totalSamplesNeeded * channels * 2);
            // CRITICAL: Ensure native byte order for OpenAL
            byteBuffer.order(java.nio.ByteOrder.nativeOrder());

            int samplesWritten = 0;
            int framesProcessed = 0;

            // Read audio frames until we have enough data
            while (samplesWritten < totalSamplesNeeded && playing.get()) {
                // Grab only audio frames
                Frame audioFrame = audioGrabber.grabFrame(true, false, true, false);

                if (audioFrame == null) {
                    // End of stream
                    break;
                }

                if (audioFrame.samples == null || audioFrame.samples.length == 0) {
                    continue;
                }

                // Get audio samples from frame - they're already in S16 format
                ShortBuffer frameData = (ShortBuffer) audioFrame.samples[0];
                if (frameData == null || !frameData.hasRemaining()) {
                    continue;
                }

                framesProcessed++;

                // CRITICAL: The ShortBuffer position might not be at 0
                frameData.rewind();

                // The samples are interleaved (L,R,L,R for stereo)
                // Total values = samples * channels
                int totalValues = frameData.remaining();
                int frameSamples = totalValues / channels;

                // Calculate how many samples we can copy
                int samplesToCopy = Math.min(frameSamples, totalSamplesNeeded - samplesWritten);
                int valuesToCopy = samplesToCopy * channels;

                // Debug first buffer only
                if (framesProcessed == 1) {
                    LOGGER.debug("First audio frame: {} values ({} samples/channel), copying {} values",
                            totalValues, frameSamples, valuesToCopy);
                }

                // Copy the samples directly
                for (int i = 0; i < valuesToCopy && frameData.hasRemaining(); i++) {
                    short sample = frameData.get();
                    byteBuffer.putShort(sample);
                }

                samplesWritten += samplesToCopy;
            }

            if (samplesWritten == 0) {
                MemoryUtil.memFree(byteBuffer);
                return false;
            }

            // Debug info
            if (framesProcessed > 0) {
                LOGGER.debug("Buffered {} samples from {} frames for OpenAL", samplesWritten, framesProcessed);
            }

            // Prepare buffer for OpenAL
            byteBuffer.flip();

            // Verify we have data
            if (byteBuffer.remaining() == 0) {
                LOGGER.error("ByteBuffer is empty after flip!");
                MemoryUtil.memFree(byteBuffer);
                return false;
            }

            // Upload to OpenAL buffer
            alBufferData(buffer, alFormat, byteBuffer, sampleRate);
            checkALError("Buffer data");

            MemoryUtil.memFree(byteBuffer);

            return true;

        } catch (Exception e) {
            LOGGER.error("Error streaming audio buffer", e);
            return false;
        }
    }

    public void setLooping(boolean loop) {
        this.looping = loop;
    }

    public boolean isLooping() {
        return looping;
    }

    public boolean isPlaying() {
        if (!initialized.get() || source == 0) {
            return false;
        }
        return playing.get() && alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    public void setVolume(float volume) {
        if (source != 0) {
            alSourcef(source, AL_GAIN, Math.max(0.0f, Math.min(1.0f, volume)));
        }
    }

    public float getVolume() {
        if (source != 0) {
            return alGetSourcef(source, AL_GAIN);
        }
        return 0.0f;
    }

    private void checkALError(String operation) {
        int error = alGetError();
        if (error != AL_NO_ERROR) {
            String errorMsg = switch (error) {
                case AL_INVALID_NAME -> "Invalid name";
                case AL_INVALID_ENUM -> "Invalid enum";
                case AL_INVALID_VALUE -> "Invalid value";
                case AL_INVALID_OPERATION -> "Invalid operation";
                case AL_OUT_OF_MEMORY -> "Out of memory";
                default -> "Unknown error (" + error + ")";
            };
            LOGGER.error("OpenAL Error during {}: {}", operation, errorMsg);
        }
    }

    public void cleanup() {
        stop();

        try {
            if (audioGrabber != null) {
                audioGrabber.close();
                audioGrabber = null;
            }

            if (source != 0) {
                alDeleteSources(source);
                source = 0;
            }

            if (buffers != null) {
                for (int buffer : buffers) {
                    if (buffer != 0) {
                        alDeleteBuffers(buffer);
                    }
                }
                buffers = null;
            }

            if (context != 0) {
                alcDestroyContext(context);
                context = 0;
            }

            if (device != 0) {
                alcCloseDevice(device);
                device = 0;
            }

            initialized.set(false);
            LOGGER.info("Audio player cleaned up");
        } catch (Exception e) {
            LOGGER.error("Error during audio player cleanup", e);
        }
    }
}