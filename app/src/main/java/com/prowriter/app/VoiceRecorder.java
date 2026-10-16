package com.prowriter.app;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Records a short voice message from the microphone (only while you're recording) into a small
 * AAC file in the app's private cache, then hands back the bytes and deletes the file.
 */
final class VoiceRecorder {

    static final String MIME_TYPE = "audio/aac";
    /** Longest recording; it stops by itself after this. */
    static final int MAX_MS = 90_000;

    interface AutoStop {
        void onMaxReached();
    }

    private final Context context;
    private MediaRecorder recorder;
    private File file;
    private long startedAt;

    VoiceRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isRecording() {
        return recorder != null;
    }

    long elapsedMs() {
        return recorder == null ? 0 : System.currentTimeMillis() - startedAt;
    }

    /** Starts recording. Returns false if the microphone can't be used right now. */
    @SuppressWarnings("deprecation")
    boolean start(AutoStop autoStop) {
        cancel();
        file = new File(context.getCacheDir(), "voice.aac");
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16_000);
            r.setAudioEncodingBitRate(32_000);
            r.setMaxDuration(MAX_MS);
            r.setOutputFile(file.getAbsolutePath());
            r.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && autoStop != null) {
                    autoStop.onMaxReached();
                }
            });
            r.prepare();
            r.start();
        } catch (Exception e) {
            r.release();
            deleteFile();
            return false;
        }
        recorder = r;
        startedAt = System.currentTimeMillis();
        return true;
    }

    /** Stops and returns the recording, or null if it was too short or failed. */
    byte[] stop() {
        MediaRecorder r = recorder;
        recorder = null;
        if (r == null) return null;
        boolean ok = true;
        try {
            r.stop();
        } catch (RuntimeException e) {
            ok = false; // stopped before any sound was recorded
        } finally {
            r.release();
        }
        byte[] data = null;
        if (ok && file != null && file.length() > 1000) {
            try {
                data = readAll(file);
            } catch (IOException ignored) {
                data = null;
            }
        }
        deleteFile();
        return data;
    }

    void cancel() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // nothing recorded
            }
            recorder.release();
            recorder = null;
        }
        deleteFile();
    }

    private void deleteFile() {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] out = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < out.length) {
                int n = in.read(out, off, out.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return out;
    }
}
