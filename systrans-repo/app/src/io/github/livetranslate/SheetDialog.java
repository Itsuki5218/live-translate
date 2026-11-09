package io.github.livetranslate;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 从底部弹出的引擎选择面板。 */
final class SheetDialog {

    private final Activity a;
    private final Prefs prefs;
    private final Runnable onChange;
    private final Dialog dialog;
    private final FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float d;

    private int page = 0;
    private volatile boolean downloading;
    private volatile boolean canceled;
    private int progress;
    private String error;

    SheetDialog(Activity a, Prefs prefs, Runnable onChange) {
        this.a = a;
        this.prefs = prefs;
        this.onChange = onChange;
        this.d = a.getResources().getDisplayMetrics().density;

        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new FrameLayout(a);
        dialog.setContentView(root);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
            w.setWindowAnimations(R.style.SheetAnim);
        }
        dialog.setOnDismissListener(dd -> canceled = true);
    }

    void show() {
        page = 0;
        render();
        dialog.show();
    }

    private void dismiss() {
        dialog.dismiss();
    }

    private void render() {
        root.removeAllViews();
        LinearLayout panel = page == 0 ? mainPage() : page == 1 ? localPage() : cloudPage();
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void go(int target) {
        page = target;
        render();
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundResource(R.drawable.bg_sheet);
        p.setPadding(dp(18), dp(8), dp(18), dp(26));
        return p;
    }

    private View handle() {
        View v = new View(a);
        v.setBackgroundColor(0x3312100D);
        FrameLayout f = new FrameLayout(a);
        f.addView(v, new FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
        f.setLayoutParams(lp);
        return f;
    }

    private TextView title(String t) {
        TextView v = new TextView(a);
        v.setText(t);
        v.setTextColor(0xFF12100D);
        v.setTextSize(17);
        v.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        v.setPadding(0, dp(2), 0, dp(10));
        return v;
    }

    private TextView hint(String t) {
        TextView v = new TextView(a);
        v.setText(t);
        v.setTextColor(0xFF6B6459);
        v.setTextSize(12);
        v.setLineSpacing(dp(2), 1f);
        v.setPadding(dp(2), dp(6), dp(2), dp(2));
        return v;
    }

    private LinearLayout mainPage() {
        LinearLayout p = panel();
        p.addView(handle());
        p.addView(title("选择引擎"));
        p.addView(row("本地模型", LocalModels.ready(a, prefs.localTier)
                        ? "已就绪 · " + LocalModels.nameOf(prefs.localTier)
                        : "未下载 · " + LocalModels.nameOf(prefs.localTier),
                Prefs.MODE_LOCAL.equals(prefs.mode), v -> go(1)));
        p.addView(row("云端 API", "当前 · " + prefs.preset().name,
                Prefs.MODE_CLOUD.equals(prefs.mode), v -> go(2)));
        p.addView(hint("本地模式不联网、不花钱，首次需下载约 545 MB 模型；云端模式填一个 Key 即可。"));
        return p;
    }

    private View row(String titleText, String subText, boolean selected, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(13), dp(16), dp(13));
        r.setBackgroundResource(R.drawable.bg_card);
        r.setOnClickListener(click);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(a);
        t.setText(titleText);
        t.setTextColor(0xFF12100D);
        t.setTextSize(15);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView mark = new TextView(a);
        mark.setText(selected ? "使用中" : "›");
        mark.setTextColor(selected ? 0xFF2B31E8 : 0x5912100D);
        mark.setTextSize(12);
        head.addView(mark);

        TextView s = new TextView(a);
        s.setText(subText);
        s.setTextColor(0xFF6B6459);
        s.setTextSize(12);
        s.setPadding(0, dp(4), 0, 0);

        r.addView(head);
        r.addView(s);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private LinearLayout localPage() {
        LinearLayout p = panel();
        p.addView(handle());
        p.addView(backBar("本地模型"));
        p.addView(hint("全部在手机上离线运行，不联网。档位越高越准，也越慢越耗电。"));

        for (int i = 0; i < LocalModels.TIERS.length; i++) {
            final int tier = i;
            LocalModels.Tier t = LocalModels.TIERS[i];
            boolean ready = LocalModels.ready(a, tier);
            String sub = t.sizeText() + (ready ? " · 已下载" : " · 未下载");
            final String title = t.name + " · " + t.sub;
            p.addView(row(title, sub, prefs.localTier == tier, v -> {
                prefs.localTier = tier;
                prefs.save(a);
                error = null;
                render();
            }));
        }

        final int tier = LocalModels.clamp(prefs.localTier);
        LocalModels.Tier t = LocalModels.TIERS[tier];

        if (LocalModels.ready(a, tier)) {
            boolean active = Prefs.MODE_LOCAL.equals(prefs.mode);
            p.addView(primary(active ? "正在使用这一档" : "启用本地模式", v -> {
                prefs.mode = Prefs.MODE_LOCAL;
                prefs.save(a);
                onChange.run();
                dismiss();
            }));
            // 文件可能不完整（表现为一启用就失败），给一个直接重下的入口
            p.addView(primary("重新下载这一档", v -> {
                LocalModels.asr(a, tier).delete();
                LocalModels.mt(a, tier).delete();
                error = null;
                startDownload(tier);
            }));
            p.addView(primary("自检这一档（加载模型）", v -> selfTest(tier)));
        } else if (downloading) {
            ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(progress);
            bar.setId(4242);
            p.addView(bar);
            TextView pct = new TextView(a);
            pct.setId(4243);
            pct.setText(progress + "%");
            pct.setTextColor(0xFF12100D);
            pct.setTextSize(12);
            pct.setPadding(0, 0, 0, dp(10));
            p.addView(pct);
            p.addView(primary("取消下载", v -> {
                canceled = true;
                downloading = false;
                render();
            }));
            startProgressTicker(bar, pct);
        } else {
            if (error != null) p.addView(hint("下载失败：" + error));
            p.addView(primary("下载「" + t.name + "」 " + t.sizeText(), v -> startDownload(tier)));
            p.addView(hint("建议连 Wi-Fi。下载完就不再需要网络，声音也不会发到任何服务器。"));
        }
        return p;
    }

    /** 真去加载一次模型，把结果打出来，免得只能靠猜。 */
    private void selfTest(final int tier) {
        final long asrLen = LocalModels.asr(a, tier).length();
        final long mtLen = LocalModels.mt(a, tier).length();
        final android.widget.TextView tv = new android.widget.TextView(a);
        tv.setTextSize(12);
        tv.setTextColor(0xFFB8BFC9);
        tv.setPadding(dp(4), dp(8), dp(4), dp(4));
        tv.setText("正在加载…（识别 " + (asrLen / 1024 / 1024) + " MB / 翻译 "
                + (mtLen / 1024 / 1024) + " MB）");
        root.removeAllViews();
        root.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("识别文件 ").append(asrLen / 1024 / 1024).append(" MB\n");
            sb.append("翻译文件 ").append(mtLen / 1024 / 1024).append(" MB\n\n");
            try {
                long t0 = System.currentTimeMillis();
                Whisper w = new Whisper();
                boolean ok = w.load(LocalModels.asr(a, tier).getAbsolutePath());
                long cost = System.currentTimeMillis() - t0;
                sb.append("识别模型：").append(ok ? "加载成功" : "加载失败")
                        .append("，").append(cost).append(" ms\n");
                w.close();
            } catch (Throwable t) {
                sb.append("识别模型异常：").append(t.getClass().getSimpleName())
                        .append(" ").append(String.valueOf(t.getMessage())).append("\n");
            }
            try {
                long t0 = System.currentTimeMillis();
                Llama l = new Llama();
                boolean ok = l.load(LocalModels.mt(a, tier).getAbsolutePath());
                long cost = System.currentTimeMillis() - t0;
                sb.append("翻译模型：").append(ok ? "加载成功" : "加载失败")
                        .append("，").append(cost).append(" ms\n");
                l.close();
            } catch (Throwable t) {
                sb.append("翻译模型异常：").append(t.getClass().getSimpleName())
                        .append(" ").append(String.valueOf(t.getMessage())).append("\n");
            }
            final String text = sb.toString();
            ui.post(() -> {
                tv.setText(text);
                tv.setTextIsSelectable(true);
            });
        }, "selftest").start();
    }

    private void startProgressTicker(final ProgressBar bar, final TextView pct) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!downloading || bar == null) return;
                bar.setProgress(progress);
                pct.setText(progress + "%");
                ui.postDelayed(this, 400);
            }
        };
        ui.postDelayed(r, 400);
    }

    private void startDownload(final int tier) {
        downloading = true;
        canceled = false;
        error = null;
        progress = 0;
        render();
        new Thread(() -> {
            try {
                LocalModels.Tier t = LocalModels.TIERS[tier];
                int wAsr = (int) (t.asrSize * 100 / t.total());
                fetch(t.asrUrl, LocalModels.asr(a, tier), t.asrSize, 0, wAsr);
                fetch(t.mtUrl, LocalModels.mt(a, tier), t.mtSize, wAsr, 100);
                downloading = false;
                ui.post(() -> {
                    if (!canceled) render();
                });
            } catch (Exception e) {
                downloading = false;
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                ui.post(() -> {
                    if (!canceled) render();
                });
            }
        }, "model-download").start();
    }

    private void fetch(String url, File dst, long expect, int from, int to) throws Exception {
        File part = new File(dst.getAbsolutePath() + ".part");
        if (part.exists()) part.delete();

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "LiveTranslate/1.0");
        c.connect();
        long total = c.getContentLengthLong();
        InputStream in = c.getInputStream();
        FileOutputStream out = new FileOutputStream(part);
        byte[] buf = new byte[64 * 1024];
        long got = 0;
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                if (canceled) throw new Exception("已取消");
                out.write(buf, 0, n);
                got += n;
                if (total > 0) progress = from + (int) ((to - from) * got / total);
            }
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }

        if (expect > 0 && got < (long) (expect * 0.95)) {
            part.delete();
            throw new Exception("下载不完整（" + (got / 1024 / 1024) + " MB / 约 "
                    + (expect / 1024 / 1024) + " MB），请重试");
        }

        // 目标已存在时 renameTo 会失败，必须先删
        if (dst.exists() && !dst.delete()) throw new Exception("无法覆盖旧文件");
        if (!part.renameTo(dst)) throw new Exception("写入失败");
    }

    private LinearLayout cloudPage() {
        LinearLayout p = panel();
        p.addView(handle());
        p.addView(backBar("云端 API"));

        for (int i = 0; i < prefs.presets.size(); i++) {
            final int index = i;
            Prefs.ApiPreset pr = prefs.presets.get(i);
            String sub = pr.base + (pr.key.isEmpty() ? " · 未填 Key" : " · 已配置");
            p.addView(row(pr.name, sub,
                    Prefs.MODE_CLOUD.equals(prefs.mode) && prefs.current == i, v -> {
                        prefs.current = index;
                        prefs.mode = Prefs.MODE_CLOUD;
                        if (pr.key.isEmpty()) {
                            edit(pr, index);
                        } else {
                            prefs.save(a);
                            onChange.run();
                            dismiss();
                        }
                    }));
        }

        p.addView(primary("＋ 添加自定义接口", v -> {
            Prefs.ApiPreset np = new Prefs.ApiPreset("自定义", "https://", "", "", "gpt-4o-mini");
            prefs.presets.add(np);
            edit(np, prefs.presets.size() - 1);
        }));
        p.addView(hint("接口需兼容 OpenAI：/audio/transcriptions 与 /chat/completions。"));
        return p;
    }

    private View backBar(String text) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(a);
        back.setText("‹");
        back.setTextSize(22);
        back.setTextColor(0xFF12100D);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(v -> go(0));
        bar.addView(back);
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(17);
        t.setTextColor(0xFF12100D);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        bar.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        bar.setLayoutParams(lp);
        return bar;
    }

    private View primary(String text, View.OnClickListener click) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(14), 0, dp(14));
        b.setBackgroundResource(R.drawable.bg_card);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void edit(Prefs.ApiPreset pr, int index) {
        Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout p = panel();
        p.addView(handle());
        p.addView(title("接口配置"));

        EditText etName = field(p, "名称", pr.name, InputType.TYPE_CLASS_TEXT);
        EditText etBase = field(p, "接口地址", pr.base, InputType.TYPE_TEXT_VARIATION_URI);
        EditText etKey = field(p, "API Key", pr.key, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText etAsr = field(p, "识别模型", pr.asr, InputType.TYPE_CLASS_TEXT);
        EditText etChat = field(p, "翻译模型", pr.chat, InputType.TYPE_CLASS_TEXT);

        p.addView(primary("保存并使用", v -> {
            Prefs.ApiPreset edited = new Prefs.ApiPreset(
                    text(etName), text(etBase), text(etKey), text(etAsr), text(etChat));
            if (edited.name.isEmpty()) edited.name = "自定义";
            prefs.presets.set(index, edited);
            prefs.current = index;
            prefs.mode = Prefs.MODE_CLOUD;
            prefs.save(a);
            onChange.run();
            dlg.dismiss();
            dismiss();
        }));

        dlg.setContentView(p);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(R.style.SheetAnim);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
        }
        dlg.show();
    }

    private EditText field(LinearLayout parent, String hint, String value, int inputType) {
        TextView label = new TextView(a);
        label.setText(hint);
        label.setTextColor(0xFF6B6459);
        label.setTextSize(11);
        label.setPadding(dp(4), dp(9), 0, dp(4));
        parent.addView(label);

        EditText e = new EditText(a);
        e.setText(value);
        e.setTextSize(14);
        e.setTextColor(0xFF12100D);
        e.setHintTextColor(0x5912100D);
        e.setInputType(inputType);
        e.setBackgroundResource(R.drawable.bg_card);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private static String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}
