package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.CategoryDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Category;
import com.stupidbeauty.hxaccounting.data.entity.CategoryType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分类数据仓库
 */
public class CategoryRepository {

    private final CategoryDao categoryDao;
    private final ExecutorService ioExecutor;
    private final Context appContext;

    public CategoryRepository(Context context) {
        this.appContext = context.getApplicationContext();
        TaijiDatabase db = TaijiDatabase.getInstance(context);
        this.categoryDao = db.categoryDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Category>> getAllCategories() {
        return categoryDao.getAllCategories();
    }

    /**
     * 获取指定类型的分类。
     * 修复 #859882136239：返回结果前自动检查数据库是否为空，
     * 如果为空则主动插入预置种子数据（与 TaijiDatabase.onCreate 一致），
     * 这样无需卸载重装也能让分类显示出来。
     */
    public LiveData<List<Category>> getByType(String type) {
        LiveData<List<Category>> source = categoryDao.getByType(type);
        // 在后台线程检查是否需要种子数据
        ioExecutor.execute(() -> {
            // 通过查所有分类来检查数据库是否为空
            // 这里我们用 getChildren(0) 不可行（0 不是有效 id）
            // 所以用一个同步查询：直接查 is_system=1 的数量
            int count = categoryDao.getSystemCategoriesSync();
            if (count == 0) {
                // 数据库为空，插入种子数据
                insertSeedCategories();
            }
        });
        return source;
    }

    public LiveData<List<Category>> getChildren(long parentId) {
        return categoryDao.getChildren(parentId);
    }

    public LiveData<Category> getCategoryById(long id) {
        return categoryDao.getCategoryById(id);
    }

    public void insert(Category category) {
        ioExecutor.execute(() -> categoryDao.insert(category));
    }

    public void update(Category category) {
        ioExecutor.execute(() -> categoryDao.update(category));
    }

    public void delete(Category category) {
        ioExecutor.execute(() -> categoryDao.delete(category));
    }

    /**
     * 插入预置种子分类数据（与 TaijiDatabase.populateDefaultCategories 逻辑一致）。
     * 修复 #859882136239：当用户升级到包含此修复的版本时，
     * 如果之前数据库是空的，会自动填充预置分类。
     */
    private void insertSeedCategories() {
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