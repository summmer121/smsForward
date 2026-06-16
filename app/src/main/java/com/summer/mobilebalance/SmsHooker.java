package com.summer.mobilebalance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SmsHooker implements IXposedHookLoadPackage {

    private static final String TAG = "MobileBalance";
    private static final String CHANNEL_ID = "mobile_balance_alert";

    private XSharedPreferences prefs;
    private Context systemContext;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        log("INIT", ">>> Module loading into pkg=" + lpparam.packageName + " process=" + lpparam.processName);

        // 获取系统Context用于弹窗通知
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            if (activityThread != null) {
                systemContext = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
                log("INIT", "  ✓ got systemContext");
            }
        } catch (Throwable t) {
            log("INIT", "  ✗ systemContext failed: " + t.getMessage());
        }

        // === Hook 1: SmsMessage.createFromPdu(byte[], String) ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.SmsMessage",
                lpparam.classLoader,
                "createFromPdu",
                byte[].class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            SmsMessage msg = (SmsMessage) param.getResult();
                            if (msg == null) return;
                            handleSms(msg, lpparam.packageName, "createFromPdu(2)");
                        } catch (Throwable t) {
                            log("ERROR", "hook2 error: " + t.getMessage());
                        }
                    }
                }
            );
            log("INIT", "  ✓ hooked createFromPdu(byte[],String) in " + lpparam.packageName);
        } catch (Throwable t) {
            log("INIT", "  ✗ skip 2-arg in " + lpparam.packageName + ": " + t.getMessage());
        }

        // === Hook 2: SmsMessage.createFromPdu(byte[]) ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.SmsMessage",
                lpparam.classLoader,
                "createFromPdu",
                byte[].class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            SmsMessage msg = (SmsMessage) param.getResult();
                            if (msg == null) return;
                            handleSms(msg, lpparam.packageName, "createFromPdu(1)");
                        } catch (Throwable t) {
                            log("ERROR", "hook1 error: " + t.getMessage());
                        }
                    }
                }
            );
            log("INIT", "  ✓ hooked createFromPdu(byte[]) in " + lpparam.packageName);
        } catch (Throwable ignored) {}

        // === Hook 3: SmsMessage.getDisplayMessageBody() - 额外兜底 ===
        // 有些ROM走这个方法而不是createFromPdu
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.SmsMessage",
                lpparam.classLoader,
                "getDisplayMessageBody",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            String body = (String) param.getResult();
                            if (body == null || body.length() < 4) return;
                            // 只有第一次调用时处理（避免重复）
                            String key = body.hashCode() + "_" + System.currentTimeMillis() / 5000;
                            if (body.startsWith("话费") || body.contains("余额") || body.contains("10086")) {
                                SmsMessage msg = (SmsMessage) param.thisObject;
                                String sender = msg.getOriginatingAddress();
                                log("SMS_DISPLAY", "getDisplayMessageBody triggered from=" + sender + " body=" + body);
                            }
                        } catch (Throwable t) {
                            // ignore
                        }
                    }
                }
            );
            log("INIT", "  ✓ hooked getDisplayMessageBody() in " + lpparam.packageName);
        } catch (Throwable ignored) {}

        // === Hook 4: ContentResolver.insert for sms inbox ===
        // 这是最可靠的方式：当短信被写入数据库时触发
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContentResolver",
                lpparam.classLoader,
                "insert",
                android.net.Uri.class,
                android.content.ContentValues.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            android.net.Uri uri = (android.net.Uri) param.args[0];
                            if (uri == null) return;
                            String uriStr = uri.toString();
                            // 只关注短信入库
                            if (!uriStr.contains("sms") && !uriStr.contains("Sms")) return;
                            
                            android.content.ContentValues values = (android.content.ContentValues) param.args[1];
                            if (values == null) return;
                            
                            String address = values.getAsString("address");
                            String body = values.getAsString("body");
                            if (body == null || body.length() < 2) return;
                            if (address == null) address = "";
                            
                            log("SMS_INSERT", "短信入库! from=[" + address + "] body=[" + body + "]");
                            processSmsContent(address, body, lpparam.packageName, "ContentResolver.insert");
                        } catch (Throwable t) {
                            // 不要让异常影响系统
                        }
                    }
                }
            );
            log("INIT", "  ✓ hooked ContentResolver.insert() in " + lpparam.packageName);
        } catch (Throwable t) {
            log("INIT", "  ✗ ContentResolver hook failed: " + t.getMessage());
        }
    }

    private void handleSms(SmsMessage msg, String pkg, String hookSrc) {
        String sender = msg.getOriginatingAddress();
        String body = msg.getMessageBody();
        if (sender == null) sender = "";
        if (body == null) body = "";

        log("SMS_PDU", "收到短信! from=[" + sender + "] pkg=[" + pkg + "] hook=[" + hookSrc + "]");
        log("SMS_PDU", "  内容=[" + body + "]");
        
        processSmsContent(sender, body, pkg, hookSrc);
    }

    private void processSmsContent(String sender, String body, String pkg, String source) {
        loadPrefs();
        if (!prefs.getBoolean(Config.KEY_ENABLED, Config.DEFAULT_ENABLED)) {
            log("SKIP", "模块已禁用, 忽略短信 from=" + sender);
            return;
        }

        String senderRegex = prefs.getString(Config.KEY_SENDER_REGEX, Config.DEFAULT_SENDER_REGEX);
        String balanceRegex = prefs.getString(Config.KEY_BALANCE_REGEX, Config.DEFAULT_BALANCE_REGEX);

        // 校验发件人
        try {
            if (!Pattern.compile(senderRegex).matcher(sender).find()) {
                log("SKIP", "发件人不匹配 sender_regex=[" + senderRegex + "], from=[" + sender + "], 忽略");
                return;
            }
        } catch (Throwable t) {
            log("ERROR", "sender_regex 编译错误: " + t.getMessage());
            return;
        }

        log("MATCH_SENDER", "✓ 发件人匹配成功! from=[" + sender + "]");

        // 提取余额
        String balance = null;
        String matchDetail = null;
        try {
            Matcher m = Pattern.compile(balanceRegex).matcher(body);
            if (m.find() && m.groupCount() >= 1) {
                balance = m.group(1);
                matchDetail = m.group(0);
                log("MATCH", "✓ 余额正则命中! balance=" + balance + " 匹配段=[" + matchDetail + "]");
            } else {
                log("SKIP", "余额正则未命中, 不发送webhook. body=[" + body + "]");
                // 仍然弹窗告知收到了短信
                showAlertNotification("收到短信(未匹配余额)", "来自: " + sender + "\n内容: " + body);
                return;
            }
        } catch (Throwable t) {
            log("ERROR", "balance_regex 编译错误: " + t.getMessage());
            return;
        }

        // 识别成功 -> 弹窗通知 + 发送webhook
        String alertMsg = "✅ 识别成功!\n发件人: " + sender + "\n余额: " + balance + " 元\n匹配: " + matchDetail;
        showAlertNotification("话费余额识别成功", alertMsg);

        sendWebhook(sender, body, balance);
    }

    private void showAlertNotification(String title, String msg) {
        if (systemContext == null) {
            log("WARN", "无法弹窗: systemContext为null");
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) systemContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            // 创建通知渠道 (Android 8+)
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "话费识别提醒", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("短信话费余额识别结果通知");
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(channel);
            }

            // 点击跳转到App日志页
            Intent intent = new Intent();
            intent.setClassName("com.summer.mobilebalance", "com.summer.mobilebalance.LogActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(systemContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification notification;
            if (Build.VERSION.SDK_INT >= 26) {
                notification = new Notification.Builder(systemContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("📱 " + title)
                    .setContentText(msg)
                    .setStyle(new Notification.BigTextStyle().bigText(msg))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setDefaults(Notification.FLAG_INSISTENT)
                    .build();
            } else {
                notification = new Notification.Builder(systemContext)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("📱 " + title)
                    .setContentText(msg)
                    .setStyle(new Notification.BigTextStyle().bigText(msg))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            }

            nm.notify((int) (System.currentTimeMillis() % 10000), notification);
            log("NOTIFY", "✓ 弹窗通知已发送: " + title);
        } catch (Throwable t) {
            log("ERROR", "弹窗通知失败: " + t.getMessage());
        }
    }

    private void loadPrefs() {
        if (prefs == null) {
            prefs = new XSharedPreferences("com.summer.mobilebalance", Config.PREF_NAME);
            prefs.makeWorldReadable();
        }
        prefs.reload();
    }

    private void sendWebhook(final String sender, final String body, final String balance) {
        final String url = prefs.getString(Config.KEY_WEBHOOK_URL, Config.DEFAULT_WEBHOOK_URL);
        final String method = prefs.getString(Config.KEY_WEBHOOK_METHOD, Config.DEFAULT_WEBHOOK_METHOD).toUpperCase();
        final String tpl = prefs.getString(Config.KEY_WEBHOOK_BODY, Config.DEFAULT_WEBHOOK_BODY);
        final String headers = prefs.getString(Config.KEY_WEBHOOK_HEADERS, Config.DEFAULT_WEBHOOK_HEADERS);

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String ts = String.valueOf(System.currentTimeMillis());
                    String payload = tpl
                        .replace("{balance}", balance)
                        .replace("{sender}", sender)
                        .replace("{content}", body)
                        .replace("{timestamp}", ts);

                    String finalUrl = url;
                    if ("GET".equals(method)) {
                        String sep = url.contains("?") ? "&" : "?";
                        finalUrl = url + sep
                            + "balance=" + URLEncoder.encode(balance, "UTF-8")
                            + "&sender=" + URLEncoder.encode(sender, "UTF-8")
                            + "&content=" + URLEncoder.encode(body, "UTF-8")
                            + "&ts=" + ts;
                    }

                    log("POST", "→ " + method + " " + finalUrl);
                    if (!"GET".equals(method)) {
                        log("POST", "  body=" + payload);
                    }

                    URL u = new URL(finalUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod(method);

                    if (headers != null && !headers.isEmpty()) {
                        for (String line : headers.split("\\r?\\n")) {
                            int idx = line.indexOf(':');
                            if (idx > 0) {
                                String k = line.substring(0, idx).trim();
                                String v = line.substring(idx + 1).trim();
                                if (!k.isEmpty()) conn.setRequestProperty(k, v);
                            }
                        }
                    }

                    if ("POST".equals(method) || "PUT".equals(method)) {
                        conn.setDoOutput(true);
                        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                        out.write(payload.getBytes("UTF-8"));
                        out.flush();
                        out.close();
                    }

                    int code = conn.getResponseCode();
                    String resp = readStream(code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream());
                    log("RESP", "← HTTP " + code + " " + resp);

                    // 发送结果弹窗
                    if (code >= 200 && code < 300) {
                        showAlertNotification("Webhook发送成功 ✅", "HTTP " + code + "\n余额: " + balance + "元");
                    } else {
                        showAlertNotification("Webhook发送失败 ❌", "HTTP " + code + " " + resp);
                    }
                } catch (Throwable t) {
                    log("ERROR", "webhook异常: " + t.getMessage());
                    showAlertNotification("Webhook发送失败 ❌", t.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "MB-Webhook").start();
    }

    private static String readStream(InputStream in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String l;
            int n = 0;
            while ((l = br.readLine()) != null && n++ < 20) {
                sb.append(l);
                if (sb.length() > 500) { sb.append("..."); break; }
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) try { br.close(); } catch (Throwable ignored) {}
        }
        return sb.toString();
    }

    private void log(String type, String msg) {
        try {
            if (prefs == null || prefs.getBoolean(Config.KEY_LOG_ENABLED, Config.DEFAULT_LOG_ENABLED)) {
                XposedBridge.log("[" + TAG + "][" + type + "] " + msg);
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "][" + type + "] " + msg);
        }
        LogStore.append(type, msg);
    }
}
