package com.stupidbeauty.hxaccounting.data.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.stupidbeauty.hxaccounting.data.dao.*;
import com.stupidbeauty.hxaccounting.data.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 太极记账 Room 数据库
 * 包含6张核心表 + 对应的DAO接口
 * 提供单例访问 + 首次启动预置分类数据
 *
 * <p>版本历史：
 * <ul>
 *   <li>v1：5 张表（accounts / transactions / categories / budgets / recommendations）</li>
 *   <li>v2：新增 budget_settings 表（v2 周期配置），数据迁移已保留全部老数据</li>
 * </ul>
 */
@Database(
    entities = {
        Account.class,
        Transaction.class,
        Category.class,
        Budget.class,
        Recommendation.class,
        BudgetSettings.class
    },
    version = 2,
    exportSchema = true
)
public abstract class TaijiDatabase extends RoomDatabase {

    private static volatile TaijiDatabase INSTANCE;
    private static final String DATABASE_NAME = "taiji_accounting.db";
    private static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public abstract AccountDao accountDao();
    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract BudgetDao budgetDao();
    public abstract RecommendationDao recommendationDao();
    public abstract BudgetSettingsDao budgetSettingsDao();

    /**
     * v1 → v2 数据迁移：新增 budget_settings 表
     *
     * <p>迁移策略（数据零丢失）：
     * <ol>
     *   <li>新建 budget_settings 表，period_days 默认 30</li>
     *   <li>从老 accounts 表读出所有账本</li>
     *   <li>为每个老账本插入一条默认 BudgetSettings（periodDays=30）</li>
     *   <li>原有 5 张表数据完全不动</li>
     * </ol>
     *
     * <p>注意：账本删除时 budget_settings 会通过外键 CASCADE 自动清理（v2 实体已声明）。
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. 新建 budget_settings 表（不含外键，避免迁移期间 CASCADE 触发意外）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budget_settings` (" +
                "`account_id` INTEGER NOT NULL, " +
                "`period_days` INTEGER NOT NULL DEFAULT 30, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`account_id`))"
            );

            // 2. 为每个老账本创建默认 BudgetSettings（periodDays=30）
            final long now = System.currentTimeMillis();
            db.execSQL(
                "INSERT OR IGNORE INTO budget_settings (account_id, period_days, created_at, updated_at) " +
                "SELECT id, 30, " + now + ", " + now + " FROM accounts"
            );

            // 3. 补充添加外键约束（CASCADE 删除）
            // SQLite 不支持直接给已有表加外键，只能重建表
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budget_settings_new` (" +
                "`account_id` INTEGER NOT NULL, " +
                "`period_days` INTEGER NOT NULL DEFAULT 30, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`account_id`), " +
                "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            );
            db.execSQL(
                "INSERT OR IGNORE INTO budget_settings_new (account_id, period_days, created_at, updated_at) " +
                "SELECT account_id, period_days, created_at, updated_at FROM budget_settings"
            );
            db.execSQL("DROP TABLE IF EXISTS `budget_settings`");
            db.execSQL("ALTER TABLE `budget_settings_new` RENAME TO `budget_settings`");
        }
    };

    /**
     * 获取数据库单例
     */
    public static TaijiDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TaijiDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TaijiDatabase.class,
                            DATABASE_NAME
                        )
                        .addMigrations(MIGRATION_1_2)  // v2：注册迁移
                        .addCallback(new Callback() {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                super.onCreate(db);
                                // 首次创建时插入预置分类数据
                                databaseWriteExecutor.execute(() -> {
                                    populateDefaultCategories(INSTANCE.categoryDao());
                                });
                            }
                        })
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 插入系统预置分类（支出类）
     *
     * <p>⚠️ 修复：全新安装首次建库崩溃（FOREIGN KEY constraint failed）。
     *
     * <p>原实现用 insertAll 一次性插入父 + 子分类，但父分类此时尚未落库，
     * {@code createChild(..., food.getId(), ...)} 拿到的是 0（未落库的自增 ID），
     * 子分类 parent_id=0 指向不存在的父分类，触发外键约束失败。
     *
     * <p>现改为与 {@code CategoryRepository.insertSeedCategories()} 一致的两步插入：
     * <ol>
     *   <li>先逐个插入父分类，用返回的真实 ID 回填 {@code parent.setId()}</li>
     *   <li>再插入子分类，parentId 使用真实 ID</li>
     * </ol>
     */
    private static void populateDefaultCategories(CategoryDao categoryDao) {
        List<Category> parents = new ArrayList<>();
        List<Category> children = new ArrayList<>();

        // 🍜 餐饮
        Category food = new Category("餐饮", CategoryType.EXPENSE);
        food.setIcon("restaurant");
        food.setColor("#FF6B6B");
        food.setSystem(true);
        parents.add(food);

        // 🚗 交通
        Category transport = new Category("交通", CategoryType.EXPENSE);
        transport.setIcon("car");
        transport.setColor("#4ECDC4");
        transport.setSystem(true);
        parents.add(transport);

        // 🛒 购物
        Category shopping = new Category("购物", CategoryType.EXPENSE);
        shopping.setIcon("shopping");
        shopping.setColor("#FFA07A");
        shopping.setSystem(true);
        parents.add(shopping);

        // 🏠 居家
        Category home = new Category("居家", CategoryType.EXPENSE);
        home.setIcon("home");
        home.setColor("#95E1D3");
        home.setSystem(true);
        parents.add(home);

        // 🎮 娱乐
        Category entertainment = new Category("娱乐", CategoryType.EXPENSE);
        entertainment.setIcon("game");
        entertainment.setColor("#F38181");
        entertainment.setSystem(true);
        parents.add(entertainment);

        //  医疗（异常支出）
        Category medical = new Category("医疗", CategoryType.EXPENSE);
        medical.setIcon("medical");
        medical.setColor("#AA96DA");
        medical.setSystem(true);
        parents.add(medical);

        // 📚 教育（异常支出）
        Category education = new Category("教育", CategoryType.EXPENSE);
        education.setIcon("education");
        education.setColor("#6C5CE7");
        education.setSystem(true);
        parents.add(education);

        // 👶 育儿
        Category parenting = new Category("育儿", CategoryType.EXPENSE);
        parenting.setIcon("baby");
        parenting.setColor("#FDA7DF");
        parenting.setSystem(true);
        parents.add(parenting);

        // 💰 收入
        Category income = new Category("收入", CategoryType.INCOME);
        income.setIcon("income");
        income.setColor("#26DE81");
        income.setSystem(true);
        parents.add(income);

        // 📦 其他
        Category other = new Category("其他", CategoryType.ALL);
        other.setIcon("other");
        other.setColor("#778CA3");
        other.setSystem(true);
        parents.add(other);

        // ========== 第一步：逐个插入父分类（让 Room 生成真实 ID）==========
        for (Category parent : parents) {
            long newId = categoryDao.insert(parent);
            parent.setId(newId);
        }

        // ========== 第二步：插入子分类（parentId 用真实 ID）==========
        children.add(createChild("早餐", food.getId(), "bread"));
        children.add(createChild("午餐", food.getId(), "rice"));
        children.add(createChild("晚餐", food.getId(), "noodles"));
        children.add(createChild("外卖", food.getId(), "takeout"));
        children.add(createChild("奶茶", food.getId(), "tea"));

        children.add(createChild("公交地铁", transport.getId(), "bus"));
        children.add(createChild("打车", transport.getId(), "taxi"));
        children.add(createChild("加油", transport.getId(), "fuel"));

        children.add(createChild("日用", shopping.getId(), "daily"));
        children.add(createChild("服饰", shopping.getId(), "clothing"));

        children.add(createChild("房租", home.getId(), "rent"));
        children.add(createChild("水电煤", home.getId(), "utility"));

        categoryDao.insertAll(children);
    }

    private static Category createChild(String name, long parentId, String icon) {
        Category child = new Category(name, CategoryType.EXPENSE);
        child.setParentId(parentId);
        child.setIcon(icon);
        child.setSystem(true);
        return child;
    }
}
