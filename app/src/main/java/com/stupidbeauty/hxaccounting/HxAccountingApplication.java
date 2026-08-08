package com.stupidbeauty.hxaccounting;

import android.app.Application;
import android.util.Log;

import com.stupidbeauty.crashdetector.CrashHandler;
import com.stupidbeauty.hxaccounting.utils.FileLogger;
import com.stupidbeauty.upgrademanager.UpgradeManager;
import com.stupidbeauty.upgrademanager.listener.PackageNameUrlMapDataListener;

import java.util.HashMap;
import java.util.List;

/**
 * 应用程序对象。
 *
 * 借鉴 sisterfuture 项目 (#4968) 的初始化方式，
 * 集成 android-crash-detector 库捕获真实崩溃日志。
 *
 * 任务 #859222728113 Phase 2 - Application 初始化
 *
 * 任务 #858208996466 Phase 3 - 接入升级管理器 UpgradeManager
 * (D8 - 接入更新管理器)
 */
public class HxAccountingApplication extends Application implements PackageNameUrlMapDataListener
{
    private static final String TAG = "HxAccountingApplication";

    // #858208996466 Phase 3 - 升级管理器（懒创建，首次 startCheckUpgrade 时实例化）
    private UpgradeManager upgradeManager = null;

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

        // #858208996466 Phase 3 - 启动时检查更新（升级管理器）
        startCheckUpgrade();
    }

    /**
     * #858208996466 Phase 3
     * 启动时检查更新。懒创建 UpgradeManager（避免冷启动阶段做重活），
     * 设置 listener 后调用 checkUpgrade() 触发远程版本检测。
     */
    private void startCheckUpgrade()
    {
        if (upgradeManager == null) // 升级管理器尚未创建
        {
            upgradeManager = new UpgradeManager(this); // 创建升级管理器
        }

        upgradeManager.setPackageNameUrlMapDataListener(this); // 设置数据回调监听器
        upgradeManager.checkUpgrade(); // 触发远程版本检查

        FileLogger.i(TAG, "✅ UpgradeManager 已启动，开始检查更新（task #858208996466）");
    }

    // ========== 以下为 PackageNameUrlMapDataListener 接口实现 ==========
    // 当前仅做占位实现（Phase 3 仅启动检查，不处理 UI 层回调）
    // Phase 4-5 会接入具体的弹窗 / 下载 / 安装流程

    @Override
    public void setVoicePackageUrlMap(HashMap<String, String> voicePackageUrlMap) {
        // TODO Phase 4-5: 处理语音包名-URL 映射数据
    }

    @Override
    public void setPackageNameUrlMap(HashMap<String, String> packageNameUrlMap) {
        // TODO Phase 4-5: 处理包名-下载URL 映射数据
    }

    @Override
    public void setPackageNameInstallerTypeMap(HashMap<String, String> packageNameInstallerTypeMap) {
        // TODO Phase 4-5: 处理包名-安装类型映射数据
    }

    @Override
    public void setPackageNameExtraPackageNamesMap(HashMap<String, List<String>> packageNameExtraPackageNamesMap) {
        // TODO Phase 4-5: 处理包名-额外包名映射数据
    }

    @Override
    public void setPackageNameInformationUrlMap(HashMap<String, String> packageNameInformationUrlMap) {
        // TODO Phase 4-5: 处理包名-信息URL 映射数据
    }

    @Override
    public void setPackageNameVersionNameMap(HashMap<String, String> packageNameVersionNameMap) {
        // TODO Phase 4-5: 处理包名-版本名映射数据
    }

    @Override
    public void setPackageNameApplicationNameMap(HashMap<String, String> packageNameApplicationNameMap) {
        // TODO Phase 4-5: 处理包名-应用名映射数据
    }

    @Override
    public void setPackageNameIconUrlMap(HashMap<String, String> packageNameIconUrlMap) {
        // TODO Phase 4-5: 处理包名-图标URL 映射数据
    }

    @Override
    public void setApkUrlPackageNameMap(HashMap<String, String> apkUrlPackageNameMap) {
        // TODO Phase 4-5: 处理APK URL-包名映射数据
    }

    @Override
    public void setPackages(List<com.stupidbeauty.appstore.bean.AndroidPackageInformation> packages) {
        // TODO Phase 4-5: 处理应用包列表数据
    }
}