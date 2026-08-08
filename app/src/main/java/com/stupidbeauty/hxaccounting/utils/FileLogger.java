package com.stupidbeauty.hxaccounting.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 文件日志工具类 - 借鉴自 sisterfuture 项目（FileLogger #4834）
 *
 * 功能：
 * 1. 日志输出到外置存储
 * 2. 按日期命名和分割（单文件最大 10MB）
 * 3. 自动清理超过 7 天的旧日志
 * 4. 敏感信息自动过滤
 *
 * 使用方式：
 * FileLogger.init(context);
 * FileLogger.d("TAG", "调试信息");
 * FileLogger.e("TAG", "错误信息");
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 全 catch 块嵌套 try-catch 兜底 Log.e 异常（任务 #861693812595）
 */
public class FileLogger {
    private static final String TAG = "FileLogger";

    // v2 修复（任务 #861693812595）：去掉 final，改用 lazy init
    // 原版 static final 字段会在类加载时执行 Environment.getExternalStorageDirectory()，
    // JVM 单元测试环境下抛 RuntimeException，导致 ExceptionInInitializerError。
    private static String LOG_DIR = null;

    // 单文件最大大小：10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 日志保留天数：7 天
    private static final int MAX_DAYS = 7;

    // 日志级别
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;

    // 当前日志级别（默认 DEBUG）
    private static int currentLevel = LEVEL_DEBUG;

    // 当前日志文件
    private static File currentLogFile = null;
    private static long currentFileSize = 0;

    // 敏感信息过滤正则
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(api[_-]?key|token|secret|password)[\"']?\\s*[:=]\\s*[\"']?[\\w-]+", Pattern.CASE_INSENSITIVE);

    /**
     * v2 修复（任务 #861693812595）：lazy 初始化 LOG_DIR
     * 避免类加载时调用 Environment.getExternalStorageDirectory() 导致单元测试失败
     */
    private static String getLogDir() {
        if (LOG_DIR == null) {
            LOG_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/hxaccounting_logs/";
        }
        return LOG_DIR;
    }

    /**
     * v3 修复（任务 #861693812595）：Log.e 在单元测试环境也可能抛 RuntimeException
     * 所有 catch 块里调 Log.e 时嵌套一层 try-catch 兜底
     */
    private static void safeLogE(String msg, Throwable t) {
        try {
            Log.e(TAG, msg, t);
        } catch (Throwable ignored) {
            // 单元测试环境 Log.e 也可能抛，吞掉
        }
    }

    /**
     * 初始化日志系统
     * 必须在 Application.onCreate() 中调用
     */
    public static void init(Context context) {
        try {
            // 创建日志目录
            File logDir = new File(getLogDir());
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                safeLogE("📁 创建日志目录：" + getLogDir() + " - " + (created ? "成功" : "失败"), null);
            }

            // 清理旧日志
            cleanupOldLogs();

            // 初始化当前日志文件
            rotateLogFile();

            safeLogE("✅ FileLogger 初始化完成", null);
        } catch (Exception e) {
            safeLogE("❌ FileLogger 初始化失败", e);
        }
    }

    /**
     * 设置日志级别
     * @param level 日志级别
     */
    public static void setLevel(int level) {
        currentLevel = level;
    }

    /**
     * 调试日志
     */
    public static void d(String tag, String message) {
        try {
            if (currentLevel <= LEVEL_DEBUG) {
                writeToFile("DEBUG", tag, message);
            }
        } catch (Throwable t) {
            safeLogE("d() 失败", t);
        }
    }

    /**
     * 普通信息
     */
    public static void i(String tag, String message) {
        try {
            if (currentLevel <= LEVEL_INFO) {
                writeToFile("INFO", tag, message);
            }
        } catch (Throwable t) {
            safeLogE("i() 失败", t);
        }
    }

    /**
     * 警告
     */
    public static void w(String tag, String message) {
        try {
            if (currentLevel <= LEVEL_WARN) {
                writeToFile("WARN", tag, message);
            }
        } catch (Throwable t) {
            safeLogE("w() 失败", t);
        }
    }

    /**
     * 错误
     */
    public static void e(String tag, String message) {
        try {
            if (currentLevel <= LEVEL_ERROR) {
                writeToFile("ERROR", tag, message);
            }
        } catch (Throwable t) {
            safeLogE("e() 失败", t);
        }
    }

    /**
     * 写入日志文件
     */
    private static void writeToFile(String level, String tag, String message) {
        try {
            if (currentLogFile == null || currentFileSize >= MAX_FILE_SIZE) {
                rotateLogFile();
            }

            if (currentLogFile == null) {
                return;
            }

            // 格式化日志行
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String filteredMessage = filterSensitiveInfo(message);
            String logLine = String.format("%s %s/%s: %s\n", timestamp, level, tag, filteredMessage);

            // 追加写入
            FileWriter writer = new FileWriter(currentLogFile, true);
            writer.append(logLine);
            writer.flush();
            writer.close();

            currentFileSize += logLine.getBytes().length;
        } catch (Throwable t) {
            safeLogE("写入日志文件失败", t);
        }
    }

    /**
     * 轮转日志文件（按日期或大小）
     */
    private static void rotateLogFile() {
        try {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String timeStr = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
            String logFileName = "hxaccounting_" + dateStr + "_" + timeStr + ".log";
            currentLogFile = new File(getLogDir() + logFileName);

            if (currentLogFile.exists()) {
                currentFileSize = currentLogFile.length();
            } else {
                currentFileSize = 0;
                currentLogFile.createNewFile();
            }
        } catch (Throwable t) {
            safeLogE("创建日志文件失败", t);
            currentLogFile = null;
            currentFileSize = 0;
        }
    }

    /**
     * 清理超过指定天数的旧日志
     */
    private static void cleanupOldLogs() {
        try {
            File logDir = new File(getLogDir());
            if (!logDir.exists()) {
                return;
            }

            File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith("hxaccounting_") && name.endsWith(".log"));
            if (logFiles == null || logFiles.length == 0) {
                return;
            }

            long now = System.currentTimeMillis();
            long maxAge = MAX_DAYS * 24 * 60 * 60 * 1000L;
            int deletedCount = 0;

            for (File logFile : logFiles) {
                long fileAge = now - logFile.lastModified();
                if (fileAge > maxAge) {
                    boolean deleted = logFile.delete();
                    if (deleted) {
                        deletedCount++;
                        safeLogE("🗑️ 删除旧日志文件：" + logFile.getName(), null);
                    }
                }
            }

            if (deletedCount > 0) {
                safeLogE("✅ 清理完成，共删除 " + deletedCount + " 个旧日志文件", null);
            }
        } catch (Exception e) {
            safeLogE("清理旧日志失败", e);
        }
    }

    /**
     * 过滤敏感信息
     */
    private static String filterSensitiveInfo(String message) {
        if (message == null) {
            return "";
        }
        String filtered = API_KEY_PATTERN.matcher(message).replaceAll("$1=[FILTERED]");
        filtered = filtered.replaceAll("[A-Za-z0-9]{32,}", "[KEY_FILTERED]");
        return filtered;
    }

    /**
     * 获取日志目录路径
     */
    public static String getLogDirPath() {
        return getLogDir();
    }

    /**
     * 获取当前日志文件路径
     */
    public static String getCurrentLogFilePath() {
        return currentLogFile != null ? currentLogFile.getAbsolutePath() : "N/A";
    }
}