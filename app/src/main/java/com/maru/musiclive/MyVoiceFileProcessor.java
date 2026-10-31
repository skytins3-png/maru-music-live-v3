package com.maru.musiclive;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decode an OWNED voice recording (MP3/M4A/WAV, depending on device decoders),
 * clean its PCM, and write a local WAV. Never send audio or metadata anywhere.
 * 90-second limit protects memory / battery, without touching the input recording.
 */
public final class MyVoiceFileProcessor {
    public static final int MAX_SECONDS = 90;
    public static final String OUTPUT_NAME = "my_voice_enhanced.wav";
    private MyVoiceFileProcessor() {}

    public static File output(Context ctx) {
        File folder = new File(ctx.getFilesDir(), "my_voice_private");
        return new File(folder, OUTPUT_NAME);
    }

    public static File enhance(Context ctx, Uri input) throws IOException {
        if (ctx == null || input == null) throw new IOException("Choose your own voice file.");
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            android.os.ParcelFileDescriptor fd = ctx.getContentResolver().openFileDescriptor(input, "r");
            if (fd == null) throw new IOException("The selected recording cannot be opened.");
            try {
                extractor.setDataSource(fd.getFileDescriptor());
            } finally {
                fd.close();
            }
            int track = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i; inputFormat = f; break;
                }
            }
            if (track < 0 || inputFormat == null) throw new IOException("No audio track in the file.");
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("Unknown recording format.");
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    && inputFormat.getLong(MediaFormat.KEY_DURATION) > MAX_SECONDS * 1_000_000L) {
                throw new IOException("Please choose a recording under 90 seconds.");
            }
            extractor.selectTrack(track);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();
            boolean sentEnd = false, receivedEnd = false;
            int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            if (channels < 1 || channels > 8) throw new IOException("Unsupported channel count.");
            if (sampleRate < 8000 || sampleRate > 192000)
                throw new IOException("Unsupported sample rate.");
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int idle = 0;
            final long hardMaxSamples = (long) sampleRate * MAX_SECONDS;
            while (!receivedEnd) {
                if (!sentEnd) {
                    int index = decoder.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer in = decoder.getInputBuffer(index);
                        if (in == null) throw new IOException("Decoder input unavailable.");
                        in.clear();
                        int bytes = extractor.readSampleData(in, 0);
                        if (bytes < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sentEnd = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, bytes,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int index = decoder.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = decoder.getOutputFormat();
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (channels < 1 || channels > 8 || sampleRate < 8000 || sampleRate > 192000)
                        throw new IOException("Unsupported decoded recording.");
                    int encoding = out.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT)
                        throw new IOException("Device decoder did not provide 16-bit PCM.");
                } else if (index >= 0) {
                    ByteBuffer out = decoder.getOutputBuffer(index);
                    if (out == null) throw new IOException("Decoder output unavailable.");
                    if (info.size > 0) {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        if (info.size % (2 * channels) != 0)
                            throw new IOException("Invalid decoded PCM frame.");
                        if ((raw.size() + (long) info.size) / (2L * channels) > hardMaxSamples)
                            throw new IOException("Recording exceeds 90 seconds.");
                        byte[] chunk = new byte[info.size];
                        out.get(chunk);
                        raw.write(chunk, 0, chunk.length);
                    }
                    receivedEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(index, false);
                    idle = 0;
                } else if (++idle > 3000) {
                    throw new IOException("Decoder took too long.");
                }
            }
            byte[] pcm = raw.toByteArray();
            if (pcm.length == 0) throw new IOException("Empty voice recording.");
            ByteBuffer frames = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            int n = pcm.length / (2 * channels);
            short[] mono = new short[n];
            for (int i = 0; i < n; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) sum += frames.getShort();
                mono[i] = (short) (sum / channels);
            }
            short[] cleaned = MyVoiceDsp.enhance(mono, sampleRate);
            File finalFile = output(ctx);
            File parent = finalFile.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs()))
                throw new IOException("Unable to create private output folder.");
            File temp = new File(parent, OUTPUT_NAME + ".tmp");
            try {
                try (BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(temp))) {
                    writeWav(dest, cleaned, sampleRate);
                }
                if (finalFile.exists() && !finalFile.delete())
                    throw new IOException("Could not replace the previous enhanced copy.");
                if (!temp.renameTo(finalFile))
                    throw new IOException("Could not save enhanced WAV.");
                return finalFile;
            } finally {
                if (temp.exists()) temp.delete();
            }
        } catch (RuntimeException error) {
            throw new IOException("Could not decode this recording: " + error.getMessage(), error);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (RuntimeException ignored) {}
                decoder.release();
            }
            extractor.release();
        }
    }

    private static void put16(java.io.OutputStream out, int value) throws IOException {
        out.write(value & 255); out.write((value >>> 8) & 255);
    }
    private static void put32(java.io.OutputStream out, int value) throws IOException {
        put16(out, value); put16(out, value >>> 16);
    }
    private static void writeWav(java.io.OutputStream out, short[] pcm, int rate)
            throws IOException {
        int dataBytes = pcm.length * 2;
        out.write(new byte[]{'R','I','F','F'});
        put32(out, dataBytes + 36);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '});
        put32(out, 16); put16(out, 1); put16(out, 1);
        put32(out, rate); put32(out, rate * 2); put16(out, 2); put16(out, 16);
        out.write(new byte[]{'d','a','t','a'});
        put32(out, dataBytes);
        for (short sample : pcm) put16(out, sample & 65535);
    }
}
