package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Category;
import java.util.List;

/**
 * 分类数据访问接口
 * 支持二级分类的父子关系查询
 */
@Dao
public interface CategoryDao {

    // --- 基础 CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Category category);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    // --- 查询所有分类 ---

    @Query("SELECT * FROM categories WHERE is_archived = 0 " +
           "AND (type = :type OR type = 'ALL') ORDER BY sort_order ASC, id ASC")
    LiveData<List<Category>> getByType(String type);

    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY sort_order ASC, id ASC")
    LiveData<List<Category>> getAllCategories();

    // --- 按父分类查询子分类 ---

    @Query("SELECT * FROM categories WHERE parent_id = :parentId " +
           "AND is_archived = 0 ORDER BY sort_order ASC")
    LiveData<List<Category>> getChildren(long parentId);

    // --- 系统预置分类 ---

    @Query("SELECT * FROM categories WHERE is_system = 1 AND is_archived = 0 " +
           "ORDER BY sort_order ASC")
    LiveData<List<Category>> getSystemCategories();

    /**
     * 同步获取系统分类数量（用于 #859882136239 修复：
     * 检测数据库是否为空，决定是否需要插入种子数据）。
     */
    @Query("SELECT COUNT(*) FROM categories WHERE is_system = 1")
    int getSystemCategoriesSync();

    // --- 单个分类 ---

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    LiveData<Category> getCategoryById(long id);

    // --- 批量插入（首次启动时插入预置分类）---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<Category> categories);

    // ====================================================================
    // 以下为全量数据导出/导入功能新增方法（#937493315851）
    // 全部为同步方法，调用方必须在后台线程！
    // ====================================================================

    /**
     * 同步查询全部分类（含已归档，含二级分类）
     * 全量数据导出用，调用方需在后台线程（Room 禁止主线程执行同步查询）
     */
    @Query("SELECT * FROM categories ORDER BY id ASC")
    List<Category> getAllCategoriesSync();

}