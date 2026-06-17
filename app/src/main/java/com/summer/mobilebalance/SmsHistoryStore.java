package com.summer.mobilebalance;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史短信存储。每条监听到的短信都会被存成一条JSON记录，
 * 包含：时间、发件人、原始内容、是否识别成功、识别到的余额。
 *
 * 存储路径与LogStore一致，优先App私有目录(无需权限)。
 * Xposed进程和App进程都会读写，需要world-readable/writable。
 */
public class SmsHistoryStore {
    private static final SimpleDateFormat FMT =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    /** 最多保留多少条历史记录 (循环覆盖最旧). */
    public static final int MAX_RECORDS = 100;

    /** App进程初始化后的优先路径. */
    private static String[] PATHS;

    public static synchronized void initDirs(Context ctx) {
        String appDir = ctx.getExternalFilesDir(null) != null
            ? ctx.getExternalFilesDir(null).getAbsolutePath()
            : ctx.getFilesDir().getAbsolutePath();
        // 重要: /data/local/tmp 排第一,因为Xposed进程在被hook的App里写不了我们自己的私有目录
        PATHS = new String[] {
            "/data/local/tmp/mobile_balance_sms_history.json",
            appDir + "/sms_history.json",
            "/sdcard/Android/data/com.summer.mobilebalance/files/sms_history.json",
        };
        File parent = new File(PATHS[1]).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    private static synchronized String[] getPaths() {
        if (PATHS != null) return PATHS;
        // Xposed进程默认路径 (没有Context)
        PATHS = new String[] {
            "/data/local/tmp/mobile_balance_sms_history.json",
            "/sdcard/Android/data/com.summer.mobilebalance/files/sms_history.json",
        };
        return PATHS;
    }

    /** Xposed进程调用：追加一条历史短信. */
    public static synchronized void addRecord(String sender, String body,
                                              boolean matched, String balance,
                                              String source) {
        try {
            JSONArray arr = readArray();

            // 去重: 5秒窗口内同 sender+body 不重复入库
            long now = System.currentTimeMillis();
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject prev = arr.optJSONObject(i);
                if (prev == null) continue;
                long pts = prev.optLong("ts", 0L);
                if (now - pts > 5000L) break; // 超出窗口,前面更老的不必比
                String ps = prev.optString("sender", "");
                String pb = prev.optString("body", "");
                if (ps.equals(sender == null ? "" : sender)
                        && pb.equals(body == null ? "" : body)) {
                    return; // 重复, 直接丢弃
                }
            }

            JSONObject o = new JSONObject();
            o.put("ts", now);
            o.put("time", FMT.format(new Date(now)));
            o.put("sender", sender == null ? "" : sender);
            o.put("body", body == null ? "" : body);
            o.put("matched", matched);
            o.put("balance", balance == null ? "" : balance);
            o.put("source", source == null ? "" : source);

            arr.put(o);

            // 限制条数：超过MAX_RECORDS时丢弃最旧的
            while (arr.length() > MAX_RECORDS) {
                arr.remove(0);
            }

            writeArray(arr);
        } catch (Throwable t) {
            // 静默失败,不影响主流程
        }
    }

    /** App进程调用：读出所有历史记录, 最新的在最前. */
    public static synchronized List<JSONObject> readAll() {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            JSONArray arr = readArray();
            for (int i = arr.length() - 1; i >= 0; i--) {  // 倒序：新→旧
                out.add(arr.getJSONObject(i));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static synchronized boolean clear() {
        try {
            writeArray(new JSONArray());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static JSONArray readArray() {
        for (String p : getPaths()) {
            File f = new File(p);
            if (!f.exists() || f.length() == 0 || !f.canRead()) continue;
            try {
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                fis.read(buf);
                fis.close();
                String s = new String(buf, "UTF-8");
                if (s.trim().isEmpty()) continue;
                return new JSONArray(s);
            } catch (Throwable ignored) {}
        }
        return new JSONArray();
    }

    private static void writeArray(JSONArray arr) {
        String content = arr.toString();
        for (String p : getPaths()) {
            FileOutputStream fos = null;
            try {
                File f = new File(p);
                File dir = f.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                    chmod("777", dir.getAbsolutePath());
                }
                fos = new FileOutputStream(f, false);
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
                chmod("666", p);
                return;
            } catch (Throwable ignored) {
            } finally {
                if (fos != null) try { fos.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void chmod(String mode, String path) {
        try { Runtime.getRuntime().exec(new String[]{"chmod", mode, path}); } catch (Throwable ignored) {}
    }
}
