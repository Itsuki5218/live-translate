package io.github.livetranslate;

import android.content.Context;

import java.io.File;

/** 本地推理：Whisper 识别 + 小模型翻译。模型没下载时给出明确错误。 */
public final class LocalEngine implements Engine {

    private final Context app;
    private final Prefs prefs;
    private final int tier;
    private final Whisper whisper = new Whisper();
    private final Llama llama = new Llama();
    private boolean asrReady;
    private boolean mtReady;

    public LocalEngine(Context c, Prefs p) {
        this.app = c.getApplicationContext();
        this.prefs = p;
        this.tier = LocalModels.clamp(p.localTier);
    }

    private void ensure() throws Exception {
        if (!Whisper.available()) throw new Exception("本地识别库不可用");
        if (!asrReady) {
            File f = LocalModels.asr(app, tier);
            if (f.length() < 10_000_000L) throw new Exception("识别模型还没下载");
            asrReady = whisper.load(f.getAbsolutePath());
            if (!asrReady) throw new Exception("识别模型加载失败（文件 " +
                    (f.length() / 1024 / 1024) + " MB，可能不完整）");
        }
        if (!Llama.available()) throw new Exception("本地翻译库不可用");
        if (!mtReady) {
            File f = LocalModels.mt(app, tier);
            if (f.length() < 50_000_000L) throw new Exception("翻译模型还没下载");
            mtReady = llama.load(f.getAbsolutePath());
            if (!mtReady) throw new Exception("翻译模型加载失败（文件 " +
                    (f.length() / 1024 / 1024) + " MB，可能不完整）");
        }
    }

    @Override
    public String transcribe(short[] pcm, int len) throws Exception {
        ensure();
        return whisper.transcribe(pcm, len, prefs.srcLang);
    }

    @Override
    public String translate(String text) throws Exception {
        ensure();
        String sys = "你是字幕翻译引擎。把用户输入翻译成" + prefs.tgtLang + "，只输出译文。";
        String user = text;
        if (LocalModels.TIERS[tier].mtFile.toLowerCase().contains("qwen3")) {
            user = text + " /no_think";   // Qwen3 的思考开关
        }
        String out = llama.chat(sys, user, 200);
        if (out == null) return "";
        // 兜底：万一还是输出了思考过程，裁掉
        out = out.replaceAll("(?s) thinking.*?<｜end▁of▁thinking｜>", "").trim();
        if (out.startsWith("thinking")) {
            int idx = out.indexOf("<｜end▁of▁thinking｜>");
            if (idx > 0) out = out.substring(idx + 8).trim();
        }
        return out;
    }

    @Override
    public void close() {
        whisper.close();
        llama.close();
    }
}
