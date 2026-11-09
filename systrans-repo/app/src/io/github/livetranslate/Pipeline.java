package io.github.livetranslate;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 一段音频 -> 原文 -> 译文。网络/本地都由 Engine 决定。 */
public final class Pipeline {

    public interface Sink {
        void onOriginal(String text);

        void onTranslated(String source, String translation);

        void onStatus(String text);

        void onLevel(float level);
    }

    private final Context app;
    private final Prefs prefs;
    private final Sink sink;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile Engine engine;
    private volatile boolean alive = true;
    private String lastLine;

    public Pipeline(Context c, Prefs p, Sink sink) {
        this.app = c.getApplicationContext();
        this.prefs = p;
        this.sink = sink;
    }

    public void submit(short[] pcm, int len) {
        if (!alive) return;
        if (pending.get() >= 2) return; // 落后就丢，字幕宁缺毋滥
        final short[] copy = new short[len];
        System.arraycopy(pcm, 0, copy, 0, len);
        pending.incrementAndGet();
        worker.execute(() -> {
            try {
                process(copy, len);
            } catch (Exception e) {
                sink.onStatus(err(e));
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private void process(short[] pcm, int len) throws Exception {
        Engine e = engine();
        String text = e.transcribe(pcm, len);
        if (!alive || text == null) return;
        text = text.trim();
        if (text.length() < 2) return;
        sink.onOriginal(text);

        String tr = text;
        if (prefs.translate) {
            try {
                String r = e.translate(text);
                if (r != null && !r.trim().isEmpty()) tr = r.trim();
            } catch (Exception ex) {
                sink.onStatus("翻译失败，先显示原文");
            }
        }
        if (!alive) return;
        if (tr.equals(lastLine)) return;   // 重复的整句不再上屏
        lastLine = tr;
        sink.onTranslated(text, tr);
    }

    private synchronized Engine engine() {
        if (engine == null) {
            engine = Prefs.MODE_LOCAL.equals(prefs.mode)
                    ? new LocalEngine(app, prefs)
                    : new CloudEngine(prefs);
        }
        return engine;
    }

    private static String err(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        if (m.contains("Unable to resolve host") || m.contains("Failed to connect")
                || m.contains("Connection refused")) {
            return "网络连不上，检查接口地址或网络";
        }
        return m;
    }

    public void shutdown() {
        alive = false;
        worker.shutdownNow();
        if (engine != null) engine.close();
        engine = null;
    }
}
