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
     */
    private static void populateDefaultCategories(CategoryDao categoryDao) {
        List<Category> categories = new ArrayList<>();

        // 🍜 餐饮
        Category food = new Category("餐饮", CategoryType.EXPENSE);
        food.setIcon("restaurant");
        food.setColor("#FF6B6B");
        food.setSystem(true);
        categories.add(food);

        categories.add(createChild("早餐", food.getId(), "bread"));
        categories.add(createChild("午餐", food.getId(), "rice"));
        categories.add(createChild("晚餐", food.getId(), "noodles"));
        categories.add(createChild("外卖", food.getId(), "takeout"));
        categories.add(createChild("奶茶", food.getId(), "tea"));

        // 🚗 交通
        Category transport = new Category("交通", CategoryType.EXPENSE);
        transport.setIcon("car");
        transport.setColor("#4ECDC4");
        transport.setSystem(true);
        categories.add(transport);

        categories.add(createChild("公交地铁", transport.getId(), "bus"));
        categories.add(createChild("打车", transport.getId(), "taxi"));
        categories.add(createChild("加油", transport.getId(), "fuel"));

        // 🛒 购物
        Category shopping = new Category("购物", CategoryType.EXPENSE);
        shopping.setIcon("shopping");
        shopping.setColor("#FFA07A");
        shopping.setSystem(true);
        categories.add(shopping);

        categories.add(createChild("日用", shopping.getId(), "daily"));
        categories.add(createChild("服饰", shopping.getId(), "clothing"));

        // 🏠 居家
        Category home = new Category("居家", CategoryType.EXPENSE);
        home.setIcon("home");
        home.setColor("#95E1D3");
        home.setSystem(true);
        categories.add(home);

        categories.add(createChild("房租", home.getId(), "rent"));
        categories.add(createChild("水电煤", home.getId(), "utility"));

        // 🎮 娱乐
        Category entertainment = new Category("娱乐", CategoryType.EXPENSE);
        entertainment.setIcon("game");
        entertainment.setColor("#F38181");
        entertainment.setSystem(true);
        categories.add(entertainment);

        //  医疗（异常支出）
        Category medical = new Category("医疗", CategoryType.EXPENSE);
        medical.setIcon("medical");
        medical.setColor("#AA96DA");
        medical.setSystem(true);
        categories.add(medical);

        // 📚 教育（异常支出）
        Category education = new Category("教育", CategoryType.EXPENSE);
        education.setIcon("education");
        education.setColor("#6C5CE7");
        education.setSystem(true);
        categories.add(education);

        // 👶 育儿
        Category parenting = new Category("育儿", CategoryType.EXPENSE);
        parenting.setIcon("baby");
        parenting.setColor("#FDA7DF");
        parenting.setSystem(true);
        categories.add(parenting);

        // 💰 收入
        Category income = new Category("收入", CategoryType.INCOME);
        income.setIcon("income");
        income.setColor("#26DE81");
        income.setSystem(true);
        categories.add(income);

        // 📦 其他
        Category other = new Category("其他", CategoryType.ALL);
        other.setIcon("other");
        other.setColor("#778CA3");
        other.setSystem(true);
        categories.add(other);

        categoryDao.insertAll(categories);
    }

    private static Category createChild(String name, long parentId, String icon) {
        Category child = new Category(name, CategoryType.EXPENSE);
        child.setParentId(parentId);
        child.setIcon(icon);
        child.setSystem(true);
        return child;
    }
}
