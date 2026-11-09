package io.github.livetranslate;

import android.content.Context;

import java.io.File;

/** 本地模型的三档配置。 */
public final class LocalModels {

    public static final class Tier {
        public final String name;
        public final String sub;
        public final String asrFile;
        public final String asrUrl;
        public final long asrSize;
        public final String mtFile;
        public final String mtUrl;
        public final long mtSize;

        Tier(String name, String sub, String asrFile, String asrUrl, long asrSize,
             String mtFile, String mtUrl, long mtSize) {
            this.name = name;
            this.sub = sub;
            this.asrFile = asrFile;
            this.asrUrl = asrUrl;
            this.asrSize = asrSize;
            this.mtFile = mtFile;
            this.mtUrl = mtUrl;
            this.mtSize = mtSize;
        }

        public long total() {
            return asrSize + mtSize;
        }

        public String sizeText() {
            return "约 " + ((asrSize + mtSize) / 1024 / 1024) + " MB";
        }
    }

    public static final Tier[] TIERS = {
            new Tier("轻量", "Whisper base + Qwen2.5-0.5B，最省电",
                    "ggml-base.bin",
                    "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
                    147_951_465L,
                    "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                    "https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                    397_000_000L),

            new Tier("均衡", "Whisper small + Qwen3-1.7B，推荐",
                    "ggml-small.bin",
                    "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
                    487_600_000L,
                    "Qwen3-1.7B-Q4_K_M.gguf",
                    "https://hf-mirror.com/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
                    1_107_400_000L),

            new Tier("高质量", "Whisper medium + Qwen2.5-3B，最准但更慢",
                    "ggml-medium-q5_0.bin",
                    "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
                    539_200_000L,
                    "qwen2.5-3b-instruct-q4_k_m.gguf",
                    "https://hf-mirror.com/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                    2_104_900_000L),
    };

    public static int clamp(int i) {
        return Math.max(0, Math.min(TIERS.length - 1, i));
    }

    public static String nameOf(int tier) {
        return TIERS[clamp(tier)].name;
    }

    public static File dir(Context c) {
        File d = new File(c.getExternalFilesDir(null), "models");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File asr(Context c, int tier) {
        return new File(dir(c), TIERS[clamp(tier)].asrFile);
    }

    public static File mt(Context c, int tier) {
        return new File(dir(c), TIERS[clamp(tier)].mtFile);
    }

    public static boolean ready(Context c, int tier) {
        Tier t = TIERS[clamp(tier)];
        return asr(c, tier).length() > (long) (t.asrSize * 0.9)
                && mt(c, tier).length() > (long) (t.mtSize * 0.9);
    }
}
