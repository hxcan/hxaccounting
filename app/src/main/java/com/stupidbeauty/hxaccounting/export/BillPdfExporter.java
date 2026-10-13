package com.stupidbeauty.hxaccounting.export;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.TextPaint;

import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.TransactionStatistics;
import com.stupidbeauty.hxaccounting.data.entity.TransactionWithCategory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PDF 账单导出器
 * 用 Android 原生 PdfDocument（API 19+）实现，零依赖
 *
 * PDF 结构：
 * - 第 1 页：封面（账本信息 + 收支汇总）
 * - 第 2 页起：流水明细表（每页 25 笔）
 *
 * 关联任务: #918517001614 (子任务-引擎)
 */
public class BillPdfExporter {

    // A4 纸张大小（单位：pt，1 pt = 1/72 inch）
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;

    // 页边距
    private static final int MARGIN_LEFT = 40;
    private static final int MARGIN_RIGHT = 40;
    private static final int MARGIN_TOP = 50;
    private static final int MARGIN_BOTTOM = 50;

    // 内容区域
    private static final int CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    // 每页明细行数
    private static final int ROWS_PER_PAGE = 25;

    // 行高
    private static final int ROW_HEIGHT = 24;

    // 表格列宽（总和 = CONTENT_WIDTH = 515）
    private static final int COL_DATE_WIDTH = 90;     // 日期
    private static final int COL_CATEGORY_WIDTH = 90; // 分类
    private static final int COL_AMOUNT_WIDTH = 100;  // 金额（右对齐）
    private static final int COL_DESC_WIDTH = 235;    // 备注

    // 字体大小
    private static final float TITLE_FONT_SIZE = 22f;
    private static final float SUBTITLE_FONT_SIZE = 16f;
    private static final float BODY_FONT_SIZE = 12f;
    private static final float SMALL_FONT_SIZE = 10f;

    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    public BillPdfExporter(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 导出账本为 PDF
     *
     * @param account       账本信息
     * @param transactions  流水列表（按时间倒序，含分类名）
     * @param statistics    收支统计
     * @param outputFile    输出文件（调用方创建）
     * @return 生成的 PDF 文件（即 outputFile）
     * @throws IOException IO 异常
     */
    public File export(
            Account account,
            List<TransactionWithCategory> transactions,
            TransactionStatistics statistics,
            File outputFile
    ) throws IOException {

        PdfDocument document = new PdfDocument();

        try {
            // 第 1 页：封面
            drawCoverPage(document, account, statistics);

            // 第 2 页起：明细（如果没流水就不画）
            if (transactions != null && !transactions.isEmpty()) {
                drawDetailPages(document, account, transactions);
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                document.writeTo(fos);
            }
        } finally {
            document.close();
        }

        return outputFile;
    }

    // ============================================================
    // 封面页
    // ============================================================

    private void drawCoverPage(PdfDocument document, Account account, TransactionStatistics stats) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        android.graphics.Canvas canvas = page.getCanvas();

        int y = MARGIN_TOP;

        // 标题
        TextPaint titlePaint = createPaint(TITLE_FONT_SIZE, Paint.Align.CENTER, true);
        canvas.drawText("太极记账 · 账单", PAGE_WIDTH / 2f, y + 30, titlePaint);
        y += 60;

        // 分割线
        Paint linePaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 30;

        // 账本信息
        TextPaint bodyPaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        canvas.drawText("账本名称：" + safeText(account.getName()), MARGIN_LEFT, y, bodyPaint);
        y += 24;

        canvas.drawText("导出时间：" + dateTimeFormat.format(new Date()), MARGIN_LEFT, y, bodyPaint);
        y += 24;

        canvas.drawText("统计区间：全部流水", MARGIN_LEFT, y, bodyPaint);
        y += 24;

        canvas.drawText("共 " + stats.transactionCount + " 笔流水", MARGIN_LEFT, y, bodyPaint);
        y += 40;

        // 收支汇总标题
        TextPaint subtitlePaint = createPaint(SUBTITLE_FONT_SIZE, Paint.Align.LEFT, true);
        canvas.drawText("收支汇总", MARGIN_LEFT, y, subtitlePaint);
        y += 30;

        // 分割线
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 30;

        // 收入合计
        TextPaint incomePaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        incomePaint.setColor(0xFF2E7D32); // 深绿色
        canvas.drawText("收入合计：¥ " + formatAmount(stats.totalIncome), MARGIN_LEFT, y, incomePaint);
        y += 28;

        // 支出合计
        TextPaint expensePaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        expensePaint.setColor(0xFFC62828); // 深红色
        canvas.drawText("支出合计：¥ " + formatAmount(stats.totalExpense), MARGIN_LEFT, y, expensePaint);
        y += 28;

        // 余额
        TextPaint balancePaint = createPaint(SUBTITLE_FONT_SIZE, Paint.Align.LEFT, true);
        balancePaint.setColor(stats.balance >= 0 ? 0xFF2E7D32 : 0xFFC62828);
        canvas.drawText("余  额  ：¥ " + formatAmount(stats.balance), MARGIN_LEFT, y, balancePaint);
        y += 40;

        // 笔数分布
        canvas.drawText("收入笔数：" + stats.incomeCount, MARGIN_LEFT, y, bodyPaint);
        y += 22;
        canvas.drawText("支出笔数：" + stats.expenseCount, MARGIN_LEFT, y, bodyPaint);
        y += 22;
        canvas.drawText("异常笔数：" + stats.anomalyCount, MARGIN_LEFT, y, bodyPaint);
        y += 60;

        // 底部水印
        TextPaint footerPaint = createPaint(SMALL_FONT_SIZE, Paint.Align.CENTER, false);
        footerPaint.setColor(Color.GRAY);
        canvas.drawText("由太极记账自动生成", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM, footerPaint);

        document.finishPage(page);
    }

