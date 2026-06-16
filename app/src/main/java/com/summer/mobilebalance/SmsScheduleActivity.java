package com.summer.mobilebalance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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

public class SmsScheduleActivity extends Activity {

    private SharedPreferences prefs;
    private EditText etPhone, etMessage, etHour, etMinute;
    private CheckBox cbRepeat, cbEnabled;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = getSharedPreferences(Config.PREF_NAME, Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            prefs = getSharedPreferences(Config.PREF_NAME, Context.MODE_PRIVATE);
        }

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.parseColor("#0f1419"));
        sv.addView(root);

        // 标题
        TextView title = new TextView(this);
        title.setText("⏰ 定时发送短信");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("每天定时发送短信到10086查询话费\n收到回复后自动识别余额并推送Webhook");
        hint.setTextColor(Color.parseColor("#aaaaaa"));
        hint.setTextSize(11);
        hint.setPadding(0, 0, 0, 15);
        root.addView(hint);

        // 开关
        cbEnabled = addCheckBox(root, "✅ 启用定时发送",
            prefs.getBoolean(Config.KEY_SCHEDULE_ENABLED, Config.DEFAULT_SCHEDULE_ENABLED));

        // 号码
        etPhone = addInput(root, "发送号码",
            prefs.getString(Config.KEY_SCHEDULE_PHONE, Config.DEFAULT_SCHEDULE_PHONE),
            InputType.TYPE_CLASS_PHONE);

        // 内容
        etMessage = addInput(root, "短信内容",
            prefs.getString(Config.KEY_SCHEDULE_MESSAGE, Config.DEFAULT_SCHEDULE_MESSAGE), 0);

        // 时间
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tLabel = new TextView(this);
        tLabel.setText("发送时间: ");
        tLabel.setTextColor(Color.parseColor("#00d4ff"));
        tLabel.setTextSize(12);
        timeRow.addView(tLabel);

        etHour = addInputInline("时", prefs.getString(Config.KEY_SCHEDULE_HOUR, Config.DEFAULT_SCHEDULE_HOUR),
            InputType.TYPE_CLASS_NUMBER);
        timeRow.addView(etHour);

        TextView colon = new TextView(this);
        colon.setText(" : ");
        colon.setTextColor(Color.WHITE);
        colon.setTextSize(16);
        timeRow.addView(colon);

        etMinute = addInputInline("分", prefs.getString(Config.KEY_SCHEDULE_MINUTE, Config.DEFAULT_SCHEDULE_MINUTE),
            InputType.TYPE_CLASS_NUMBER);
        timeRow.addView(etMinute);

        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.topMargin = 18;
        timeRow.setLayoutParams(trLp);
        root.addView(timeRow);

        // 重复
        cbRepeat = addCheckBox(root, "🔄 每天重复发送",
            prefs.getBoolean(Config.KEY_SCHEDULE_REPEAT, Config.DEFAULT_SCHEDULE_REPEAT));

        // 保存按钮
        Button save = new Button(this);
        save.setText("💾 保存并设置定时任务");
        save.setBackgroundColor(Color.parseColor("#00d4ff"));
        save.setTextColor(Color.parseColor("#0f1419"));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndSetAlarm(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 30;
        save.setLayoutParams(lp);
        root.addView(save);

        // 取消按钮
        Button cancel = new Button(this);
        cancel.setText("🗑 取消定时任务");
        cancel.setBackgroundColor(Color.parseColor("#442222"));
        cancel.setTextColor(Color.parseColor("#ff4444"));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancelAlarm(); }
        });
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpC.topMargin = 15;
        cancel.setLayoutParams(lpC);
        root.addView(cancel);

        // 立即发送按钮
        Button sendNow = new Button(this);
        sendNow.setText("📤 立即发送短信");
        sendNow.setBackgroundColor(Color.parseColor("#1a2332"));
        sendNow.setTextColor(Color.parseColor("#00d4ff"));
        sendNow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendSmsNow(); }
        });
        LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpS.topMargin = 15;
        sendNow.setLayoutParams(lpS);
        root.addView(sendNow);

        // 状态
        TextView status = new TextView(this);
        status.setText(getScheduleStatus());
        status.setTextColor(Color.parseColor("#66ff66"));
        status.setTextSize(11);
        status.setPadding(0, 20, 0, 0);
        status.setId(999);
        root.addView(status);

        setContentView(sv);
    }

    private EditText addInputInline(String hint, String value, int type) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setTextSize(14);
        et.setBackgroundColor(Color.parseColor("#1a2332"));
        et.setPadding(20, 15, 20, 15);
        et.setInputType(type);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(lp);
        et.setGravity(Gravity.CENTER);
        return et;
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
        et.setBackgroundColor(Color.parseColor("#1a2332"));
        et.setPadding(20, 20, 20, 20);
        if (type != 0) et.setInputType(type);
        parent.addView(et);
        return et;
    }

    private void saveAndSetAlarm() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(Config.KEY_SCHEDULE_ENABLED, cbEnabled.isChecked());
        ed.putString(Config.KEY_SCHEDULE_PHONE, etPhone.getText().toString().trim());
        ed.putString(Config.KEY_SCHEDULE_MESSAGE, etMessage.getText().toString().trim());
        ed.putString(Config.KEY_SCHEDULE_HOUR, etHour.getText().toString().trim());
        ed.putString(Config.KEY_SCHEDULE_MINUTE, etMinute.getText().toString().trim());
        ed.putBoolean(Config.KEY_SCHEDULE_REPEAT, cbRepeat.isChecked());
        ed.apply();

        // chmod for xposed read
        try {
            java.io.File f = new java.io.File(getApplicationInfo().dataDir + "/shared_prefs/" + Config.PREF_NAME + ".xml");
            if (f.exists()) Runtime.getRuntime().exec("chmod 664 " + f.getAbsolutePath());
        } catch (Throwable ignored) {}

        if (cbEnabled.isChecked()) {
            // Android 12+ 需要检查精确闹钟权限
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                boolean canSchedule = true;
                try {
                    // canScheduleExactAlarms() is API 31+
                    java.lang.reflect.Method m = am.getClass().getMethod("canScheduleExactAlarms");
                    canSchedule = (Boolean) m.invoke(am);
                } catch (Throwable t) {
                    // 如果反射失败，假设可以（有USE_EXACT_ALARM权限时）
                    canSchedule = true;
                }
                if (!canSchedule) {
                    // 引导用户到系统设置页开启
                    try {
                        Intent intent = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM");
                        intent.setData(android.net.Uri.parse("package:com.summer.mobilebalance"));
                        startActivity(intent);
                        Toast.makeText(this, "⚠️ 请先开启「精确闹钟」权限，然后重新保存", Toast.LENGTH_LONG).show();
                        return;
                    } catch (Throwable t) {
                        // 如果上面的intent不行，用应用详情页
                        try {
                            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                            intent.setData(android.net.Uri.parse("package:com.summer.mobilebalance"));
                            startActivity(intent);
                            Toast.makeText(this, "⚠️ 请在设置中开启「闹钟和提醒」权限", Toast.LENGTH_LONG).show();
                            return;
                        } catch (Throwable t2) {
                            Toast.makeText(this, "❌ 无法打开设置，请手动开启精确闹钟权限", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            }

            int hour, minute;
            try { hour = Integer.parseInt(etHour.getText().toString().trim()); }
            catch (Throwable t) { hour = 8; }
            try { minute = Integer.parseInt(etMinute.getText().toString().trim()); }
            catch (Throwable t) { minute = 0; }

            String phone = etPhone.getText().toString().trim();
            String msg = etMessage.getText().toString().trim();
            SmsAlarmReceiver.setAlarm(this, phone, msg, hour, minute, cbRepeat.isChecked());
            Toast.makeText(this, "✅ 定时任务已设置: " + hour + ":" + String.format("%02d", minute), Toast.LENGTH_SHORT).show();
        } else {
            SmsAlarmReceiver.cancelAlarm(this);
            Toast.makeText(this, "⏸ 定时任务已禁用", Toast.LENGTH_SHORT).show();
        }

        // 更新状态
        View v = findViewById(999);
        if (v instanceof TextView) ((TextView) v).setText(getScheduleStatus());
    }

    private void cancelAlarm() {
        SmsAlarmReceiver.cancelAlarm(this);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(Config.KEY_SCHEDULE_ENABLED, false);
        ed.apply();
        Toast.makeText(this, "🗑 定时任务已取消", Toast.LENGTH_SHORT).show();
        View v = findViewById(999);
        if (v instanceof TextView) ((TextView) v).setText(getScheduleStatus());
    }

    private void sendSmsNow() {
        String phone = etPhone.getText().toString().trim();
        String msg = etMessage.getText().toString().trim();
        try {
            android.telephony.SmsManager sm = android.telephony.SmsManager.getDefault();
            sm.sendTextMessage(phone, null, msg, null, null);
            LogStore.append("SMS_OUT", "→ 手动发送短信到 " + phone + " 内容: " + msg);
            Toast.makeText(this, "✅ 短信已发送到 " + phone, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            LogStore.append("SMS_OUT", "× 手动发送失败: " + t.getMessage());
            Toast.makeText(this, "❌ 发送失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getScheduleStatus() {
        boolean schedEnabled = prefs.getBoolean(Config.KEY_SCHEDULE_ENABLED, false);
        String schedTime = prefs.getString(Config.KEY_SCHEDULE_HOUR, "08") + ":" + prefs.getString(Config.KEY_SCHEDULE_MINUTE, "00");
        String schedPhone = prefs.getString(Config.KEY_SCHEDULE_PHONE, "10086");
        String schedMsg = prefs.getString(Config.KEY_SCHEDULE_MESSAGE, "查余额");
        boolean schedRepeat = prefs.getBoolean(Config.KEY_SCHEDULE_REPEAT, true);
        if (schedEnabled) {
            return "\n📋 当前状态: 已启用\n⏰ 发送时间: 每天 " + schedTime + "\n📱 发送到: " + schedPhone + "\n💬 内容: " + schedMsg + "\n🔄 重复: " + (schedRepeat ? "每天" : "仅一次");
        } else {
            return "\n📋 当前状态: 未启用";
        }
    }
}
