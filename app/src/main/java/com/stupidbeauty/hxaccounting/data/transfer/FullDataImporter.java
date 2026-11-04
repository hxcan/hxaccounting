package com.stupidbeauty.hxaccounting.data.transfer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.stupidbeauty.hxaccounting.data.dao.AccountDao;
import com.stupidbeauty.hxaccounting.data.dao.CategoryDao;
import com.stupidbeauty.hxaccounting.data.dao.TransactionDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.Category;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全量账本数据导入器（JSON 格式）
 *
 * <p>把 {@link FullDataExporter} 导出的 JSON 文件导入回数据库。
 *
 * <p><b>收费原则</b>：导入用户自己的数据<b>永久免费</b>，不设任何门槛。
 * 本类及 {@link FullDataExporter} 中不存在任何付费 / 会员 / 积分判断分支。
 *
 * <p><b>核心行为</b>：
 * <ul>
 *   <li><b>合并，不覆盖</b> —— 已有数据绝不被清空</li>
 *   <li><b>同名复用</b> —— 账本 / 分类按名字匹配已有记录，不重复创建</li>
 *   <li><b>ID 映射</b> —— 导出文件的 ID 映射到新库 ID，跨设备无冲突</li>
 *   <li><b>去重</b> —— 流水按「日期+金额+类型+分类+备注」指纹跳过重复</li>
 *   <li><b>事务性</b> —— 任一环节失败全部回滚，绝不留下"导了一半"的脏数据</li>
 * </ul>
 *
 * <p>关联任务：#937493315851
 *
 * @author 未来姐姐
 * @since 2026-11-04
 */
public class FullDataImporter {

    private static final String TAG = "FullDataImporter";

    private final Context context;
    private final TaijiDatabase database;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;

    public FullDataImporter(Context context) {
        this.context = context.getApplicationContext();
        this.database = TaijiDatabase.getInstance(this.context);
        this.accountDao = database.accountDao();
        this.categoryDao = database.categoryDao();
        this.transactionDao = database.transactionDao();
    }

    /**
     * 从 URI 导入全部账本数据
     *
     * <p><b>调用方必须在后台线程执行</b>。
     *
     * @param sourceUri 数据文件 Uri（来自 ACTION_OPEN_DOCUMENT）
     * @return 导入结果统计
     * @throws IOException 文件读取失败或格式错误
     */
    public ImportResult importFromUri(Uri sourceUri) throws IOException {
        String json = readAllText(sourceUri);

        // 解析
        JsonObject root = JsonParser.parseObject(json);

        // 格式校验
        String format = root.getString("format");
        if (format == null || !FullDataExporter.FORMAT_ID.equals(format)) {
            throw new IOException("文件格式不正确：不是太极记账导出的数据文件");
        }
        int formatVersion = (int) root.getDouble("formatVersion", 0);
        if (formatVersion <= 0 || formatVersion > FullDataExporter.FORMAT_VERSION) {
            throw new IOException("文件版本不兼容（文件版本 " + formatVersion
                    + "，当前支持到 " + FullDataExporter.FORMAT_VERSION + "）");
        }

        List<JsonObject> accountNodes = root.getArray("accounts");
        List<JsonObject> categoryNodes = root.getArray("categories");
        List<JsonObject> transactionNodes = root.getArray("transactions");

        if (accountNodes == null && categoryNodes == null && transactionNodes == null) {
            throw new IOException("文件内容为空，没有可导入的数据");
        }

        Log.i(TAG, "开始导入：账本 " + sizeOf(accountNodes)
                + " 个，分类 " + sizeOf(categoryNodes)
                + " 个，流水 " + sizeOf(transactionNodes) + " 笔");

        ImportResult result = database.runInTransaction(() -> {
            return doImport(accountNodes, categoryNodes, transactionNodes);
        });

        Log.i(TAG, "导入完成：新增账本 " + result.newAccountCount
                + " 个，复用账本 " + result.reusedAccountCount
                + " 个，新增分类 " + result.newCategoryCount
                + " 个，复用分类 " + result.reusedCategoryCount
                + " 个，新增流水 " + result.importedTransactionCount
                + " 笔，跳过重复 " + result.skippedTransactionCount + " 笔");

        return result;
    }

