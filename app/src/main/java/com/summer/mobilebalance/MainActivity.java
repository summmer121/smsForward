package com.summer.mobilebalance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private EditText etWebhook, etMethod, etBody, etHeaders, etSenderRe, etBalanceRe;
    private CheckBox cbEnabled, cbLog;

    private static final int REQ_PERMISSIONS = 1001;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences(Config.PREF_NAME, Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            prefs = getSharedPreferences(Config.PREF_NAME, Context.MODE_PRIVATE);
        }

        // 请求所有运行时权限
        requestAllPermissions();

        // 初始化日志目录
        LogStore.initLogDirs(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.parseColor("#0f1419"));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("📱 移动话费查询 v1.4");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, 15);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        Button btnLog = makeBtn("📋 日志", "#1a2332", "#00d4ff");
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });
        btnRow.addView(btnLog);

        Button btnSchedule = makeBtn("⏰ 定时发送", "#1a2332", "#00d4ff");
        btnSchedule.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SmsScheduleActivity.class));
            }
        });
        addMarginLeft(btnSchedule, 15);
        btnRow.addView(btnSchedule);

        Button btnSendNow = makeBtn("📤 立即发送", "#1a2332", "#00d4ff");
        btnSendNow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendSmsNow(); }
        });
        addMarginLeft(btnSendNow, 15);
        btnRow.addView(btnSendNow);

        Button btnPerm = makeBtn("🔑 权限", "#1a2332", "#ff9900");
        btnPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestAllPermissions(); }
        });
        addMarginLeft(btnPerm, 15);
        btnRow.addView(btnPerm);

        root.addView(btnRow);

        // 权限状态提示
        TextView permStatus = new TextView(this);
        permStatus.setText(getPermStatusText());
        permStatus.setTextColor(Color.parseColor("#ff9900"));
        permStatus.setTextSize(10);
        permStatus.setPadding(0, 0, 0, 10);
        permStatus.setId(8888);
        root.addView(permStatus);

        cbEnabled = addCheckBox(root, "✅ 启用模块",
            prefs.getBoolean(Config.KEY_ENABLED, Config.DEFAULT_ENABLED));
        cbLog = addCheckBox(root, "📝 输出 Xposed 系统日志",
            prefs.getBoolean(Config.KEY_LOG_ENABLED, Config.DEFAULT_LOG_ENABLED));

        etWebhook = addInput(root, "Webhook URL",
            prefs.getString(Config.KEY_WEBHOOK_URL, Config.DEFAULT_WEBHOOK_URL),
            InputType.TYPE_TEXT_VARIATION_URI);
        etMethod = addInput(root, "HTTP 方法 (GET/POST/PUT)",
            prefs.getString(Config.KEY_WEBHOOK_METHOD, Config.DEFAULT_WEBHOOK_METHOD), 0);
        etHeaders = addInput(root, "自定义 Headers (一行一个 key:value)",
            prefs.getString(Config.KEY_WEBHOOK_HEADERS, Config.DEFAULT_WEBHOOK_HEADERS),
            InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etBody = addInput(root, "Body 模板 (支持 {balance}{sender}{content}{timestamp})",
            prefs.getString(Config.KEY_WEBHOOK_BODY, Config.DEFAULT_WEBHOOK_BODY),
            InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etSenderRe = addInput(root, "发件人正则 (默认匹配10086)",
            prefs.getString(Config.KEY_SENDER_REGEX, Config.DEFAULT_SENDER_REGEX), 0);
        etBalanceRe = addInput(root, "余额正则 (group(1)=余额数字)",
            prefs.getString(Config.KEY_BALANCE_REGEX, Config.DEFAULT_BALANCE_REGEX),
            InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        Button save = new Button(this);
        save.setText("💾 保存配置");
        save.setBackgroundColor(Color.parseColor("#00d4ff"));
        save.setTextColor(Color.parseColor("#0f1419"));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAll(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 30;
        save.setLayoutParams(lp);
        root.addView(save);

        Button btnTest = new Button(this);
        btnTest.setText("🚀 测试发送 Webhook");
        btnTest.setBackgroundColor(Color.parseColor("#1a2332"));
        btnTest.setTextColor(Color.parseColor("#00d4ff"));
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAll(); testWebhook(); }
        });
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpT.topMargin = 15;
        btnTest.setLayoutParams(lpT);
        root.addView(btnTest);

        TextView ver = new TextView(this);
        ver.setText("\nv1.4  |  by 小小飞 ✈️ for 主人summer");
        ver.setTextColor(Color.parseColor("#666666"));
        ver.setGravity(Gravity.CENTER);
        ver.setTextSize(10);
        root.addView(ver);

        setContentView(sv);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 更新权限状态
        View v = findViewById(8888);
        if (v instanceof TextView) ((TextView) v).setText(getPermStatusText());
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= 33) {
                perms = new String[]{
                    "android.permission.SEND_SMS",
                    "android.permission.RECEIVE_SMS",
                    "android.permission.READ_SMS",
                    "android.permission.POST_NOTIFICATIONS",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                };
            } else {
                perms = new String[]{
                    "android.permission.SEND_SMS",
                    "android.permission.RECEIVE_SMS",
                    "android.permission.READ_SMS",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                };
            }
            // 检查哪些还没授权
            boolean needRequest = false;
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                requestPermissions(perms, REQ_PERMISSIONS);
            }
        }
    }

    private String getPermStatusText() {
        StringBuilder sb = new StringBuilder("权限: ");
        boolean allOk = true;
        String[] check = {"SEND_SMS", "RECEIVE_SMS", "READ_SMS", "WRITE_EXTERNAL_STORAGE"};
        for (String p : check) {
            String fullP = "android.permission." + p;
            boolean ok;
            try { ok = checkSelfPermission(fullP) == PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { ok = false; }
            if (!ok) allOk = false;
            sb.append(p.replace("android.permission.", "")).append(ok ? "✅" : "❌").append(" ");
        }
        if (allOk) sb.append(" 全部OK");
        return sb.toString();
    }

    private Button makeBtn(String text, String bgColor, String textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.parseColor(textColor));
        b.setBackgroundColor(Color.parseColor(bgColor));
        b.setPadding(20, 10, 20, 10);
        return b;
    }

    private void addMarginLeft(View v, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = margin;
        v.setLayoutParams(lp);
    }

    private void sendSmsNow() {
        String number = prefs.getString(Config.KEY_SMS_NUMBER, Config.DEFAULT_SMS_NUMBER);
        String content = prefs.getString(Config.KEY_SMS_CONTENT, Config.DEFAULT_SMS_CONTENT);
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(number, null, content, null, null);
            LogStore.append("SMS_OUT", "→ 已发送短信到 " + number + " 内容: " + content);
            Toast.makeText(this, "✅ 短信已发送到 " + number, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            LogStore.append("SMS_OUT", "× 发送失败: " + t.getMessage());
            Toast.makeText(this, "❌ 发送失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private CheckBox addCheckBox(LinearLayout parent, String label, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Color.parseColor("#ffffff"));
        cb.setChecked(checked);
        parent.addView(cb);
        return cb;
    }

    private EditText addInput(LinearLayout parent, String label, String value, int type) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#00d4ff"));
        tv.setTextSize(12);
        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lt.topMargin = 18;
        tv.setLayoutParams(lt);
        parent.addView(tv);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Color.WHITE);
        et.setTextSize(12);
        et.setHintTextColor(Color.parseColor("#666666"));
        et.setBackgroundColor(Color.parseColor("#1a2332"));
        et.setPadding(20, 20, 20, 20);
        if (type != 0) et.setInputType(InputType.TYPE_CLASS_TEXT | type);
        parent.addView(et);
        return et;
    }

    private void saveAll() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(Config.KEY_ENABLED, cbEnabled.isChecked());
        ed.putBoolean(Config.KEY_LOG_ENABLED, cbLog.isChecked());
        ed.putString(Config.KEY_WEBHOOK_URL, etWebhook.getText().toString().trim());
        ed.putString(Config.KEY_WEBHOOK_METHOD, etMethod.getText().toString().trim().toUpperCase());
        ed.putString(Config.KEY_WEBHOOK_HEADERS, etHeaders.getText().toString());
        ed.putString(Config.KEY_WEBHOOK_BODY, etBody.getText().toString());
        ed.putString(Config.KEY_SENDER_REGEX, etSenderRe.getText().toString().trim());
        ed.putString(Config.KEY_BALANCE_REGEX, etBalanceRe.getText().toString().trim());
        ed.apply();

        try {
            java.io.File f = new java.io.File(getApplicationInfo().dataDir + "/shared_prefs/" + Config.PREF_NAME + ".xml");
            if (f.exists()) {
                Runtime.getRuntime().exec("chmod 664 " + f.getAbsolutePath());
                Runtime.getRuntime().exec("chmod 711 " + f.getParentFile().getAbsolutePath());
            }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "✅ 配置已保存", Toast.LENGTH_SHORT).show();
    }

    private void testWebhook() {
        final String url = etWebhook.getText().toString().trim();
        final String method = etMethod.getText().toString().trim().toUpperCase();
        final String tpl = etBody.getText().toString();
        final String headers = etHeaders.getText().toString();

        LogStore.append("TEST", "用户发起测试 webhook");
        Toast.makeText(this, "🚀 测试请求已发出, 查看日志", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override public void run() {
                java.net.HttpURLConnection conn = null;
                try {
                    String ts = String.valueOf(System.currentTimeMillis());
                    String balance = "99.88";
                    String sender = "10086";
                    String body = "尊敬的客户:话费余额为99.88元";
                    String payload = tpl
                        .replace("{balance}", balance)
                        .replace("{sender}", sender)
                        .replace("{content}", body)
                        .replace("{timestamp}", ts);

                    String finalUrl = url;
                    if ("GET".equals(method)) {
                        String sep = url.contains("?") ? "&" : "?";
                        finalUrl = url + sep + "balance=" + balance + "&sender=" + sender + "&ts=" + ts;
                    }

                    LogStore.append("TEST", "→ " + method + " " + finalUrl);
                    if (!"GET".equals(method)) LogStore.append("TEST", "  body=" + payload);

                    java.net.URL u = new java.net.URL(finalUrl);
                    conn = (java.net.HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod(method);
                    if (headers != null && !headers.isEmpty()) {
                        for (String line : headers.split("\\r?\\n")) {
                            int idx = line.indexOf(':');
                            if (idx > 0) conn.setRequestProperty(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                        }
                    }
                    if ("POST".equals(method) || "PUT".equals(method)) {
                        conn.setDoOutput(true);
                        java.io.DataOutputStream dos = new java.io.DataOutputStream(conn.getOutputStream());
                        dos.write(payload.getBytes("UTF-8"));
                        dos.flush(); dos.close();
                    }
                    int code = conn.getResponseCode();
                    LogStore.append("TEST", "← HTTP " + code);
                } catch (Throwable t) {
                    LogStore.append("TEST", "× 异常: " + t.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
