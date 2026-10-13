package com.stupidbeauty.hxaccounting.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PDF 文件保存器
 * 把 BillPdfExporter 生成的 PDF 保存到手机的 Download 目录
 *
 * Android 版本兼容:
 * - Android 10+ (API 29+): 用 MediaStore API，**无需 WRITE_EXTERNAL_STORAGE 权限**
 * - Android 9 及以下 (API <= 28): 用传统文件 API + Environment.getExternalStoragePublicDirectory
 *
 * 关联任务: #918517007460 (子任务-保存)
 */
public class PdfFileSaver {

    private static final String TAG = "PdfFileSaver";

    // 保存目录名（Download 下）
    private static final String SUBDIR_NAME = "太极记账账单";

    // MIME 类型
    private static final String MIME_TYPE_PDF = "application/pdf";

    private final Context context;

    public PdfFileSaver(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 保存 PDF 到 Download 目录
     *
     * @param sourceFile  BillPdfExporter 生成的临时 PDF 文件
     * @param accountName 账本名（用于文件名）
     * @return 保存后的 Uri（用于"打开文件"按钮）
     * @throws IOException IO 异常
     */
    public Uri saveToDownload(File sourceFile, String accountName) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("源 PDF 文件不存在");
        }

        String fileName = generateFileName(accountName);
        long timestamp = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：用 MediaStore API（无需权限）
            return saveViaMediaStore(sourceFile, fileName, timestamp);
        } else {
            // Android 9 及以下：传统文件 API（需要 WRITE_EXTERNAL_STORAGE 权限）
            return saveViaLegacyFile(sourceFile, fileName);
        }
    }

    // ============================================================
    // Android 10+ (API 29+): MediaStore API
    // ============================================================

    private Uri saveViaMediaStore(File sourceFile, String fileName, long timestamp) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_PDF);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR_NAME);
        values.put(MediaStore.Downloads.IS_PENDING, 1); // 标记为正在写入

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("无法创建 Download 目录条目");
        }

        try {
            // 写入文件内容
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

                Log.d(TAG, "PDF saved: " + uri + " (" + totalBytes + " bytes)");
            }

            // 标记写入完成
            ContentValues updateValues = new ContentValues();
            updateValues.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, updateValues, null, null);

            return uri;
        } catch (IOException e) {
            // 写入失败，删除半成品
            Log.e(TAG, "PDF save failed, cleaning up: " + uri, e);
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    // ============================================================
    // Android 9 及以下 (API <= 28): 传统文件 API
    // ============================================================

    private Uri saveViaLegacyFile(File sourceFile, String fileName) throws IOException {
        // Download/太极记账账单/ 目录
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File subDir = new File(downloadDir, SUBDIR_NAME);

        if (!subDir.exists() && !subDir.mkdirs()) {
            throw new IOException("无法创建目录: " + subDir.getAbsolutePath());
        }

        File destFile = new File(subDir, fileName);

        // 复制文件
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new java.io.FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int read;
            long totalBytes = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBytes += read;
            }
            out.flush();

            Log.d(TAG, "PDF saved: " + destFile.getAbsolutePath() + " (" + totalBytes + " bytes)");
        }

        // 通知媒体库（让文件管理器立即看到）
        notifyMediaScanner(destFile);

        return Uri.fromFile(destFile);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 生成文件名：{账本名}_{时间}.pdf
     * 清理账本名中的非法字符
     */
    private String generateFileName(String accountName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA);
        String timestamp = sdf.format(new Date());
        String safeName = sanitizeFileName(accountName);
        return safeName + "_" + timestamp + ".pdf";
    }

    /**
     * 清理文件名中的非法字符
     * Windows / Android / Linux 都禁止的字符: \ / : * ? " < > |
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "账本";
        }
        return name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
    }

    /**
     * 通知媒体库扫描新文件
     * 让文件管理器立即能看到
     */
    private void notifyMediaScanner(File file) {
        try {
            android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{MIME_TYPE_PDF},
                    null
            );
        } catch (Exception e) {
            Log.w(TAG, "MediaScanner notify failed (non-fatal)", e);
        }
    }
}
