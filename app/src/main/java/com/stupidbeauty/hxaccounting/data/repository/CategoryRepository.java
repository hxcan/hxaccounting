package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.CategoryDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Category;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分类数据仓库
 */
public class CategoryRepository {

    private final CategoryDao categoryDao;
    private final ExecutorService ioExecutor;

    public CategoryRepository(Context context) {
        TaijiDatabase db = TaijiDatabase.getInstance(context);
        this.categoryDao = db.categoryDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Category>> getAllCategories() {
        return categoryDao.getAllCategories();
    }

    public LiveData<List<Category>> getByType(String type) {
        return categoryDao.getByType(type);
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
}
