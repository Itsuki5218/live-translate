package io.github.livetranslate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 全部本地状态：模式、接口预设、语言、字幕外观、识别灵敏度。 */
public final class Prefs {

    public static final String MODE_CLOUD = "cloud";
    public static final String MODE_LOCAL = "local";

    public String mode = MODE_CLOUD;
    public int current = 0;
    public String srcLang = "";
    public String tgtLang = "简体中文";
    public int sensitivity = 50;      // 0..100，越高越容易判为说话
    public int overlayBgAlpha = 60;   // 0..100 每行荧光笔底框的不透明度
    public int overlayTextSize = 18;  // sp
    public int overlayHold = 10;      // 字幕停留秒数
    public int localTier = 1;         // 本地模型档位
    public int overlayTextColor = 0xFF12100D;
    public int overlayHighlight = 0xFFF2EDE3;
    public boolean overlayEnabled = true;
    public boolean translate = true;

    public final List<ApiPreset> presets = new ArrayList<>();

    public static Prefs load(Context c) {
        SharedPreferences p = c.getSharedPreferences("app", Context.MODE_PRIVATE);
        Prefs s = new Prefs();
        s.mode = p.getString("mode", MODE_CLOUD);
        s.current = p.getInt("current", 0);
        s.srcLang = p.getString("src", "");
        s.tgtLang = p.getString("tgt", "简体中文");
        s.sensitivity = p.getInt("sens", 50);
        s.overlayBgAlpha = p.getInt("ov_alpha", 60);
        // 旧版本整块底板 -> 现在每行一个荧光笔框
        if (!p.getBoolean("v4_boxes", false)) {
            s.overlayBgAlpha = 90;
            p.edit().putBoolean("v4_boxes", true).putInt("ov_alpha", 90).apply();
        }
        s.overlayTextSize = p.getInt("ov_size", 18);
        s.overlayHold = p.getInt("ov_hold", 10);
        s.localTier = p.getInt("tier", 1);
        s.overlayTextColor = p.getInt("ov_color", 0xFF12100D);
        s.overlayHighlight = p.getInt("ov_hl", 0xFFF2EDE3);
        // 纸面印刷配色：旧的白字会被迁移成墨字 + 纸底
        if (!p.getBoolean("v5_paper", false)) {
            s.overlayTextColor = 0xFF12100D;
            s.overlayHighlight = 0xFFF2EDE3;
            s.overlayBgAlpha = 95;
            p.edit().putBoolean("v5_paper", true)
                    .putInt("ov_color", 0xFF12100D)
                    .putInt("ov_hl", 0xFFF2EDE3)
                    .putInt("ov_alpha", 95).apply();
        }
        s.overlayEnabled = p.getBoolean("ov_on", true);
        s.translate = p.getBoolean("translate", true);

        String raw = p.getString("presets", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    s.presets.add(ApiPreset.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        }
        if (s.presets.isEmpty()) s.presets.addAll(ApiPreset.defaults());
        if (s.current < 0 || s.current >= s.presets.size()) s.current = 0;
        return s;
    }

    public void save(Context c) {
        JSONArray arr = new JSONArray();
        for (ApiPreset a : presets) arr.put(a.toJson());
        c.getSharedPreferences("app", Context.MODE_PRIVATE).edit()
                .putString("mode", mode)
                .putInt("current", current)
                .putString("src", srcLang)
                .putString("tgt", tgtLang)
                .putInt("sens", sensitivity)
                .putInt("ov_alpha", overlayBgAlpha)
                .putInt("ov_size", overlayTextSize)
                .putInt("ov_color", overlayTextColor)
                .putInt("ov_hl", overlayHighlight)
                .putInt("ov_hold", overlayHold)
                .putInt("tier", localTier)
                .putBoolean("ov_on", overlayEnabled)
                .putBoolean("translate", translate)
                .putString("presets", arr.toString())
                .apply();
    }

    public ApiPreset preset() {
        if (presets.isEmpty()) presets.addAll(ApiPreset.defaults());
        if (current < 0 || current >= presets.size()) current = 0;
        return presets.get(current);
    }

    /** 当前用于显示的名字，例如「云端 · 硅基流动」「本地 · Whisper base」。 */
    public String currentLabel() {
        if (MODE_LOCAL.equals(mode)) return "本地 · " + LocalModels.nameOf(localTier);
        return "云端 · " + preset().name;
    }

    public static final class ApiPreset {
        public String name;
        public String base;
        public String key;
        public String asr;
        public String chat;

        public ApiPreset(String name, String base, String key, String asr, String chat) {
            this.name = name;
            this.base = base;
            this.key = key;
            this.asr = asr;
            this.chat = chat;
        }

        public static List<ApiPreset> defaults() {
            List<ApiPreset> l = new ArrayList<>();
            l.add(new ApiPreset("硅基流动", "https://api.siliconflow.cn/v1", "",
                    "FunAudioLLM/SenseVoiceSmall", "tencent/Hunyuan-MT-7B"));
            l.add(new ApiPreset("OpenAI", "https://api.openai.com/v1", "",
                    "whisper-1", "gpt-4o-mini"));
            return l;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("name", name);
                o.put("base", base);
                o.put("key", key);
                o.put("asr", asr);
                o.put("chat", chat);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static ApiPreset fromJson(JSONObject o) {
            return new ApiPreset(o.optString("name", "接口"), o.optString("base", ""),
                    o.optString("key", ""), o.optString("asr", ""), o.optString("chat", ""));
        }
    }
}
