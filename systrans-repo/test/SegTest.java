package io.github.livetranslate;

import java.util.ArrayList;
import java.util.List;

/** 桌面端冒烟测试：断句逻辑 + WAV 头，不需要手机。 */
public class SegTest {
    public static void main(String[] a) throws Exception {
        final List<Integer> got = new ArrayList<>();
        Segmenter s = new Segmenter(new Segmenter.Listener() {
            public void onSegment(short[] pcm, int len) {
                got.add(len);
            }

            public void onLevel(int rms) {
            }
        });

        int frame = 320; // 20ms
        feed(s, frame, 60, 0);      // 1.2s 静音
        feed(s, frame, 100, 8000);  // 2.0s 说话
        feed(s, frame, 45, 0);      // 0.9s 静音 -> 触发切段

        System.out.println("segments=" + got.size());
        for (int len : got) {
            System.out.println("  len=" + len + " (" + (len / 16000.0) + "s)");
        }

        byte[] wav = Wav.encode(new short[1600], 1600, 16000);
        String riff = new String(wav, 0, 4, "US-ASCII");
        String wave = new String(wav, 8, 4, "US-ASCII");
        int rate = (wav[24] & 0xFF) | (wav[25] & 0xFF) << 8 | (wav[26] & 0xFF) << 16 | (wav[27] & 0xFF) << 24;
        int bits = (wav[34] & 0xFF) | (wav[35] & 0xFF) << 8;
        System.out.println("wav=" + riff + "/" + wave + " rate=" + rate + " bits=" + bits + " bytes=" + wav.length);

        boolean ok = got.size() == 1 && got.get(0) > 16000 && "RIFF".equals(riff) && rate == 16000 && wav.length == 44 + 3200;
        System.out.println(ok ? "SMOKE OK" : "SMOKE FAILED");
        if (!ok) System.exit(1);
    }

    static void feed(Segmenter s, int frame, int frames, int amp) {
        for (int i = 0; i < frames; i++) {
            short[] b = new short[frame];
            for (int j = 0; j < frame; j++) {
                b[j] = (short) (amp * Math.sin(2 * Math.PI * 800 * ((i * frame + j) / 16000.0)));
            }
            s.feed(b, frame);
        }
    }
}
