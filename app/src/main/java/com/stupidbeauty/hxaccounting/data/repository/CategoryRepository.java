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
     * 修复 #859882136239 + #859893432227：返回结果前自动检查数据库是否为空，
     * 如果为空则主动插入预置种子数据（与 TaijiDatabase.onCreate 一致），
     * 这样无需卸载重装也能让分类显示出来。
     */
    public LiveData<List<Category>> getByType(String type) {
        LiveData<List<Category>> source = categoryDao.getByType(type);
        // 在后台线程检查是否需要种子数据
        ioExecutor.execute(() -> {
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
     * 插入预置种子分类数据。
     * 修复 #859893432227：
     * 之前一次性 insertAll 父+子分类，导致子分类的 parentId=0L 触发外键约束崩溃。
     * 现在分两步：先插父分类（Room 自动生成 id），再插子分类（parentId 用真实 id）。
     */
    private void insertSeedCategories() {
        // ========== 第一步：定义所有父分类 ==========
        Category food = new Category("餐饮", CategoryType.EXPENSE);
        food.setIcon("restaurant");
        food.setColor("#FF6B6B");
        food.setSystem(true);

        Category transport = new Category("交通", CategoryType.EXPENSE);
        transport.setIcon("car");
        transport.setColor("#4ECDC4");
        transport.setSystem(true);

        Category shopping = new Category("购物", CategoryType.EXPENSE);
        shopping.setIcon("shopping");
        shopping.setColor("#FFA07A");
        shopping.setSystem(true);

        Category home = new Category("居家", CategoryType.EXPENSE);
        home.setIcon("home");
        home.setColor("#95E1D3");
        home.setSystem(true);

        Category entertainment = new Category("娱乐", CategoryType.EXPENSE);
        entertainment.setIcon("game");
        entertainment.setColor("#F38181");
        entertainment.setSystem(true);

        Category medical = new Category("医疗", CategoryType.EXPENSE);
        medical.setIcon("medical");
        medical.setColor("#AA96DA");
        medical.setSystem(true);

        Category education = new Category("教育", CategoryType.EXPENSE);
        education.setIcon("education");
        education.setColor("#6C5CE7");
        education.setSystem(true);

        Category parenting = new Category("育儿", CategoryType.EXPENSE);
        parenting.setIcon("baby");
        parenting.setColor("#FDA7DF");
        parenting.setSystem(true);

        Category income = new Category("收入", CategoryType.INCOME);
        income.setIcon("income");
        income.setColor("#26DE81");
        income.setSystem(true);

        Category other = new Category("其他", CategoryType.ALL);
        other.setIcon("other");
        other.setColor("#778CA3");
        other.setSystem(true);

        List<Category> parents = new ArrayList<>();
        parents.add(food);
        parents.add(transport);
        parents.add(shopping);
        parents.add(home);
        parents.add(entertainment);
        parents.add(medical);
        parents.add(education);
        parents.add(parenting);
        parents.add(income);
        parents.add(other);

        // ========== 第二步：插入所有父分类（让 Room 生成真实 id）==========
        for (Category parent : parents) {
            long newId = categoryDao.insert(parent);
            parent.setId(newId);
        }

        // ========== 第三步：定义并插入所有子分类（用真实的 parentId）==========
        List<Category> children = new ArrayList<>();
        children.add(buildChild("早餐", food.getId(), "bread"));
        children.add(buildChild("午餐", food.getId(), "rice"));
        children.add(buildChild("晚餐", food.getId(), "noodles"));
        children.add(buildChild("外卖", food.getId(), "takeout"));
        children.add(buildChild("奶茶", food.getId(), "tea"));

        children.add(buildChild("公交地铁", transport.getId(), "bus"));
        children.add(buildChild("打车", transport.getId(), "taxi"));
        children.add(buildChild("加油", transport.getId(), "fuel"));

        children.add(buildChild("日用", shopping.getId(), "daily"));
        children.add(buildChild("服饰", shopping.getId(), "clothing"));

        children.add(buildChild("房租", home.getId(), "rent"));
        children.add(buildChild("水电煤", home.getId(), "utility"));

        categoryDao.insertAll(children);
    }

    private static Category buildChild(String name, long parentId, String icon) {
        Category child = new Category(name, CategoryType.EXPENSE);
        child.setParentId(parentId);
        child.setIcon(icon);
        child.setSystem(true);
        return child;
    }
}