    // ============================================================
    // 明细页
    // ============================================================

    private void drawDetailPages(PdfDocument document, Account account, List<TransactionWithCategory> transactions) {
        int totalRows = transactions.size();
        int totalPages = (int) Math.ceil((double) totalRows / ROWS_PER_PAGE);

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int pageNum = pageIndex + 2; // 封面是第 1 页，明细从第 2 页开始
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
            PdfDocument.Page page = document.startPage(pageInfo);

            android.graphics.Canvas canvas = page.getCanvas();
            int y = drawDetailHeader(canvas, account, pageNum, totalPages + 1);

            // 表格列起始 X 坐标
            int xDate = MARGIN_LEFT;
            int xCategory = xDate + COL_DATE_WIDTH;
            int xAmount = xCategory + COL_CATEGORY_WIDTH;
            int xDesc = xAmount + COL_AMOUNT_WIDTH;

            // 表头
            TextPaint headerPaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, true);
            canvas.drawText("日期", xDate + 4, y, headerPaint);
            canvas.drawText("分类", xCategory + 4, y, headerPaint);
            // 金额列：用右对齐 paint，x 设为右边缘
            TextPaint amountHeaderPaint = createPaint(BODY_FONT_SIZE, Paint.Align.RIGHT, true);
            canvas.drawText("金额", xAmount + COL_AMOUNT_WIDTH - 4, y, amountHeaderPaint);
            canvas.drawText("备注", xDesc + 4, y, headerPaint);
            y += 6;

            // 表头分割线
            Paint linePaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
            linePaint.setColor(Color.DKGRAY);
            linePaint.setStrokeWidth(1.5f);
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
            y += 16;

            // 明细行
            int startRow = pageIndex * ROWS_PER_PAGE;
            int endRow = Math.min(startRow + ROWS_PER_PAGE, totalRows);

            for (int i = startRow; i < endRow; i++) {
                TransactionWithCategory tx = transactions.get(i);
                drawDetailRow(canvas, tx, y, xDate, xCategory, xAmount, xDesc);
                y += ROW_HEIGHT;
            }

