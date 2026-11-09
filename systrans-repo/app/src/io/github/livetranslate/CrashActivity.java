package io.github.livetranslate;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 展示上一次的崩溃堆栈。 */
public final class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String text = getIntent().getStringExtra("text");
        if (text == null) text = "没有记录";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2EDE3);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("上次运行崩了");
        title.setTextColor(0xFFC0392B);
        title.setTextSize(18);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("下面是错误信息，点一下可以复制，发给我就能定位。");
        tip.setTextColor(0x8C12100D);
        tip.setTextSize(12);
        tip.setPadding(0, pad / 2, 0, pad);
        root.addView(tip);

        final ScrollView sv = new ScrollView(this);
        final TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(0xFFD8DCE3);
        body.setTextSize(11);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView copy = new TextView(this);
        copy.setText("复制错误信息");
        copy.setTextColor(Color.WHITE);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(0, pad, 0, pad);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", body.getText()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        root.addView(copy);

        setContentView(root);
    }
}
