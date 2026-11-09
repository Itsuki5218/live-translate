package io.github.livetranslate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面：技术文献式的纸面终端。 */
public final class HomeActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_AUDIO = 1002;
    private static final int REQ_NOTIF = 1003;

    private Prefs prefs;
    private View stamp;
    private TextView tvAction, tvActionSub, tvState, status;
    private TextView tvSrc, tvSrcMeta, tvTgt, tvTgtMeta, tvModel;
    private final Handler ui = new Handler();
    private boolean resumeAfterOverlay;
    private boolean motionPlayed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.load(this);
        checkLastCrash();

        try {
            setContentView(R.layout.home);
        } catch (Throwable t) {
            fallback(t);
            return;
        }

        stamp = findViewById(R.id.stamp);
        tvAction = findViewById(R.id.tv_action);
        tvActionSub = findViewById(R.id.tv_action_sub);
        tvState = findViewById(R.id.tv_state);
        status = findViewById(R.id.status);
        tvSrc = findViewById(R.id.tv_src);
        tvSrcMeta = findViewById(R.id.tv_src_meta);
        tvTgt = findViewById(R.id.tv_tgt);
        tvTgtMeta = findViewById(R.id.tv_tgt_meta);
        tvModel = findViewById(R.id.tv_model);

        stamp.setOnClickListener(v -> toggle());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.pill_model).setOnClickListener(v ->
                new SheetDialog(this, prefs, this::refresh).show());
        findViewById(R.id.pill_src).setOnClickListener(v ->
                LangPicker.show(this, v, true, prefs.srcLang, value -> {
                    prefs.srcLang = value;
                    prefs.save(this);
                    refresh();
                }));
        findViewById(R.id.pill_tgt).setOnClickListener(v ->
                LangPicker.show(this, v, false, prefs.tgtLang, value -> {
                    prefs.tgtLang = value;
                    prefs.save(this);
                    refresh();
                }));

        refresh();
        dropStamp();
    }

    /** 印章从右上落下，直线、无回弹。 */
    private void dropStamp() {
        if (motionPlayed) return;
        motionPlayed = true;
        float d = getResources().getDisplayMetrics().density;
        stamp.setTranslationX(26 * d);
        stamp.setTranslationY(-26 * d);
        stamp.setAlpha(0f);
        stamp.animate().translationX(0).translationY(0).alpha(1f)
                .setDuration(200).setInterpolator(new LinearInterpolator()).start();
    }

    /** 通知从底部左侧滑入。 */
    private void notice(String text) {
        status.setText(text);
        float d = getResources().getDisplayMetrics().density;
        status.setTranslationX(-12 * d);
        status.setTranslationY(6 * d);
        status.setAlpha(0.2f);
        status.animate().translationX(0).translationY(0).alpha(1f)
                .setDuration(160).setInterpolator(new LinearInterpolator()).start();
    }

    private void fallback(Throwable t) {
        TextView tv = new TextView(this);
        tv.setBackgroundColor(0xFFF2EDE3);
        tv.setTextColor(0xFF12100D);
        tv.setPadding(60, 200, 60, 40);
        tv.setTextSize(12);
        StringBuilder sb = new StringBuilder(t.toString()).append("\n\n");
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(8, st.length); i++) sb.append(st[i]).append('\n');
        tv.setText(sb.toString());
        setContentView(tv);
    }

    private void checkLastCrash() {
        java.io.File f = App.crashFile();
        if (!f.exists()) return;
        String text = "";
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable ignored) {
        }
        f.delete();
        startActivity(new Intent(this, CrashActivity.class).putExtra("text", text));
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs = Prefs.load(this);
        refresh();
        ui.post(ticker);
        if (resumeAfterOverlay && Settings.canDrawOverlays(this)) {
            resumeAfterOverlay = false;
            begin();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    private void refresh() {
        tvSrc.setText(labelOfSource(prefs.srcLang));
        tvSrcMeta.setText("SOURCE · " + codeOfSource(prefs.srcLang));
        tvTgt.setText(prefs.tgtLang == null || prefs.tgtLang.isEmpty() ? "简体中文" : prefs.tgtLang);
        tvTgtMeta.setText("TARGET · " + codeOfTarget(prefs.tgtLang));
        tvModel.setText(prefs.currentLabel());
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean running = CaptureService.running;
            if (running) {
                stamp.setBackgroundResource(R.drawable.stamp_run);
                tvAction.setText("停止翻译");
                tvActionSub.setText("STOP");
                tvState.setText("LISTENING / 聆听");
                String s = CaptureService.status;
                if (s != null && !s.isEmpty() && !s.equals(status.getText().toString())) {
                    notice(s);
                }
            } else {
                stamp.setBackgroundResource(R.drawable.stamp_idle);
                tvAction.setText("开始翻译");
                tvActionSub.setText("START");
                tvState.setText("IDLE");
            }
            ui.postDelayed(this, 300);
        }
    };

    private void toggle() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
            notice("已停止 / STOPPED");
            return;
        }
        begin();
    }

    private void begin() {
        prefs = Prefs.load(this);

        if (Prefs.MODE_CLOUD.equals(prefs.mode) && prefs.preset().key.trim().isEmpty()) {
            toast("先选一个接口并填上 Key");
            new SheetDialog(this, prefs, this::refresh).show();
            return;
        }
        if (Prefs.MODE_LOCAL.equals(prefs.mode) && !LocalModels.ready(this, prefs.localTier)) {
            toast("本地模型还没下载");
            new SheetDialog(this, prefs, this::refresh).show();
            return;
        }
        if (prefs.overlayEnabled && !Settings.canDrawOverlays(this)) {
            resumeAfterOverlay = true;
            toast("请允许「显示在其他应用上层」");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        askProjection();
    }

    private void askProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_AUDIO) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) askProjection();
            else toast("没有录音权限，无法内录");
        } else if (req == REQ_NOTIF) {
            askProjection();
        }
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        super.onActivityResult(req, code, data);
        if (req != REQ_PROJECTION) return;
        if (code != RESULT_OK || data == null) {
            notice("已取消录屏授权 / CANCELLED");
            return;
        }
        Intent svc = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra("code", code)
                .putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        notice("已开始，切到视频应用 / RUNNING");
    }

    private static String labelOfSource(String code) {
        if (code == null || code.isEmpty()) return "自动";
        switch (code) {
            case "en":
                return "英语";
            case "ja":
                return "日语";
            case "ko":
                return "韩语";
            case "zh":
                return "中文";
            case "yue":
                return "粤语";
            case "ru":
                return "俄语";
            case "fr":
                return "法语";
            case "de":
                return "德语";
            case "es":
                return "西班牙语";
            case "pt":
                return "葡萄牙语";
            case "it":
                return "意大利语";
            case "th":
                return "泰语";
            case "vi":
                return "越南语";
            case "ar":
                return "阿拉伯语";
            default:
                return code;
        }
    }

    private static String codeOfSource(String code) {
        if (code == null || code.isEmpty()) return "AUTO";
        return code.toUpperCase();
    }

    private static String codeOfTarget(String name) {
        if (name == null || name.isEmpty()) return "ZH";
        if (name.contains("中文") || name.contains("繁")) return "ZH";
        if (name.toLowerCase().contains("english")) return "EN";
        if (name.contains("日本")) return "JA";
        if (name.contains("한국")) return "KO";
        if (name.contains("Рус")) return "RU";
        if (name.contains("Fran")) return "FR";
        if (name.contains("Deutsch")) return "DE";
        if (name.contains("Espa")) return "ES";
        if (name.contains("ไทย")) return "TH";
        if (name.contains("Việt")) return "VI";
        return "—";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
