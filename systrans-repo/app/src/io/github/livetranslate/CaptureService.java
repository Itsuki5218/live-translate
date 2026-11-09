package io.github.livetranslate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

/** 前台服务：抓系统声音 -> 断句 -> 识别翻译 -> 悬浮歌词。 */
public final class CaptureService extends Service {

    public static final String ACTION_START = "io.github.livetranslate.START";
    public static final String ACTION_STOP = "io.github.livetranslate.STOP";

    private static final String CHANNEL = "capture";
    private static final int NOTIF_ID = 42;

    static volatile boolean running;
    static volatile String status = "";
    static volatile float level;
    static volatile String label = "";

    private MediaProjection projection;
    private AudioRecord record;
    private Thread worker;
    private volatile boolean alive;
    private Pipeline pipeline;
    private Segmenter segmenter;
    private Overlay overlay;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;

        startForegroundCompat();
        status = "启动中";
        label = Prefs.load(this).currentLabel();

        Intent data = intent.getParcelableExtra("data");
        int code = intent.getIntExtra("code", 0);
        if (data == null) {
            fail("没有拿到录屏授权");
            return START_NOT_STICKY;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) {
            fail("录屏授权已失效，请重新开始");
            return START_NOT_STICKY;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, null);

        final Prefs prefs = Prefs.load(this);
        overlay = Overlay.get(this);
        overlay.configure(prefs.overlayTextSize, prefs.overlayBgAlpha, prefs.overlayHold,
                prefs.overlayTextColor, prefs.overlayHighlight);
        if (prefs.overlayEnabled) overlay.show();
        overlay.status("正在启动…");

        pipeline = new Pipeline(this, prefs, new Pipeline.Sink() {
            @Override
            public void onOriginal(String text) {
                status = text;
            }

            @Override
            public void onTranslated(String source, String translation) {
                status = translation;
                overlay.push(source, translation);
            }

            @Override
            public void onStatus(String text) {
                status = text;
                overlay.status(text);
            }

            @Override
            public void onLevel(float l) {
                level = l;
            }
        });

        segmenter = new Segmenter(new Segmenter.Listener() {
            @Override
            public void onSegment(short[] pcm, int len) {
                pipeline.submit(pcm, len);
            }

            @Override
            public void onLevel(int rms) {
                level = Math.min(1f, rms / 5200f);
            }
        });
        segmenter.setSensitivity(prefs.sensitivity);

        alive = true;
        running = true;
        worker = new Thread(this::captureLoop, "audio-capture");
        worker.start();
        return START_NOT_STICKY;
    }

    private void captureLoop() {
        try {
            AudioPlaybackCaptureConfiguration cap = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            int min = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(min * 2, 16384))
                    .setAudioPlaybackCaptureConfig(cap)
                    .build();

            record.startRecording();
            status = "正在聆听系统声音";
            if (overlay != null) overlay.status("正在聆听系统声音…");

            short[] frame = new short[320];
            while (alive) {
                int n = record.read(frame, 0, frame.length);
                if (n > 0) segmenter.feed(frame, n);
                else if (n < 0) break;
            }
        } catch (SecurityException e) {
            fail("没有录音权限，或系统拒绝了内录");
        } catch (Exception e) {
            fail("内录失败：" + e.getClass().getSimpleName());
        }
    }

    private void fail(String why) {
        status = why;
        if (overlay != null) overlay.status(why);
        stopSelf();
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "实时听译", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, HomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("同声译正在听")
                .setContentText("系统声音 → 字幕翻译")
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher),
                        "停止", stopPi).build())
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        status = "已停止";
        level = 0f;

        if (segmenter != null) segmenter.flush();
        if (record != null) {
            try {
                record.stop();
            } catch (Exception ignored) {
            }
            record.release();
            record = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
            }
            projection = null;
        }
        if (pipeline != null) pipeline.shutdown();
        if (worker != null) worker.interrupt();

        Overlay o = Overlay.get(this);
        o.status("已停止");
        o.hide();
        stopForeground(true);
        super.onDestroy();
    }
}
