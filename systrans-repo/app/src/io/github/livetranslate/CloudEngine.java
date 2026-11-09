package io.github.livetranslate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** OpenAI 兼容接口：/audio/transcriptions + /chat/completions。 */
public final class CloudEngine implements Engine {

    private final Prefs.ApiPreset p;
    private final Prefs prefs;

    public CloudEngine(Prefs prefs) {
        this.prefs = prefs;
        this.p = prefs.preset();
    }

    @Override
    public String transcribe(short[] pcm, int len) throws Exception {
        if (p.key == null || p.key.trim().isEmpty()) throw new IOException("还没填 API Key");
        byte[] wav = Wav.encode(pcm, len, 16000);
        String boundary = "----lt" + System.nanoTime();

        HttpURLConnection c = open("/audio/transcriptions", false);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        DataOutputStream out = new DataOutputStream(c.getOutputStream());
        field(out, boundary, "model", p.asr);
        if (prefs.srcLang != null && !prefs.srcLang.trim().isEmpty()) {
            field(out, boundary, "language", prefs.srcLang.trim());
        }
        field(out, boundary, "response_format", "json");
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.wav\"\r\n");
        out.writeBytes("Content-Type: audio/wav\r\n\r\n");
        out.write(wav);
        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
        out.close();

        int code = c.getResponseCode();
        String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new IOException("识别接口 " + code + " " + brief(body));
        return new JSONObject(body).optString("text", "");
    }

    @Override
    public String translate(String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", p.chat);
        body.put("temperature", 0.2);
        body.put("max_tokens", 400);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system")
                .put("content", "你是实时字幕翻译引擎。把用户输入的字幕翻译成" + prefs.tgtLang
                        + "。只输出译文，不要解释、不要加引号、不要重复原文。若原文已是目标语言则原样输出。"));
        messages.put(new JSONObject().put("role", "user").put("content", text));
        body.put("messages", messages);

        HttpURLConnection c = open("/chat/completions", true);
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(45000);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new IOException("翻译接口 " + code + " " + brief(raw));
        return new JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "");
    }

    @Override
    public void close() {
    }

    private HttpURLConnection open(String path, boolean json) throws IOException {
        String base = p.base == null ? "" : p.base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.startsWith("http")) base = "https://" + base;
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Authorization", "Bearer " + (p.key == null ? "" : p.key.trim()));
        c.setRequestProperty("Accept", "application/json");
        if (json) c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        return c;
    }

    private static void field(DataOutputStream out, String b, String k, String v) throws IOException {
        out.writeBytes("--" + b + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + k + "\"\r\n\r\n");
        out.write(v.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String brief(String s) {
        if (s == null) return "";
        return s.length() > 240 ? s.substring(0, 240) : s;
    }
}
