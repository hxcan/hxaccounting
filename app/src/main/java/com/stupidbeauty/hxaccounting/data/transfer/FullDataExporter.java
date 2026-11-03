package com.stupidbeauty.hxaccounting.data.transfer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.stupidbeauty.hxaccounting.data.dao.AccountDao;
import com.stupidbeauty.hxaccounting.data.dao.CategoryDao;
import com.stupidbeauty.hxaccounting.data.dao.TransactionDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.Category;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 全量账本数据导出器（JSON 格式）
 *
 * <p>把**全部**账本、分类、流水一次性导出为结构化 JSON 文件。
 *
 * <p><b>收费原则</b>：导出用户自己的数据<b>永久免费</b>，不设任何门槛。
 * 本类及 {@link FullDataImporter} 中不存在任何付费 / 会员 / 积分判断分支。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>零第三方依赖 —— 手写 JSON 序列化，APK 体积不变</li>
 *   <li>流式写入 —— 逐条写盘，不把全量数据拼成一个大 String，避免大库 OOM</li>
 *   <li>只存业务语义，不依赖数据库自增主键 —— 导入时建立 ID 映射，跨设备不冲突</li>
 *   <li>Android 10+ 用 MediaStore 保存，<b>不申请任何存储权限</b>（沿用 #861731247062 结论）</li>
 * </ul>
 *
 * <p>关联任务：#937493315851
 *
 * @author 未来姐姐
 * @since 2026-11-04
 */
public class FullDataExporter {

    private static final String TAG = "FullDataExporter";

    /** 导出文件格式标识 */
    public static final String FORMAT_ID = "taiji-accounting-export";

    /** 导出格式版本（结构变更时 +1） */
    public static final int FORMAT_VERSION = 1;

    /** 保存目录名（Download 下） */
    private static final String SUBDIR_NAME = "太极记账导出";

    /** MIME 类型 */
    private static final String MIME_TYPE_JSON = "application/json";

    private final Context context;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;

    public FullDataExporter(Context context) {
        this.context = context.getApplicationContext();
        TaijiDatabase db = TaijiDatabase.getInstance(this.context);
        this.accountDao = db.accountDao();
        this.categoryDao = db.categoryDao();
        this.transactionDao = db.transactionDao();
    }

    /**
     * 导出全部账本数据到 JSON 文件（保存到 Download 目录）
     *
     * <p><b>调用方必须在后台线程执行</b>（Room 禁止主线程同步查询）。
     *
     * @return 导出结果（包含保存后的 Uri 与统计数据）
     * @throws IOException IO 异常
     */
    public ExportResult exportAllToDownload() throws IOException {
        File cacheDir = new File(context.getCacheDir(), "data_transfer");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("无法创建缓存目录: " + cacheDir.getAbsolutePath());
        }
        String fileName = generateFileName();
        File tempFile = new File(cacheDir, fileName);

        ExportStats stats = writeJsonFile(tempFile);

        Uri savedUri;
        try {
            savedUri = saveToDownload(tempFile, fileName);
        } finally {
            // 无论成功失败都清理临时文件
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }

        Log.i(TAG, "导出完成：账本 " + stats.accountCount
                + " 个，分类 " + stats.categoryCount
                + " 个，流水 " + stats.transactionCount + " 笔");

