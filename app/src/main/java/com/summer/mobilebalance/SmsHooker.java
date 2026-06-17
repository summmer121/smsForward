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

    // === 短信去重缓存 ===
    // 同一条短信会被多个 hook 点 (createFromPdu / getDisplayMessageBody / ContentProvider 等) 重复触发,
    // 长短信还会被运营商拆成多个 PDU 分片. 用 sender+body 摘要 + 5秒窗口去重.
    private static final java.util.Map<String, Long> RECENT_SMS = new java.util.HashMap<String, Long>();
    private static final long SMS_DEDUP_WINDOW_MS = 5000L;
    private static final Object SMS_DEDUP_LOCK = new Object();

    private static boolean isDuplicateSms(String sender, String body) {
        if (sender == null) sender = "";
        if (body == null) body = "";
        String key = sender + "|" + body;
        long now = System.currentTimeMillis();
        synchronized (SMS_DEDUP_LOCK) {
            // 清理过期 entry
            java.util.Iterator<java.util.Map.Entry<String, Long>> it = RECENT_SMS.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() > SMS_DEDUP_WINDOW_MS) it.remove();
            }
            Long last = RECENT_SMS.get(key);
            if (last != null && (now - last) < SMS_DEDUP_WINDOW_MS) {
                return true; // 是重复
            }
            RECENT_SMS.put(key, now);
            return false;
        }
    }

    private void processSmsContent(String sender, String body, String pkg, String source) {
        loadPrefs();
        if (!prefs.getBoolean(Config.KEY_ENABLED, Config.DEFAULT_ENABLED)) {
            log("SKIP", "模块已禁用, 忽略短信 from=" + sender);
            return;
        }

        // 同一条短信会被 createFromPdu / getDisplayMessageBody / ContentProvider 等多处触发,5秒窗口内去重
        if (isDuplicateSms(sender, body)) {
            // 只在 XposedBridge.log 留个痕,不污染 App 日志
            try { XposedBridge.log("[" + TAG + "][DEDUP] 跳过重复短信 from=" + sender + " src=" + source); } catch (Throwable ignored) {}
            return;
        }

        // 明确展示监听到的短信内容(单独一条,方便日志中定位)
        log("SMS_RAW", "════════════════════════════════════════");
        log("SMS_RAW", "📩 监听到短信");
        log("SMS_RAW", "  发件人: " + sender);
        log("SMS_RAW", "  来源:   " + source + " (pkg=" + pkg + ")");
        log("SMS_RAW", "  内容:   " + body);
        log("SMS_RAW", "════════════════════════════════════════");

        String senderRegex = prefs.getString(Config.KEY_SENDER_REGEX, Config.DEFAULT_SENDER_REGEX);
        String balanceRegex = prefs.getString(Config.KEY_BALANCE_REGEX, Config.DEFAULT_BALANCE_REGEX);

        // 校验发件人
        try {
            if (!Pattern.compile(senderRegex).matcher(sender).find()) {
                log("SKIP", "发件人不匹配 sender_regex=[" + senderRegex + "], from=[" + sender + "], 忽略");
                // 即使发件人不匹配也存历史(让用户看到都监听到了什么)
                SmsHistoryStore.addRecord(sender, body, false, "", source);
                broadcastHistory(sender, body, false, "", source);
                log("SUMMARY", "📊 收到短信 [from=" + sender + "] -> 发件人不匹配, 未识别余额");
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
                // 记录到历史
                SmsHistoryStore.addRecord(sender, body, false, "", source);
                broadcastHistory(sender, body, false, "", source);
                log("SUMMARY", "📊 收到短信 [from=" + sender + "] -> 余额正则未命中, 未识别余额");
                return;
            }
        } catch (Throwable t) {
            log("ERROR", "balance_regex 编译错误: " + t.getMessage());
            return;
        }

        // === 识别成功: 持久化最新余额和时间到 prefs (主界面会读取) ===
        try {
            long ts = System.currentTimeMillis();
            String tsStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.CHINA).format(new java.util.Date(ts));
            // XSharedPreferences 是只读的, 用直接写文件的方式保存
            saveLastBalance(balance, tsStr, ts, sender, body);
            // 通过广播让 App 进程落盘 (绕过SELinux)
            broadcastBalance(balance, tsStr, ts, sender, body);
            // 同时存历史短信
            SmsHistoryStore.addRecord(sender, body, true, balance, source);
            broadcastHistory(sender, body, true, balance, source);
            log("SAVE", "✓ 已保存最新余额到主界面显示: " + balance + " 元 @ " + tsStr);
            log("SUMMARY", "📊 收到短信 [from=" + sender + "] -> ✅ 识别余额: " + balance + " 元");
        } catch (Throwable t) {
            log("ERROR", "保存最新余额失败: " + t.getMessage());
        }

        // 识别成功 -> 弹窗通知 + 发送webhook
        String alertMsg = "✅ 识别成功!\n发件人: " + sender + "\n余额: " + balance + " 元\n匹配: " + matchDetail;
        showAlertNotification("话费余额识别成功", alertMsg);

        sendWebhook(sender, body, balance);
    }

    /** 
     * Xposed进程里保存最新余额信息，让主界面能读取.
     * 重要: Xposed hook 跑在被hook的App进程里 (如 com.android.phone), 
     *       没有权限写 /data/data/com.summer.mobilebalance/, 
     *       所以改用 /data/local/tmp 这个所有进程都可写的位置.
     */
    private void saveLastBalance(String balance, String tsStr, long ts, String sender, String body) {
        try {
            // 使用 /data/local/tmp 作为跨进程通信文件 (所有应用进程都可读写)
            String filePath = "/data/local/tmp/mobile_balance_last.json";
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("balance", balance);
            o.put("time", tsStr);
            o.put("ts", ts);
            o.put("sender", sender);
            o.put("body", body);

            java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath, false);
            fos.write(o.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
            try { Runtime.getRuntime().exec(new String[]{"chmod", "666", filePath}); } catch (Throwable ignored) {}
            log("SAVE", "  ✓ 写入跨进程文件: " + filePath);
        } catch (Throwable t) {
            log("ERROR", "saveLastBalance 写入失败: " + t.getMessage());
            // 兜底: 尝试老路径(prefs)
            try {
                String prefPath = "/data/data/com.summer.mobilebalance/shared_prefs/" + Config.PREF_NAME + ".xml";
                java.io.File pf = new java.io.File(prefPath);
                if (!pf.exists() || !pf.canWrite()) return;
                java.io.FileInputStream fis = new java.io.FileInputStream(pf);
                byte[] buf = new byte[(int) pf.length()];
                fis.read(buf);
                fis.close();
                String xml = new String(buf, "UTF-8");
                xml = upsertStringPref(xml, Config.KEY_LAST_BALANCE, balance);
                xml = upsertStringPref(xml, Config.KEY_LAST_BALANCE_TIME, tsStr);
                xml = upsertLongPref(xml, Config.KEY_LAST_BALANCE_TS, ts);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(pf);
                fos.write(xml.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Throwable ignored) {}
        }
    }

    private static String upsertStringPref(String xml, String key, String value) {
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
        String pattern = "<string name=\"" + key + "\">[^<]*</string>";
        String replacement = "<string name=\"" + key + "\">" + escaped + "</string>";
        if (xml.matches("(?s).*" + java.util.regex.Pattern.quote("<string name=\"" + key + "\">") + ".*")) {
            return xml.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        // 不存在则插入到 </map> 前
        return xml.replace("</map>", "    " + replacement + "\n</map>");
    }

    private static String upsertLongPref(String xml, String key, long value) {
        String pattern = "<long name=\"" + key + "\" value=\"[^\"]*\" />";
        String replacement = "<long name=\"" + key + "\" value=\"" + value + "\" />";
        if (xml.contains("<long name=\"" + key + "\"")) {
            return xml.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        return xml.replace("</map>", "    " + replacement + "\n</map>");
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
                    String tsStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            java.util.Locale.CHINA).format(new java.util.Date());

                    if (code >= 200 && code < 300) {
                        log("SEND_OK", "✅ Webhook发送成功 @ " + tsStr);
                        log("SEND_OK", "  HTTP状态: " + code);
                        log("SEND_OK", "  目标URL: " + finalUrl);
                        log("SEND_OK", "  发送内容: " + (("GET".equals(method)) ? "(GET参数已编码到URL)" : payload));
                        log("SEND_OK", "  服务端响应: " + resp);
                        log("SEND_OK", "  余额: " + balance + " 元");
                        showAlertNotification("Webhook发送成功 ✅", "HTTP " + code + "\n余额: " + balance + "元\n时间: " + tsStr);
                    } else {
                        log("SEND_FAIL", "❌ Webhook发送失败 @ " + tsStr);
                        log("SEND_FAIL", "  HTTP状态: " + code);
                        log("SEND_FAIL", "  服务端响应: " + resp);
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
        // 直接写文件 (兼容老路径,可能因SELinux失败,失败也无所谓)
        try { LogStore.append(type, msg); } catch (Throwable ignored) {}
        // 同时通过 Broadcast 发给 App 进程落盘 (绕过 SELinux 限制)
        try { broadcastLog(type, msg); } catch (Throwable ignored) {}
    }

    /** 给 App 自己的 Receiver 发 Intent, 由 App 进程负责真正落盘. */
    private void broadcastLog(String type, String msg) {
        if (systemContext == null) return;
        Intent i = new Intent("com.summer.mobilebalance.UPDATE");
        i.setPackage("com.summer.mobilebalance");
        i.putExtra("type", "log");
        i.putExtra("log_type", type);
        i.putExtra("log_msg", msg);
        try {
            systemContext.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    private void broadcastHistory(String sender, String body, boolean matched, String balance, String source) {
        if (systemContext == null) return;
        Intent i = new Intent("com.summer.mobilebalance.UPDATE");
        i.setPackage("com.summer.mobilebalance");
        i.putExtra("type", "history");
        i.putExtra("sender", sender == null ? "" : sender);
        i.putExtra("body", body == null ? "" : body);
        i.putExtra("matched", matched);
        i.putExtra("balance", balance == null ? "" : balance);
        i.putExtra("source", source == null ? "" : source);
        try {
            systemContext.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    private void broadcastBalance(String balance, String tsStr, long ts, String sender, String body) {
        if (systemContext == null) return;
        Intent i = new Intent("com.summer.mobilebalance.UPDATE");
        i.setPackage("com.summer.mobilebalance");
        i.putExtra("type", "balance");
        i.putExtra("balance", balance == null ? "" : balance);
        i.putExtra("time", tsStr == null ? "" : tsStr);
        i.putExtra("ts", ts);
        i.putExtra("sender", sender == null ? "" : sender);
        i.putExtra("body", body == null ? "" : body);
        try {
            systemContext.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }
}
