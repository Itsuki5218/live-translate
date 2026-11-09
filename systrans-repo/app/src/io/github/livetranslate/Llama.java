package io.github.livetranslate;

/** llama.cpp 的 JNI 封装，用于本地翻译。 */
public final class Llama {

    private static Boolean loaded;
    private long model;
    private long ctx;

    public static synchronized boolean available() {
        if (loaded == null) {
            try {
                System.loadLibrary("llamajni");
                loaded = true;
            } catch (Throwable t) {
                loaded = false;
            }
        }
        return loaded;
    }

    public synchronized boolean load(String path) {
        if (!available()) return false;
        close();
        long[] out = nativeLoad(path, 1536);
        if (out == null || out.length < 2) return false;
        model = out[0];
        ctx = out[1];
        return model != 0 && ctx != 0;
    }

    public synchronized String chat(String system, String user, int maxTokens) {
        if (ctx == 0) return "";
        return nativeChat(ctx, system, user, maxTokens);
    }

    public synchronized void close() {
        if (ctx != 0 || model != 0) {
            nativeFree(model, ctx);
            ctx = 0;
            model = 0;
        }
    }

    private static native long[] nativeLoad(String modelPath, int nCtx);

    private static native String nativeChat(long ctx, String system, String user, int maxTokens);

    private static native void nativeFree(long model, long ctx);
}