        return new ExportResult(savedUri, stats);
    }

    /**
     * 把全量数据写成 JSON 文件
     *
     * @param outputFile 输出文件
     * @return 统计数据
     * @throws IOException IO 异常
     */
    private ExportStats writeJsonFile(File outputFile) throws IOException {
        List<Account> accounts = accountDao.getAllAccountsSync();
        List<Category> categories = categoryDao.getAllCategoriesSync();
        List<Transaction> transactions = transactionDao.getAllTransactionsSync();

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

            writer.write('{');
            writer.newLine();

            // 头部元信息
            writer.write("  \"format\": \"" + FORMAT_ID + "\",");
            writer.newLine();
            writer.write("  \"formatVersion\": " + FORMAT_VERSION + ",");
            writer.newLine();
            writer.write("  \"exportedAt\": \"" + isoFormat.format(new Date()) + "\",");
            writer.newLine();
            writer.write("  \"appVersion\": \"" + appVersion() + "\",");
            writer.newLine();

            // 账本
            writer.write("  \"accounts\": [");
            writer.newLine();
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                writer.write("    {");
                writer.write("\"id\": " + a.getId() + ", ");
                writer.write("\"name\": " + jsonString(a.getName()) + ", ");
                writer.write("\"type\": " + jsonString(a.getType()) + ", ");
                writer.write("\"color\": " + jsonString(a.getColor()) + ", ");
                writer.write("\"icon\": " + jsonString(a.getIcon()) + ", ");
                writer.write("\"budget\": " + a.getBudget() + ", ");
                writer.write("\"sortOrder\": " + a.getSortOrder() + ", ");
                writer.write("\"isArchived\": " + a.isArchived() + ", ");
                writer.write("\"createdAt\": " + a.getCreatedAt() + ", ");
                writer.write("\"updatedAt\": " + a.getUpdatedAt());
                writer.write("}");
                if (i < accounts.size() - 1) {
                    writer.write(",");
                }
                writer.newLine();
            }
            writer.write("  ],");
            writer.newLine();

            // 分类
            writer.write("  \"categories\": [");
            writer.newLine();
            for (int i = 0; i < categories.size(); i++) {
                Category c = categories.get(i);
                writer.write("    {");
                writer.write("\"id\": " + c.getId() + ", ");
                writer.write("\"name\": " + jsonString(c.getName()) + ", ");
                writer.write("\"parentId\": " + (c.getParentId() == null ? "null" : c.getParentId()) + ", ");
                writer.write("\"icon\": " + jsonString(c.getIcon()) + ", ");
                writer.write("\"color\": " + jsonString(c.getColor()) + ", ");
                writer.write("\"type\": " + jsonString(c.getType()) + ", ");
                writer.write("\"sortOrder\": " + c.getSortOrder() + ", ");
                writer.write("\"isSystem\": " + c.isSystem() + ", ");
                writer.write("\"isArchived\": " + c.isArchived() + ", ");
                writer.write("\"createdAt\": " + c.getCreatedAt());
                writer.write("}");
                if (i < categories.size() - 1) {
                    writer.write(",");
                }
                writer.newLine();
            }
            writer.write("  ],");
            writer.newLine();

            // 流水
            writer.write("  \"transactions\": [");
            writer.newLine();
            for (int i = 0; i < transactions.size(); i++) {
                Transaction t = transactions.get(i);
                writer.write("    {");
                writer.write("\"accountId\": " + t.getAccountId() + ", ");
                writer.write("\"amount\": " + t.getAmount() + ", ");
                writer.write("\"type\": " + jsonString(t.getType()) + ", ");
                writer.write("\"categoryId\": " + (t.getCategoryId() == null ? "null" : t.getCategoryId()) + ", ");
                writer.write("\"subcategory\": " + jsonString(t.getSubcategory()) + ", ");
                writer.write("\"description\": " + jsonString(t.getDescription()) + ", ");
                writer.write("\"recipient\": " + jsonString(t.getRecipient()) + ", ");
                writer.write("\"paymentMethod\": " + jsonString(t.getPaymentMethod()) + ", ");
                writer.write("\"transactionTime\": " + t.getTransactionTime() + ", ");
                writer.write("\"isAnomaly\": " + t.isAnomaly() + ", ");
                writer.write("\"source\": " + jsonString(t.getSource()) + ", ");
                writer.write("\"createdAt\": " + t.getCreatedAt() + ", ");
                writer.write("\"updatedAt\": " + t.getUpdatedAt());
                writer.write("}");
                if (i < transactions.size() - 1) {
                    writer.write(",");
                }
                writer.newLine();
            }
            writer.write("  ]");
            writer.newLine();

            writer.write('}');
            writer.newLine();
            writer.flush();
        }

        return new ExportStats(accounts.size(), categories.size(), transactions.size());
    }

    // ====================================================================
    // 保存到 Download 目录
    // ====================================================================

    /**
     * 保存文件到 Download 目录
     * Android 10+ 用 MediaStore（无需权限），Android 9 及以下用传统文件 API
     */
    private Uri saveToDownload(File sourceFile, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveViaMediaStore(sourceFile, fileName);
        } else {
            return saveViaLegacyFile(sourceFile, fileName);
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri saveViaMediaStore(File sourceFile, String fileName) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_JSON);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR_NAME);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("无法创建 Download 目录条目");
        }

        try {
            try (OutputStream out = resolver.openOutputStream(uri);
                 InputStream in = new FileInputStream(sourceFile)) {
                if (out == null) {
                    throw new IOException("无法打开输出流: " + uri);
                }
                byte[] buffer = new byte[8192];
                int read;
                long totalBytes = 0;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalBytes += read;
                }
                out.flush();
                Log.i(TAG, "JSON saved: " + uri + " (" + totalBytes + " bytes)");
            }
            ContentValues updateValues = new ContentValues();
            updateValues.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, updateValues, null, null);
            return uri;
        } catch (IOException e) {
            Log.e(TAG, "JSON 保存失败，清理半成品: " + uri, e);
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    private Uri saveViaLegacyFile(File sourceFile, String fileName) throws IOException {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File subDir = new File(downloadDir, SUBDIR_NAME);
        if (!subDir.exists() && !subDir.mkdirs()) {
            throw new IOException("无法创建目录: " + subDir.getAbsolutePath());
        }
        File destFile = new File(subDir, fileName);
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Log.i(TAG, "JSON saved: " + destFile.getAbsolutePath());
        }
        notifyMediaScanner(destFile);
        return Uri.fromFile(destFile);
    }

    private void notifyMediaScanner(File file) {
        try {
            android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{MIME_TYPE_JSON},
                    null
            );
        } catch (Exception e) {
            Log.w(TAG, "MediaScanner notify failed (non-fatal)", e);
        }
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /**
     * 生成文件名：taiji_export_{yyyyMMdd_HHmmss}.json
     */
    private String generateFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
        return "taiji_export_" + sdf.format(new Date()) + ".json";
    }

    /**
     * JSON 字符串转义
     * null → null（非字符串）；其余加引号并转义特殊字符
     */
    static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private String appVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ====================================================================
    // 结果对象
    // ====================================================================

    /**
     * 导出统计
     */
    public static class ExportStats {
        public final int accountCount;
        public final int categoryCount;
        public final int transactionCount;

        public ExportStats(int accountCount, int categoryCount, int transactionCount) {
            this.accountCount = accountCount;
            this.categoryCount = categoryCount;
            this.transactionCount = transactionCount;
        }
    }

    /**
     * 导出结果
     */
    public static class ExportResult {
        public final Uri uri;
        public final ExportStats stats;

        public ExportResult(Uri uri, ExportStats stats) {
            this.uri = uri;
            this.stats = stats;
        }
    }
}
