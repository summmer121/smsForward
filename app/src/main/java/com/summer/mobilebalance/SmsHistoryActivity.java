package com.summer.mobilebalance;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

/**
 * 历史短信查看页 - 主人summer要求.
 * 显示最近监听到的所有短信(JSON存储,最多100条),包含:
 *   - 时间
 *   - 发件人
 *   - 内容
 *   - 是否识别为话费短信
 *   - 识别到的余额(若有)
 */
public class SmsHistoryActivity extends Activity {

    private LinearLayout listContainer;
    private TextView tvEmpty;
    private Handler handler = new Handler();
    private boolean autoRefresh = true;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!autoRefresh) return;
            loadHistory();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SmsHistoryStore.initDirs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0f1419"));

        // 标题栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(20, 20, 20, 20);
        bar.setBackgroundColor(Color.parseColor("#1a2332"));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("📨 历史监听短信");
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setTextSize(16);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(title);

        Button btnRefresh = makeBtn("🔄 刷新");
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadHistory(); }
        });
        bar.addView(btnRefresh);

        Button btnClear = makeBtn("🗑 清空");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (SmsHistoryStore.clear()) {
                    Toast.makeText(SmsHistoryActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                    loadHistory();
                } else {
                    Toast.makeText(SmsHistoryActivity.this, "清空失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        bar.addView(btnClear);

        root.addView(bar);

        // 提示
        TextView hint = new TextView(this);
        hint.setText("最近监听到的短信 (最多保留100条, 每3秒自动刷新)");
        hint.setTextColor(Color.parseColor("#666666"));
        hint.setTextSize(10);
        hint.setPadding(20, 10, 20, 10);
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(20, 10, 20, 30);
        sv.addView(listContainer);

        tvEmpty = new TextView(this);
        tvEmpty.setText("\n（暂无历史记录）\n\n监听到短信后会自动出现在这里。\n如果一直没有记录，请检查:\n• LSPosed作用域是否勾选了系统短信App / com.android.phone\n• 模块是否启用\n• 重启系统短信进程或手机");
        tvEmpty.setTextColor(Color.parseColor("#888888"));
        tvEmpty.setTextSize(12);
        tvEmpty.setPadding(20, 30, 20, 30);
        listContainer.addView(tvEmpty);

        root.addView(sv);

        setContentView(root);
        loadHistory();
    }

    private Button makeBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(Color.parseColor("#0f1419"));
        b.setBackgroundColor(Color.parseColor("#00d4ff"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = 10;
        b.setLayoutParams(lp);
        b.setPadding(20, 10, 20, 10);
        return b;
    }

    private void loadHistory() {
        listContainer.removeAllViews();
        List<JSONObject> records = SmsHistoryStore.readAll();
        if (records.isEmpty()) {
            listContainer.addView(tvEmpty);
            return;
        }

        TextView count = new TextView(this);
        count.setText("📊 共 " + records.size() + " 条记录 (最新的在最上面)");
        count.setTextColor(Color.parseColor("#00d4ff"));
        count.setTextSize(11);
        count.setPadding(0, 0, 0, 15);
        listContainer.addView(count);

        for (JSONObject o : records) {
            listContainer.addView(makeRecordCard(o));
        }
    }

    private View makeRecordCard(JSONObject o) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1a2332"));
        card.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 12;
        card.setLayoutParams(lp);

        boolean matched = o.optBoolean("matched", false);
        String balance = o.optString("balance", "");
        String time = o.optString("time", "");
        String sender = o.optString("sender", "");
        String body = o.optString("body", "");

        // 顶部状态行：识别成功/未识别 + 时间
        TextView top = new TextView(this);
        if (matched) {
            top.setText("✅ 已识别  💰 " + balance + " 元    " + time);
            top.setTextColor(Color.parseColor("#00ff9f"));
        } else {
            top.setText("⚠️  未识别余额    " + time);
            top.setTextColor(Color.parseColor("#ff9966"));
        }
        top.setTextSize(12);
        top.setTypeface(null, Typeface.BOLD);
        card.addView(top);

        // 发件人
        TextView tvSender = new TextView(this);
        tvSender.setText("📤 发件人: " + sender);
        tvSender.setTextColor(Color.parseColor("#00d4ff"));
        tvSender.setTextSize(11);
        tvSender.setPadding(0, 6, 0, 4);
        card.addView(tvSender);

        // 内容
        TextView tvBody = new TextView(this);
        tvBody.setText(body);
        tvBody.setTextColor(Color.parseColor("#a0e0ff"));
        tvBody.setTextSize(12);
        tvBody.setTypeface(Typeface.MONOSPACE);
        tvBody.setTextIsSelectable(true);
        tvBody.setPadding(8, 6, 8, 6);
        tvBody.setBackgroundColor(Color.parseColor("#0f1419"));
        card.addView(tvBody);

        return card;
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoRefresh = true;
        loadHistory();
        handler.postDelayed(refreshTask, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefresh = false;
        handler.removeCallbacks(refreshTask);
    }
}
