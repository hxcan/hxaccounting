package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.transfer.FullDataExporter;
import com.stupidbeauty.hxaccounting.data.transfer.FullDataImporter;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据导入导出页面
 *
 * <p>太极记账的「数据自主」入口：
 * <ul>
 *   <li><b>导出全部数据</b> —— 把所有账本 / 分类 / 流水导出为 JSON 文件</li>
 *   <li><b>导入数据</b> —— 从 JSON 文件恢复数据（合并，不覆盖现有数据）</li>
 * </ul>
 *
 * <p><b>收费原则</b>：导出和导入用户自己的数据<b>永久免费</b>，不设任何门槛。
 *
 * <p>关联任务：#937493315851
 *
 * @author 未来姐姐
 * @since 2026-11-04
 */
public class DataTransferActivity extends AppCompatActivity {

    private static final String TAG = "DataTransferActivity";

    // 请求码：系统文件选择器
    private static final int REQUEST_IMPORT_FILE = 1001;

    // 请求码：存储权限（Android 9 及以下需要）
    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    private ExecutorService transferExecutor;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_transfer);

        transferExecutor = Executors.newSingleThreadExecutor();

        tvStatus = findViewById(R.id.tvTransferStatus);

        findViewById(R.id.btnExportAll).setOnClickListener(v -> startExport());
        findViewById(R.id.btnImportData).setOnClickListener(v -> startImportPicker());
        findViewById(R.id.btnAutoImport).setOnClickListener(v -> autoFindAndImport());

        FileLogger.i(TAG, "DataTransferActivity 初始化完成");
    }

    // ====================================================================
    // 导出
    // ====================================================================

    /**
     * 导出全部账本数据
     */
    private void startExport() {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("导出全部数据")
                .setMessage("正在导出，请稍候...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        transferExecutor.execute(() -> {
            try {
                FullDataExporter exporter = new FullDataExporter(this);
                FullDataExporter.ExportResult result = exporter.exportAllToDownload();

                FileLogger.i(TAG, "导出成功：URI = " + result.uri
                        + "，账本 " + result.stats.accountCount
                        + " 个，流水 " + result.stats.transactionCount + " 笔");

                final Uri uri = result.uri;
                final int accountCount = result.stats.accountCount;
                final int categoryCount = result.stats.categoryCount;
                final int transactionCount = result.stats.transactionCount;

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showExportSuccess(uri, accountCount, categoryCount, transactionCount);
                });

            } catch (Exception e) {
                FileLogger.e(TAG, "导出失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showError("导出失败", errorMsg);
                });
            }
        });
    }

    /**
     * 显示导出成功对话框
     */
    private void showExportSuccess(Uri uri, int accountCount, int categoryCount, int transactionCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("已导出全部账本数据：\n\n");
        sb.append("账本 ").append(accountCount).append(" 个\n");
        sb.append("分类 ").append(categoryCount).append(" 个\n");
        sb.append("流水 ").append(transactionCount).append(" 笔\n\n");
        sb.append("保存位置：\n").append(uri.getPath() != null ? uri.getPath() : uri.toString());

        new AlertDialog.Builder(this)
                .setTitle("导出成功")
                .setMessage(sb.toString())
                .setPositiveButton("分享", (dialog, which) -> shareFile(uri))
                .setNegativeButton("完成", null)
                .show();
    }

    /**
     * 分享导出文件
     */
    private void shareFile(Uri uri) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享数据文件"));
        } catch (Exception e) {
            FileLogger.w(TAG, "分享失败: " + e.getMessage());
            android.widget.Toast.makeText(this, "分享失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ====================================================================
    // 导入
    // ====================================================================

    /**
     * 打开导入入口
     *
     * <p><b>为什么用自建文件列表</b>：主人实测（Android 9，定制 ROM）发现
     * <p>系统文件选择器（ACTION_OPEN_DOCUMENT / ACTION_GET_CONTENT）无论怎么设 MIME，
     * <p>都只会打开「图片浏览器」，看不到 JSON 文件 —— 那台 ROM 的 DocumentsUI 写死了只处理图片。
     * <p>所以：先试系统选择器，同时提供一个自建的「浏览 Download 目录」入口作为可靠兼底。
     */
    private void startImportPicker() {
        new AlertDialog.Builder(this)
                .setTitle("导入数据")
                .setItems(new String[]{"📂 浏览 Download 目录（推荐）", "🔧 使用系统文件选择器"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showBuiltInFilePicker();
                            } else {
                                openSystemPicker();
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 系统文件选择器（三级降级）
     *
     * <p>注意：部分定制 ROM 无论传什么 MIME 都只开图片界面（主人实测）。
     * <p>所以推荐用自建列表 {@link #showBuiltInFilePicker()}。
     */
    private void openSystemPicker() {
        // 方案 1：ACTION_OPEN_DOCUMENT + 通配类型（不限制类型，显示所有文件）
        if (tryOpenPicker(Intent.ACTION_OPEN_DOCUMENT, null)) {
            return;
        }

        // 方案 2：ACTION_GET_CONTENT + EXTRA_MIME_TYPES（含通配）
        if (tryOpenPicker(Intent.ACTION_GET_CONTENT,
                new String[]{"*/*", "application/json", "text/plain", "text/json"})) {
            return;
        }

        // 方案 3：ACTION_GET_CONTENT + 通配类型
        if (tryOpenPicker(Intent.ACTION_GET_CONTENT, null)) {
            return;
        }

        // 全部失败：改用自建文件列表
        FileLogger.e(TAG, "无可用文件管理器，改为自建文件列表");
        showBuiltInFilePicker();
    }

    /**
     * 自建文件选择器：列出 Download 目录下的 JSON 文件
     *
     * <p>不依赖系统文件管理器，兼容任何 ROM。
     */
    private void showBuiltInFilePicker() {
        // Android 9 及以下需要存储权限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasStoragePermission()) {
            FileLogger.i(TAG, "缺少存储权限，先申请");
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return;
        }

        java.util.List<java.io.File> files = listJsonFiles();
        if (files.isEmpty()) {
            showError("未找到 JSON 文件",
                    "Download 目录下没找到 .json 文件。\n\n"
                            + "请把导出的 JSON 文件放到：\n"
                            + "Download/太极记账导出/\n\n"
                            + "或者直接放到 Download 根目录也可以。");
            return;
        }

        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            names[i] = files.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("选要导入的数据文件")
                .setItems(names, (dialog, which) -> {
                    java.io.File picked = files.get(which);
                    FileLogger.i(TAG, "自建选择器选中：" + picked.getAbsolutePath());
                    doImport(Uri.fromFile(picked));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 列出 Download 目录（含导出子目录）下的所有 JSON 文件，按修改时间倒序
     */
    private java.util.List<java.io.File> listJsonFiles() {
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();

        java.io.File external = Environment.getExternalStorageDirectory();
        if (external == null) {
            return candidates;
        }

        java.io.File downloadDir = new java.io.File(external, "Download");
        if (downloadDir.isDirectory()) {
            collectJsonFiles(downloadDir, candidates);
        }

        java.io.File exportDir = new java.io.File(downloadDir, "太极记账导出");
        if (exportDir.isDirectory()) {
            collectJsonFiles(exportDir, candidates);
        }

        java.io.File rootExportDir = new java.io.File(external, "太极记账导出");
        if (rootExportDir.isDirectory()) {
            collectJsonFiles(rootExportDir, candidates);
        }

        java.util.Collections.sort(candidates, (a, b) ->
                Long.compare(b.lastModified(), a.lastModified()));
        return candidates;
    }

    /**
     * 尝试打开文件选择器
     *
     * @param action      Intent action
     * @param mimeTypes   MIME 类型数组，null 表示不限制（使用 *&#47;*）
     * @return true 表示成功调起
     */
    private boolean tryOpenPicker(String action, String[] mimeTypes) {
        try {
            Intent intent = new Intent(action);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (mimeTypes != null && mimeTypes.length > 0) {
                intent.setType(mimeTypes[0]);
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            } else {
                intent.setType("*/*");
            }
            startActivityForResult(intent, REQUEST_IMPORT_FILE);
            return true;
        } catch (Exception e) {
            FileLogger.w(TAG, action + " 打开失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设备无可用文件管理器时的提示 + 自动扫描 Download 目录
     */
    private void showNoFileManagerHint() {
        new AlertDialog.Builder(this)
                .setTitle("无法打开文件选择器")
                .setMessage("这台设备没有可用的文件管理器。\n\n"
                        + "请把导出的 JSON 文件放到：\n"
                        + "Download/太极记账导出/\n\n"
                        + "放好后点「自动查找并导入」，姐姐会帮你找出来。")
                .setPositiveButton("自动查找并导入", (dialog, which) -> autoFindAndImport())
                .setNegativeButton("取消", null)
                .show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FILE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            FileLogger.i(TAG, "用户取消了文件选择");
            return;
        }
        Uri uri = data.getData();
        FileLogger.i(TAG, "用户选择了文件：" + uri);
        doImport(uri);
    }

    /**
     * 执行导入
     */
    private void doImport(Uri uri) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("导入数据")
                .setMessage("正在导入，请稍候...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        transferExecutor.execute(() -> {
            try {
                FullDataImporter importer = new FullDataImporter(this);
                FullDataImporter.ImportResult result = importer.importFromUri(uri);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showImportSuccess(result);
                });
            } catch (Exception e) {
                FileLogger.e(TAG, "导入失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showError("导入失败", errorMsg);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            FileLogger.i(TAG, "存储权限已授予，继续扫描");
            doAutoScan();
        } else {
            FileLogger.e(TAG, "存储权限被拒绝，无法扫描外置存储");
            showError("需要存储权限",
                    "Android 9 及以下需要存储权限才能读取 Download 目录。\n\n"
                            + "请到「设置 → 应用 → 权限 → 存储」中允许后重试。");
        }
    }

    /**
     * 自动在 Download 目录查找导出的 JSON 文件并导入
     *
     * <p>用于设备没有可用文件管理器的场景（部分定制 ROM）。
     * <p>Android 9 及以下需要 READ/WRITE_EXTERNAL_STORAGE 权限，否则读不到外置存储。
     */
    private void autoFindAndImport() {
        // Android 9 及以下：先申请存储权限，否则 listFiles() 返回 null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasStoragePermission()) {
            FileLogger.i(TAG, "缺少存储权限，先申请再扫描");
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return;
        }
        doAutoScan();
    }

    /**
     * 是否已有存储权限（Android 9 及以下）
     */
    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 实际执行扫描
     */
    private void doAutoScan() {
        transferExecutor.execute(() -> {
            try {
                java.io.File found = findExportFile();
                if (found == null) {
                    runOnUiThread(() -> showError("未找到数据文件",
                            "没在 Download 目录找到导出的 JSON 文件。\n\n"
                                    + "请确认文件已放到：\n"
                                    + "Download/太极记账导出/\n\n"
                                    + "然后再点一次「自动查找并导入」。"));
                    return;
                }

                FileLogger.i(TAG, "自动找到数据文件：" + found.getAbsolutePath());
                Uri uri = Uri.fromFile(found);
                runOnUiThread(() -> doImport(uri));
            } catch (Exception e) {
                FileLogger.e(TAG, "自动查找失败: " + e.getMessage());
                runOnUiThread(() -> showError("查找失败",
                        e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }
    /**
     * 在 Download 目录中查找导出的 JSON 数据文件（按修改时间倒序取最新）
     */
    private java.io.File findExportFile() {
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();

        java.io.File external = android.os.Environment.getExternalStorageDirectory();
        if (external == null) {
            return null;
        }

        // Download 根目录（public/Download，Android 9+ 标准位置）
        java.io.File downloadDir = new java.io.File(external, "Download");
        if (downloadDir.isDirectory()) {
            collectJsonFiles(downloadDir, candidates);
        }

        // Download/太极记账导出/ 导出子目录
        java.io.File exportDir = new java.io.File(downloadDir, "太极记账导出");
        if (exportDir.isDirectory()) {
            collectJsonFiles(exportDir, candidates);
        }

        // 兼容：部分 ROM 把导出目录直接放在 /sdcard/ 根下
        java.io.File rootExportDir = new java.io.File(external, "太极记账导出");
        if (rootExportDir.isDirectory()) {
            collectJsonFiles(rootExportDir, candidates);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 按修改时间倒序，最新的优先
        java.util.Collections.sort(candidates, (a, b) ->
                Long.compare(b.lastModified(), a.lastModified()));
        return candidates.get(0);
    }

    /**
     * 收集目录下的 JSON 文件（非递归）
     */
    private void collectJsonFiles(java.io.File dir, java.util.List<java.io.File> out) {
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File f : files) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName().toLowerCase(java.util.Locale.getDefault());
            if (name.endsWith(".json")) {
                out.add(f);
            }
        }
    }


    /**
     * 显示导入结果报告
     */
    private void showImportSuccess(FullDataImporter.ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("导入完成！\n\n");

        sb.append("账本：新增 ").append(result.newAccountCount)
                .append(" 个，复用 ").append(result.reusedAccountCount).append(" 个\n");
        sb.append("分类：新增 ").append(result.newCategoryCount)
                .append(" 个，复用 ").append(result.reusedCategoryCount).append(" 个\n");
        sb.append("流水：导入 ").append(result.importedTransactionCount)
                .append(" 笔，跳过重复 ").append(result.skippedTransactionCount).append(" 笔\n\n");

        if (result.skippedTransactionCount > 0) {
            sb.append("（重复流水已自动跳过，没有覆盖你的原有数据）");
        }

        new AlertDialog.Builder(this)
                .setTitle("导入成功")
                .setMessage(sb.toString())
                .setPositiveButton("完成", (dialog, which) -> finish())
                .show();
    }

    /**
     * 显示错误对话框
     */
    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transferExecutor != null) {
            transferExecutor.shutdown();
        }
    }
}