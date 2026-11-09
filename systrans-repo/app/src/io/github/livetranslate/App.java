package io.github.livetranslate;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 记下上一次崩溃，下次启动直接显示出来，免得只能看到闪退。 */
public final class App extends Application {

    static final String CRASH_FILE = "last-crash.txt";
    private static File crashFile;

    @Override
    public void onCreate() {
        super.onCreate();
        crashFile = new File(getFilesDir(), CRASH_FILE);

        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                FileOutputStream fo = new FileOutputStream(crashFile);
                fo.write(sw.toString().getBytes("UTF-8"));
                fo.close();
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static File crashFile() {
        if (crashFile == null) return new File("last-crash.txt");
        return crashFile;
    }
}