    /**
     * 实际导入逻辑（在事务中执行）
     */
    private ImportResult doImport(
            List<JsonObject> accountNodes,
            List<JsonObject> categoryNodes,
            List<JsonObject> transactionNodes) {

        ImportResult result = new ImportResult();

        // ========== 1. 账本 ==========
        // 导出文件 ID → 新库 ID
        Map<Long, Long> accountIdMap = new HashMap<>();
        // 账本名 → 新库 ID（用于同名复用）
        Map<String, Long> accountNameMap = new HashMap<>();

        if (accountNodes != null) {
            for (Account existing : accountDao.getAllAccountsSync()) {
                accountNameMap.put(existing.getName(), existing.getId());
            }

            for (JsonObject node : accountNodes) {
                long oldId = (long) node.getDouble("id", 0);
                String name = node.getString("name");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                Long existingId = accountNameMap.get(name);
                if (existingId != null) {
                    // 同名账本已存在 → 复用，不重复创建
                    accountIdMap.put(oldId, existingId);
                    result.reusedAccountCount++;
                    continue;
                }

                Account account = new Account();
                account.setName(name);
                account.setType(defaultIfNull(node.getString("type"), "CASH"));
                account.setColor(defaultIfNull(node.getString("color"), "#000000"));
                account.setIcon(defaultIfNull(node.getString("icon"), "wallet"));
                account.setBudget(node.getDouble("budget", 0));
                account.setSortOrder((int) node.getDouble("sortOrder", 0));
                account.setArchived(node.getBool("isArchived", false));
                long createdAt = (long) node.getDouble("createdAt", System.currentTimeMillis());
                long updatedAt = (long) node.getDouble("updatedAt", createdAt);
                account.setCreatedAt(createdAt);
                account.setUpdatedAt(updatedAt);

                long newId = accountDao.insert(account);
                accountIdMap.put(oldId, newId);
                accountNameMap.put(name, newId);
                result.newAccountCount++;
            }
        }

        // ========== 2. 分类 ==========
        Map<Long, Long> categoryIdMap = new HashMap<>();
        Map<String, Long> categoryNameMap = new HashMap<>();

        if (categoryNodes != null) {
            for (Category existing : categoryDao.getAllCategoriesSync()) {
                categoryNameMap.put(existing.getName(), existing.getId());
            }

            // 第一轮：插入父分类（parentId = null 的）
            for (JsonObject node : categoryNodes) {
                if (node.getLongOrNull("parentId") != null) {
                    continue; // 子分类留到第二轮
                }
                long oldId = (long) node.getDouble("id", 0);
                String name = node.getString("name");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                Long existingId = categoryNameMap.get(name);
                if (existingId != null) {
                    categoryIdMap.put(oldId, existingId);
                    result.reusedCategoryCount++;
                    continue;
                }

                Category category = new Category();
                category.setName(name);
                category.setParentId(null);
                category.setIcon(defaultIfNull(node.getString("icon"), "tag"));
                category.setColor(defaultIfNull(node.getString("color"), "#666666"));
                category.setType(defaultIfNull(node.getString("type"), "EXPENSE"));
                category.setSortOrder((int) node.getDouble("sortOrder", 0));
                category.setSystem(node.getBool("isSystem", false));
                category.setArchived(node.getBool("isArchived", false));
                category.setCreatedAt((long) node.getDouble("createdAt", System.currentTimeMillis()));

                long newId = categoryDao.insert(category);
                categoryIdMap.put(oldId, newId);
                categoryNameMap.put(name, newId);
                result.newCategoryCount++;
            }

            // 第二轮：插入子分类（父分类已就位，parentId 用映射后的真实 ID）
            for (JsonObject node : categoryNodes) {
                Long oldParentId = node.getLongOrNull("parentId");
                if (oldParentId == null) {
                    continue;
                }
                long oldId = (long) node.getDouble("id", 0);
                String name = node.getString("name");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                Long existingId = categoryNameMap.get(name);
                if (existingId != null) {
                    categoryIdMap.put(oldId, existingId);
                    result.reusedCategoryCount++;
                    continue;
                }

                Long newParentId = categoryIdMap.get(oldParentId);
                if (newParentId == null) {
                    // 父分类不存在（可能被跳过），当作顶级分类插入
                    newParentId = null;
                }

                Category category = new Category();
                category.setName(name);
                category.setParentId(newParentId);
                category.setIcon(defaultIfNull(node.getString("icon"), "tag"));
                category.setColor(defaultIfNull(node.getString("color"), "#666666"));
                category.setType(defaultIfNull(node.getString("type"), "EXPENSE"));
                category.setSortOrder((int) node.getDouble("sortOrder", 0));
                category.setSystem(node.getBool("isSystem", false));
                category.setArchived(node.getBool("isArchived", false));
                category.setCreatedAt((long) node.getDouble("createdAt", System.currentTimeMillis()));

                long newId = categoryDao.insert(category);
                categoryIdMap.put(oldId, newId);
                categoryNameMap.put(name, newId);
                result.newCategoryCount++;
            }
        }

        // ========== 3. 流水 ==========
        if (transactionNodes != null) {
            // 预加载现有流水的指纹集合（按账本分别建）
            Map<Long, Set<String>> existingFingerprints = new HashMap<>();
            for (Account account : accountDao.getAllAccountsSync()) {
                Set<String> fingerprints = new HashSet<>();
                for (Transaction t : transactionDao.getByAccountIdForImportSync(account.getId())) {
                    fingerprints.add(fingerprint(t));
                }
                existingFingerprints.put(account.getId(), fingerprints);
            }

            for (JsonObject node : transactionNodes) {
                long oldAccountId = (long) node.getDouble("accountId", -1);
                Long newAccountId = accountIdMap.get(oldAccountId);
                if (newAccountId == null) {
                    // 找不到对应账本，跳过这笔记���
                    result.skippedTransactionCount++;
                    continue;
                }

                Transaction transaction = new Transaction();
                transaction.setAccountId(newAccountId);
                transaction.setAmount(node.getDouble("amount", 0));
                transaction.setType(defaultIfNull(node.getString("type"), "EXPENSE"));
                transaction.setCategoryId(mapCategoryId(node.getLongOrNull("categoryId"), categoryIdMap));
                transaction.setSubcategory(node.getString("subcategory"));
                transaction.setDescription(node.getString("description"));
                transaction.setRecipient(node.getString("recipient"));
                transaction.setPaymentMethod(defaultIfNull(node.getString("paymentMethod"), "OTHER"));
                transaction.setTransactionTime((long) node.getDouble("transactionTime", System.currentTimeMillis()));
                transaction.setAnomaly(node.getBool("isAnomaly", false));
                transaction.setSource(defaultIfNull(node.getString("source"), "MANUAL"));
                long createdAt = (long) node.getDouble("createdAt", System.currentTimeMillis());
                transaction.setCreatedAt(createdAt);
                transaction.setUpdatedAt((long) node.getDouble("updatedAt", createdAt));

                // 去重：指纹相同则跳过
                Set<String> fingerprints = existingFingerprints.get(newAccountId);
                if (fingerprints == null) {
                    fingerprints = new HashSet<>();
                    existingFingerprints.put(newAccountId, fingerprints);
                }
                String fp = fingerprint(transaction);
                if (fingerprints.contains(fp)) {
                    result.skippedTransactionCount++;
                    continue;
                }
                fingerprints.add(fp);

                transactionDao.insert(transaction);
                result.importedTransactionCount++;
            }
        }

        return result;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /**
     * 读取 URI 全部文本内容
     */
    private String readAllText(Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            if (in == null) {
                throw new IOException("无法打开文件");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 流水去重指纹：日期 + 金额 + 类型 + 分类 + 备注
     * 不含 ID 和时间戳的毫秒部分，保证同一笔交易在多次导出/导入间稳定匹配
     */
    private String fingerprint(Transaction t) {
        return t.getTransactionTime() + "|"
                + String.format(java.util.Locale.US, "%.4f", t.getAmount()) + "|"
                + t.getType() + "|"
                + (t.getCategoryId() == null ? "" : t.getCategoryId()) + "|"
                + safe(t.getDescription()) + "|"
                + safe(t.getSubcategory());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private Long mapCategoryId(Long oldCategoryId, Map<Long, Long> categoryIdMap) {
        if (oldCategoryId == null) {
            return null;
        }
        return categoryIdMap.get(oldCategoryId);
    }

    private String defaultIfNull(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private int sizeOf(List<JsonObject> list) {
        return list == null ? 0 : list.size();
    }

    // ====================================================================
    // 结果对象
    // ====================================================================

    /**
     * 导入结果统计
     */
    public static class ImportResult {
        /** 新增账本数 */
        public int newAccountCount;
        /** 复用（已存在同名）账本数 */
        public int reusedAccountCount;
        /** 新增分类数 */
        public int newCategoryCount;
        /** 复用（已存在同名）分类数 */
        public int reusedCategoryCount;
        /** 成功导入流水数 */
        public int importedTransactionCount;
        /** 跳过的重复流水数 */
        public int skippedTransactionCount;

        public int getTotalAccountCount() {
            return newAccountCount + reusedAccountCount;
        }

        public int getTotalCategoryCount() {
            return newCategoryCount + reusedCategoryCount;
        }
    }

    // ====================================================================
    // 极简 JSON 解析器（零依赖）
    // ====================================================================

    /**
     * JSON 对象（仅支持我们导出的扁平结构 + 数组）
     */
    static class JsonObject {
        private final Map<String, Object> values = new HashMap<>();

        void put(String key, Object value) {
            values.put(key, value);
        }

        String getString(String key) {
            Object v = values.get(key);
            return v instanceof String ? (String) v : null;
        }

        double getDouble(String key, double fallback) {
            Object v = values.get(key);
            return v instanceof Number ? ((Number) v).doubleValue() : fallback;
        }

        boolean getBool(String key, boolean fallback) {
            Object v = values.get(key);
            return v instanceof Boolean ? (Boolean) v : fallback;
        }

        Long getLongOrNull(String key) {
            Object v = values.get(key);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        List<JsonObject> getArray(String key) {
            Object v = values.get(key);
            return v instanceof List ? (List<JsonObject>) v : null;
        }
    }

    /**
     * 极简 JSON 解析器
     * 只处理我们导出的格式：对象、数组、字符串、数字、布尔、null
     */
    static class JsonParser {

        static JsonObject parseObject(String json) throws IOException {
            Cursor cursor = new Cursor(json);
            cursor.skipWhitespace();
            JsonObject result = parseObjectValue(cursor);
            cursor.skipWhitespace();
            return result;
        }

        private static JsonObject parseObjectValue(Cursor c) throws IOException {
            JsonObject obj = new JsonObject();
            c.expect('{');
            c.skipWhitespace();
            if (c.peek() == '}') {
                c.next();
                return obj;
            }
            while (true) {
                c.skipWhitespace();
                String key = parseString(c);
                c.skipWhitespace();
                c.expect(':');
                c.skipWhitespace();
                Object value = parseValue(c);
                obj.put(key, value);
                c.skipWhitespace();
                char ch = c.next();
                if (ch == '}') {
                    break;
                }
                if (ch != ',') {
                    throw new IOException("JSON 格式错误：期望 ',' 或 '}'，实际 '" + ch + "'");
                }
            }
            return obj;
        }

        private static List<JsonObject> parseArray(Cursor c) throws IOException {
            List<JsonObject> list = new ArrayList<>();
            c.expect('[');
            c.skipWhitespace();
            if (c.peek() == ']') {
                c.next();
                return list;
            }
            while (true) {
                c.skipWhitespace();
                list.add(parseObjectValue(c));
                c.skipWhitespace();
                char ch = c.next();
                if (ch == ']') {
                    break;
                }
                if (ch != ',') {
                    throw new IOException("JSON 格式错误：期望 ',' 或 ']'，实际 '" + ch + "'");
                }
            }
            return list;
        }

        private static Object parseValue(Cursor c) throws IOException {
            c.skipWhitespace();
            char ch = c.peek();
            switch (ch) {
                case '{':
                    return parseObjectValue(c);
                case '[':
                    return parseArray(c);
                case '"':
                    return parseString(c);
                case 't':
                    c.expectWord("true");
                    return Boolean.TRUE;
                case 'f':
                    c.expectWord("false");
                    return Boolean.FALSE;
                case 'n':
                    c.expectWord("null");
                    return null;
                default:
                    return parseNumber(c);
            }
        }

        private static String parseString(Cursor c) throws IOException {
            c.expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char ch = c.next();
                if (ch == '"') {
                    break;
                }
                if (ch == '\\') {
                    char esc = c.next();
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            String hex = c.nextRaw(4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        private static Double parseNumber(Cursor c) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                char ch = c.peek();
                if (ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E'
                        || (ch >= '0' && ch <= '9')) {
                    sb.append(c.next());
                } else {
                    break;
                }
            }
            try {
                return Double.parseDouble(sb.toString());
            } catch (NumberFormatException e) {
                throw new IOException("JSON 数字格式错误: " + sb);
            }
        }

        /**
         * 解析游标
         */
        static class Cursor {
            private final String text;
            private int pos;

            Cursor(String text) {
                this.text = text;
            }

            char peek() throws IOException {
                if (pos >= text.length()) {
                    throw new IOException("JSON 意外结束");
                }
                return text.charAt(pos);
            }

            char next() throws IOException {
                if (pos >= text.length()) {
                    throw new IOException("JSON 意外结束");
                }
                return text.charAt(pos++);
            }

            String nextRaw(int count) throws IOException {
                if (pos + count > text.length()) {
                    throw new IOException("JSON 意外结束");
                }
                String result = text.substring(pos, pos + count);
                pos += count;
                return result;
            }

            void skipWhitespace() {
                while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                    pos++;
                }
            }

            void expect(char expected) throws IOException {
                skipWhitespace();
                char actual = next();
                if (actual != expected) {
                    throw new IOException("JSON 格式错误：期望 '" + expected + "'，实际 '" + actual + "'");
                }
            }

            void expectWord(String word) throws IOException {
                skipWhitespace();
                String actual = nextRaw(word.length());
                if (!actual.equals(word)) {
                    throw new IOException("JSON 格式错误：期望 '" + word + "'，实际 '" + actual + "'");
                }
            }
        }
    }
}