            // 页脚
            TextPaint footerPaint = createPaint(SMALL_FONT_SIZE, Paint.Align.CENTER, false);
            footerPaint.setColor(Color.GRAY);
            canvas.drawText("第 " + pageNum + " / " + (totalPages + 1) + " 页 · 太极记账",
                    PAGE_WIDTH / 2f, PAGE_HEIGHT - 30, footerPaint);

            document.finishPage(page);
        }
    }

    private int drawDetailHeader(android.graphics.Canvas canvas, Account account, int pageNum, int totalPages) {
        int y = MARGIN_TOP;

        // 标题
        TextPaint titlePaint = createPaint(SUBTITLE_FONT_SIZE, Paint.Align.LEFT, true);
        canvas.drawText("账单明细", MARGIN_LEFT, y, titlePaint);

        // 右上角账本名
        TextPaint subtitlePaint = createPaint(SMALL_FONT_SIZE, Paint.Align.RIGHT, false);
        subtitlePaint.setColor(Color.GRAY);
        canvas.drawText("账本：" + safeText(account.getName()) + " · 第 " + pageNum + " / " + totalPages + " 页",
                PAGE_WIDTH - MARGIN_RIGHT, y, subtitlePaint);
        y += 30;

        return y;
    }

    private void drawDetailRow(android.graphics.Canvas canvas, TransactionWithCategory tx, int y,
                               int xDate, int xCategory, int xAmount, int xDesc) {
        // 日期
        String dateStr = dateFormat.format(new Date(tx.transactionTime));
        TextPaint datePaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        canvas.drawText(dateStr, xDate + 4, y, datePaint);

        // 分类（categoryName 可能为 null）
        String categoryStr = tx.categoryName != null ? tx.categoryName : "未分类";
        // 异常标记
        if (tx.isAnomaly) {
            categoryStr = "⚠ " + categoryStr;
        }
        TextPaint categoryPaint = createPaint(BODY_FONT_SIZE, Paint.Align.LEFT, false);
        if (tx.isAnomaly) {
            categoryPaint.setColor(0xFFFF6F00); // 橙色
        }
        canvas.drawText(categoryStr, xCategory + 4, y, categoryPaint);

        // 金额（右对齐 + 颜色）
        String amountStr = (tx.type.equals("INCOME") ? "+" : "-") + formatAmount(tx.amount);
        TextPaint amountPaint = createRightAlignedPaint(BODY_FONT_SIZE, false);
        amountPaint.setColor(tx.type.equals("INCOME") ? 0xFF2E7D32 : 0xFFC62828);
        canvas.drawText(amountStr, xAmount + COL_AMOUNT_WIDTH - 4, y, amountPaint);

        // 备注（subcategory > description > recipient）
        String descStr = "";
        if (tx.subcategory != null && !tx.subcategory.isEmpty()) {
            descStr = tx.subcategory;
        } else if (tx.description != null && !tx.description.isEmpty()) {
            descStr = tx.description;
        } else if (tx.recipient != null && !tx.recipient.isEmpty()) {
            descStr = tx.recipient;
        }
        TextPaint descPaint = createPaint(SMALL_FONT_SIZE, Paint.Align.LEFT, false);
        descPaint.setColor(Color.DKGRAY);
        canvas.drawText(safeText(descStr), xDesc + 4, y, descPaint);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private TextPaint createPaint(float textSize, Paint.Align align, boolean bold) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(textSize);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return paint;
    }

    private TextPaint createRightAlignedPaint(float textSize, boolean bold) {
        return createPaint(textSize, Paint.Align.RIGHT, bold);
    }

    private String formatAmount(double amount) {
        return String.format(Locale.CHINA, "%.2f", amount);
    }

    private String safeText(String text) {
        if (text == null) return "";
        // 简单的安全处理：替换可能导致渲染问题的字符
        return text.replace("\n", " ").replace("\r", " ");
    }
}
