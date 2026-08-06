package com.stupidbeauty.hxaccounting;

import android.app.Application;
import android.util.Log;

import com.stupidbeauty.crashdetector.CrashHandler;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

/**
 * 应用程序对象。
 *
 * 借鉴 sisterfuture 项目 (#4968) 的初始化方式，
 * 集成 android-crash-detector 库捕获真实崩溃日志。
 *
 * 任务 #859222728113 Phase 2 - Application 初始化
 */
public class HxAccountingApplication extends Application
{
    private static final String TAG = "HxAccountingApplication";

    @Override
    public void onCreate()
    {
        super.onCreate();

        // #859222728113 初始化全局崩溃检测器 - 使用 JitPack 库
        CrashHandler.init(this);
        Log.i(TAG, "✅ android-crash-detector 库已初始化 (v2026.4.8)");

        // #859768032855 初始化 FileLogger - 日志输出到 /sdcard/Download/hxaccounting_logs/
        FileLogger.init(this);
        FileLogger.i(TAG, "✅ FileLogger 初始化完成，日志目录：" + FileLogger.getLogDirPath());
        FileLogger.i(TAG, "当前日志文件：" + FileLogger.getCurrentLogFilePath());
    }
}