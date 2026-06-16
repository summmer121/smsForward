package com.summer.mobilebalance;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogStore {
    private static final SimpleDateFormat FMT =
        new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);

    // 日志路径优先级：App私有目录(无需权限) > /sdcard > /data/local/tmp
    private static String[] PATHS;

    /** 由MainActivity调用，初始化日志目录并设置路径 */
    public static synchronized void initLogDirs(Context ctx) {
        String appDir = ctx.getExternalFilesDir(null) != null
            ? ctx.getExternalFilesDir(null).getAbsolutePath()
            : ctx.getFilesDir().getAbsolutePath();
        PATHS = new String[] {
            appDir + "/mobile_balance.log",           // App私有外部存储 (无需权限)
            "/sdcard/MobileBalance/sms.log",           // 公共sdcard
            "/data/local/tmp/mobile_balance.log",      // tmp (xposed进程可写)
        };
        // 确保第一个目录存在
        ensureDir(new File(PATHS[0]).getParentFile());
        // 写一条启动日志
        append("APP", "App启动, 日志目录=" + PATHS[0]);
    }

    /** Xposed进程调用（无Context），使用默认路径 */
    private static synchronized String[] getPaths() {
        if (PATHS != null) return PATHS;
        PATHS = new String[] {
            "/data/local/tmp/mobile_balance.log",
            "/sdcard/MobileBalance/sms.log",
            "/sdcard/Android/data/com.summer.mobilebalance/files/mobile_balance.log",
        };
        return PATHS;
    }

    public static synchronized String resolveFile() {
        for (String p : getPaths()) {
            File f = new File(p);
            if (f.exists() && f.length() > 0) return p;
        }
        return getPaths()[0];
    }

    public static void append(String tag, String msg) {
        String line = "[" + FMT.format(new Date()) + "] [" + tag + "] " + msg + "\n";
        // 尝试写入所有可用路径
        for (String path : getPaths()) {
            if (tryAppend(path, line)) return;
        }
        // 全部失败，尝试用runtime exec写
        try {
            String tmpFile = "/data/local/tmp/mobile_balance.log";
            ensureDir(new File(tmpFile).getParentFile());
            Runtime rt = Runtime.getRuntime();
            // 用echo追加
            String escaped = line.replace("'", "'\\''");
            rt.exec(new String[]{"sh", "-c", "echo '" + escaped + "' >> " + tmpFile});
            rt.exec(new String[]{"chmod", "666", tmpFile});
        } catch (Throwable ignored) {}
    }

    private static boolean tryAppend(String path, String line) {
        FileOutputStream fos = null;
        try {
            File f = new File(path);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                ensureDir(dir);
            }
            if (!f.exists()) {
                f.createNewFile();
                chmod("666", path);
            }
            // 超过512KB截断
            if (f.length() > 512 * 1024) truncateHalf(f);
            fos = new FileOutputStream(path, true);
            fos.write(line.getBytes("UTF-8"));
            fos.flush();
            chmod("666", path);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (fos != null) try { fos.close(); } catch (IOException e) {}
        }
    }

    private static void ensureDir(File dir) {
        if (dir == null) return;
        if (!dir.exists()) {
            dir.mkdirs();
            chmod("777", dir.getAbsolutePath());
        }
    }

    private static void truncateHalf(File f) {
        try {
            long len = f.length();
            long keep = len / 2;
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw");
            raf.seek(len - keep);
            byte[] buf = new byte[(int) Math.min(keep, 256 * 1024)];
            int n = raf.read(buf);
            raf.setLength(0);
            raf.seek(0);
            raf.write("[--- log truncated ---]\n".getBytes("UTF-8"));
            raf.write(buf, 0, n);
            raf.close();
        } catch (Throwable ignored) {}
    }

    public static String readAll() {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String path : getPaths()) {
            File f = new File(path);
            if (f.exists() && f.length() > 0) {
                any = true;
                sb.append("==== ").append(path).append(" (").append(f.length()).append("B) ====\n");
                sb.append(readFile(f));
                sb.append("\n");
            }
        }
        if (!any) {
            sb.append("(暂无日志)\n\n排查步骤:\n");
            sb.append("1. 点击🔑权限按钮，确保所有权限已授予\n");
            sb.append("2. LSPosed启用模块 + 勾选作用域(com.android.phone, 系统短信App)\n");
            sb.append("3. 重启手机\n");
            sb.append("4. 收到短信后才有日志\n");
            sb.append("5. 日志路径: ").append(resolveFile()).append("\n");
        }
        return sb.toString();
    }

    private static String readFile(File f) {
        try {
            // 只读最后64KB避免内存溢出
            long len = f.length();
            long skip = 0;
            if (len > 64 * 1024) {
                skip = len - 64 * 1024;
            }
            FileInputStream fis = new FileInputStream(f);
            if (skip > 0) fis.skip(skip);
            byte[] buf = new byte[(int) Math.min(len, 64 * 1024)];
            int n = fis.read(buf);
            fis.close();
            String content = new String(buf, 0, n, "UTF-8");
            if (skip > 0) content = "[...前面已截断...]\n" + content;
            return content;
        } catch (Throwable t) {
            return "[读取失败: " + t.getMessage() + "]\n";
        }
    }

    public static boolean clear() {
        boolean any = false;
        for (String path : getPaths()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    FileOutputStream fos = new FileOutputStream(path, false);
                    fos.write(("[" + FMT.format(new Date()) + "] [INFO] 日志已清空\n").getBytes("UTF-8"));
                    fos.close();
                    chmod("666", path);
                    any = true;
                }
            } catch (Throwable ignored) {}
        }
        return any;
    }

    private static void chmod(String mode, String path) {
        try { Runtime.getRuntime().exec(new String[]{"chmod", mode, path}); } catch (Throwable ignored) {}
    }
}
