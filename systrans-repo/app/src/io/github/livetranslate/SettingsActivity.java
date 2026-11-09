package io.github.livetranslate;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** 设置：字幕外观、识别灵敏度、语言。 */
public final class SettingsActivity extends Activity {

    private Prefs prefs;
    private float d;
    private TextView previewSrc, previewDst;
    private View previewBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.load(this);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2EDE3);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        col.setPadding(pad, dp(20), pad, dp(30));
        sv.addView(col);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xFF12100D);
        back.setPadding(0, 0, dp(12), dp(4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView t = new TextView(this);
        t.setText("设置");
        t.setTextSize(18);
        t.setTypeface(Typeface.create("sans-serif-medium", 0));
        t.setTextColor(0xFF12100D);
        bar.addView(t);
        col.addView(bar);

        col.addView(section("桌面歌词预览"));
        LinearLayout previewCard = card();
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(12), dp(16), dp(14));

        previewSrc = new TextView(this);
        previewSrc.setText("This is what the subtitle looks like.");
        previewSrc.setTextColor(0x8C12100D);
        previewDst = new TextView(this);
        previewDst.setText("字幕大概就是这个样子。");
        previewDst.setTextColor(Color.WHITE);
        previewDst.setTypeface(Typeface.create("sans-serif-medium", 0));
        preview.addView(previewSrc);
        preview.addView(previewDst);
        previewCard.addView(preview);
        previewBox = preview;
        col.addView(previewCard);

        col.addView(section("字体颜色"));
        LinearLayout cColor = card();
        cColor.addView(colorRow(new int[]{0xFFFFFFFF, 0xFF111111, 0xFFFFE066,
                0xFF7BE0C8, 0xFFFF9EC4, 0xFF9CC3FF}, prefs.overlayTextColor, v -> {
            prefs.overlayTextColor = v;
            prefs.save(this);
            recreate();
        }));
        col.addView(cColor);

        col.addView(section("底框颜色"));
        LinearLayout cHl = card();
        cHl.addView(colorRow(new int[]{0xFF101318, 0xFF2B3038, 0xFF000000, 0xFFFFE066,
                0xFF0A84FF, 0xFFFF9EC4}, prefs.overlayHighlight, v -> {
            prefs.overlayHighlight = v;
            prefs.save(this);
            recreate();
        }));
        col.addView(cHl);

        col.addView(section("底框浓度"));
        LinearLayout c1 = card();
        c1.addView(slider(0, 100, prefs.overlayBgAlpha, v -> {
            prefs.overlayBgAlpha = v;
            prefs.save(this);
            applyPreview();
        }));
        col.addView(c1);

        col.addView(section("字幕字号"));
        LinearLayout c2 = card();
        c2.addView(slider(12, 28, prefs.overlayTextSize, v -> {
            prefs.overlayTextSize = v;
            prefs.save(this);
            applyPreview();
        }));
        col.addView(c2);

        col.addView(section("字幕停留"));
        LinearLayout cHold = card();
        cHold.addView(slider(3, 30, prefs.overlayHold, v -> {
            prefs.overlayHold = v;
            prefs.save(this);
        }));
        col.addView(cHold);

        col.addView(section("识别灵敏度"));
        LinearLayout c3 = card();
        c3.addView(slider(0, 100, prefs.sensitivity, v -> {
            prefs.sensitivity = v;
            prefs.save(this);
        }));
        TextView note = new TextView(this);
        note.setText("越高越容易把小声判成说话；环境嘈杂时调低一些。");
        note.setTextColor(0xFF6B6459);
        note.setTextSize(11);
        note.setPadding(dp(14), 0, dp(14), dp(12));
        c3.addView(note);
        col.addView(c3);

        col.addView(section("语言"));
        LinearLayout c4 = card();
        c4.addView(langRow("源语言", labelOfSource(prefs.srcLang), v -> {
            prefs.srcLang = v;
            prefs.save(this);
            recreate();
        }));
        c4.addView(divider());
        c4.addView(langRow("目标语言", prefs.tgtLang, v -> {
            prefs.tgtLang = v;
            prefs.save(this);
            recreate();
        }));
        col.addView(c4);

        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applyPreview();
    }

    private void applyPreview() {
        previewDst.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.overlayTextSize);
        previewSrc.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, prefs.overlayTextSize - 5));
        previewDst.setShadowLayer(dp(12), 0, 2, 0xE6000000);
        previewSrc.setShadowLayer(dp(6), 0, 1, 0xCC000000);
        if (previewBox != null) {
            if (prefs.overlayBgAlpha <= 0) {
                previewBox.setBackground(null);
            } else {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setColor(android.graphics.Color.argb(
                        Math.min(235, (int) (prefs.overlayBgAlpha * 2.35f)), 16, 19, 24));
                g.setCornerRadius(dp(12));
                previewBox.setBackground(g);
            }
        }
    }

    private View section(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(0xFF6B6459);
        v.setTextSize(11);
        v.setLetterSpacing(0.08f);
        v.setPadding(dp(4), dp(22), 0, dp(8));
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        c.setLayoutParams(lp);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0x2612100D);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.leftMargin = dp(14);
        lp.rightMargin = dp(14);
        v.setLayoutParams(lp);
        return v;
    }

    private View colorRow(int[] colors, int current, final IntSink sink) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        for (int c : colors) {
            View v = new View(this);
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(dp(2), c == current ? 0xFF2B31E8 : 0x3312100D);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(14);
            row.addView(v, lp);
            v.setOnClickListener(x -> {
                sink.onValue(c);
            });
        }
        return row;
    }

    private View slider(int min, int max, int value, final IntSink sink) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(10), dp(12), dp(6));

        TextView val = new TextView(this);
        val.setText(String.valueOf(value));
        val.setTextColor(0xFF12100D);
        val.setTextSize(12);
        val.setGravity(Gravity.END);
        wrap.addView(val);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF2B31E8));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF2B31E8));
        }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                val.setText(String.valueOf(v));
                if (fromUser) sink.onValue(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        wrap.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View langRow(String label, String value, final LangPicker.Pick pick) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(15), dp(16), dp(15));
        r.setClickable(true);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFF12100D);
        l.setTextSize(14);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value + "  ›");
        v.setTextColor(0xFF6B6459);
        v.setTextSize(13);
        r.addView(v);

        r.setOnClickListener(x -> LangPicker.show(this, r, "源语言".equals(label), value, pick));
        return r;
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

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    interface IntSink {
        void onValue(int v);
    }
}
