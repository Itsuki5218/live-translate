package io.github.livetranslate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 用本地 mock 服务验证「识别 -> 翻译」这两跳的 HTTP 逻辑。 */
public class PipeTest {
    public static void main(String[] a) throws Exception {
        Config cfg = new Config();
        cfg.baseUrl = "http://127.0.0.1:8765/v1/"; // 故意带尾斜杠
        cfg.apiKey = "sk-test";
        cfg.asrModel = "fake-asr";
        cfg.chatModel = "fake-chat";
        cfg.srcLang = "en";
        cfg.tgtLang = "简体中文";
        cfg.translate = true;
        cfg.subtitle = true;

        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> src = new AtomicReference<>();
        final AtomicReference<String> dst = new AtomicReference<>();
        final AtomicReference<String> err = new AtomicReference<>();

        Pipeline p = new Pipeline(cfg, new Pipeline.Sink() {
            public void onOriginal(String text) {
                src.set(text);
            }

            public void onTranslated(String source, String translation) {
                dst.set(translation);
                done.countDown();
            }

            public void onStatus(String text) {
                err.set(text);
                done.countDown();
            }
        });

        short[] pcm = new short[16000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (6000 * Math.sin(2 * Math.PI * 440 * i / 16000.0));
        }
        p.submit(pcm, pcm.length);

        boolean ok = done.await(20, TimeUnit.SECONDS);
        System.out.println("finished=" + ok);
        System.out.println("src=" + src.get());
        System.out.println("dst=" + dst.get());
        System.out.println("err=" + err.get());

        boolean pass = ok && "Hello world, this is a test.".equals(src.get())
                && "你好世界，这是一次测试。".equals(dst.get());
        System.out.println(pass ? "PIPE OK" : "PIPE FAILED");
        p.shutdown();
        if (!pass) System.exit(1);
    }
}
