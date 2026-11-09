package io.github.livetranslate;

import java.util.ArrayDeque;

/**
 * 基于能量的断句：连续说话超过 0.6 秒、停 0.7 秒就切一段，
 * 单段最长 9 秒。带 160ms 的前导，避免吃字。
 */
final class Segmenter {

    interface Listener {
        void onSegment(short[] pcm, int len);

        void onLevel(int rms);
    }

    private static final int RATE = 16000;
    private static final int MIN_LEN = (int) (RATE * 0.8);          // 至少 0.5s 才值得送
    private static final int MAX_LEN = (int) (RATE * 2.5);          // 最长 9s
    private static final int SILENCE_FRAMES = 18;         // 0.7s @20ms
    private static final int PREROLL = 8;                 // 0.16s

    private final Listener listener;
    private final ArrayDeque<short[]> pre = new ArrayDeque<>();
    private final short[] buf = new short[MAX_LEN + RATE];
    private int len;
    private int speechEnd;
    private int silence;
    private boolean speaking;
    private float noise = 300f;
    private float mult = 3.0f;
    private float floor = 380f;

    Segmenter(Listener listener) {
        this.listener = listener;
    }

    /** 0..100，越高越灵敏（更容易把小声判成说话）。 */
    void setSensitivity(int s) {
        s = Math.max(0, Math.min(100, s));
        mult = 4.6f - (3.0f * s / 100f);
        floor = 620f - (400f * s / 100f);
    }

    void feed(short[] frame, int n) {
        short[] f = new short[n];
        System.arraycopy(frame, 0, f, 0, n);

        double sum = 0;
        for (int i = 0; i < n; i++) sum += (double) f[i] * f[i];
        int rms = (int) Math.sqrt(sum / n);
        listener.onLevel(rms);

        float threshold = Math.max(noise * mult, floor);

        if (!speaking) {
            pre.addLast(f);
            while (pre.size() > PREROLL) pre.removeFirst();
            if (rms > threshold) {
                speaking = true;
                silence = 0;
                len = 0;
                for (short[] p : pre) append(p);
                pre.clear();
                speechEnd = len;
            } else {
                noise = noise * 0.97f + rms * 0.03f;
            }
            return;
        }

        append(f);
        if (rms > threshold) {
            silence = 0;
            speechEnd = len;
        } else {
            silence++;
            if (silence >= SILENCE_FRAMES) {
                speaking = false;
                int keep = Math.min(len, speechEnd + RATE / 20); // 尾巴留 50ms
                if (keep >= MIN_LEN) listener.onSegment(buf, keep);
                len = 0;
                noise = noise * 0.97f + rms * 0.03f;
            }
        }
        if (len >= MAX_LEN) {
            listener.onSegment(buf, len);
            len = 0;
            speechEnd = 0;
        }
    }

    private void append(short[] f) {
        int room = buf.length - len;
        if (room <= 0) return;
        int n = Math.min(f.length, room);
        System.arraycopy(f, 0, buf, len, n);
        len += n;
    }

    void flush() {
        int keep = Math.min(len, speechEnd + RATE / 20);
        if (speaking && keep >= MIN_LEN) listener.onSegment(buf, keep);
        len = 0;
        speechEnd = 0;
        speaking = false;
    }
}
