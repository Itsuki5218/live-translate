package io.github.livetranslate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 把 PCM16 单声道样本包成 WAV，接口要求带容器头。 */
final class Wav {
    static byte[] encode(short[] pcm, int len, int sampleRate) {
        int dataLen = len * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataLen);
        try {
            writeAscii(out, "RIFF");
            writeInt(out, 36 + dataLen);
            writeAscii(out, "WAVEfmt ");
            writeInt(out, 16);
            writeShort(out, 1);              // PCM
            writeShort(out, 1);              // mono
            writeInt(out, sampleRate);
            writeInt(out, sampleRate * 2);   // byte rate
            writeShort(out, 2);              // block align
            writeShort(out, 16);             // bits
            writeAscii(out, "data");
            writeInt(out, dataLen);
            for (int i = 0; i < len; i++) {
                writeShort(out, pcm[i]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream o, String s) throws IOException {
        o.write(s.getBytes("US-ASCII"));
    }

    private static void writeInt(ByteArrayOutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }
}
