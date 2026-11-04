package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

    /** 请求码：系统文件选择器 */
    private static final int REQUEST_IMPORT_FILE = 1001;

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
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

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
     * 打开系统文件选择器（零权限，不需要存储权限）
     */
    private void startImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // 允许用户看到 JSON 文件
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/plain", "text/json", "*/*"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FILE);
        } catch (Exception e) {
            FileLogger.e(TAG, "无法打开文件选择器: " + e.getMessage());
            android.widget.Toast.makeText(this, "无法打开文件选择器", android.widget.Toast.LENGTH_SHORT).show();
        }
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
