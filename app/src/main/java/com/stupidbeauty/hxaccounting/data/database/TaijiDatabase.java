package com.stupidbeauty.hxaccounting.data.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.stupidbeauty.hxaccounting.data.dao.*;
import com.stupidbeauty.hxaccounting.data.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 太极记账 Room 数据库
 * 包含5张核心表 + 对应的DAO接口
 * 提供单例访问 + 首次启动预置分类数据
 */
@Database(
    entities = {
        Account.class,
        Transaction.class,
        Category.class,
        Budget.class,
        Recommendation.class
    },
    version = 1,
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
