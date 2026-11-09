package io.github.livetranslate;

import java.io.IOException;

/** 识别 + 翻译的统一接口，云端和本地各一个实现。 */
public interface Engine {
    String transcribe(short[] pcm, int len) throws Exception;

    String translate(String text) throws Exception;

    void close();
}
