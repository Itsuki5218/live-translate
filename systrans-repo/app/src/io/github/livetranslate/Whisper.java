package io.github.livetranslate;

/** whisper.cpp 的 JNI 封装。so 不存在时全部安全降级。 */
public final class Whisper {

    private static Boolean loaded;
    private long handle;

    public static synchronized boolean available() {
        if (loaded == null) {
            try {
                System.loadLibrary("whisperjni");
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
        handle = nativeInit(path);
        return handle != 0;
    }

    public synchronized String transcribe(short[] pcm, int len, String lang) {
        if (handle == 0) return "";
        return nativeTranscribe(handle, pcm, len, lang == null ? "" : lang);
    }

    public synchronized void close() {
        if (handle != 0) {
            nativeFree(handle);
            handle = 0;
        }
    }

    private static native long nativeInit(String modelPath);

    private static native String nativeTranscribe(long handle, short[] pcm, int len, String lang);

    private static native void nativeFree(long handle);
}
