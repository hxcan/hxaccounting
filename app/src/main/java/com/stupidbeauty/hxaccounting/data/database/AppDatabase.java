package com.stupidbeauty.hxaccounting.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.stupidbeauty.hxaccounting.data.dao.AccountDao;
import com.stupidbeauty.hxaccounting.data.entity.Account;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用数据库（Room）
 *
 * 单例模式，通过 getInstance() 获取数据库实例
 *
 * 当前版本：v1
 * - 注册 Account 实体
 * - 暴露 AccountDao
 */
@Database(
    entities = {Account.class},
    version = 1,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "hxaccounting.db";

    // ============ DAO 访问器 ============

    /**
     * 账本 DAO
     */
    public abstract AccountDao accountDao();

    // ============ 单例模式 ============

    private static volatile AppDatabase INSTANCE;

    /**
     * 后台线程池（用于数据库写入操作）
     */
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * 获取数据库单例
     *
     * @param context Application Context
     * @return AppDatabase 实例
     */
    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 构建数据库实例（仅在初始化时调用）
     */
    private static AppDatabase buildDatabase(@NonNull Context context) {
        return Room.databaseBuilder(
            context.getApplicationContext(),
            AppDatabase.class,
            DATABASE_NAME
        )
        // 允许主线程查询（开发阶段方便，生产环境建议关闭）
        // .allowMainThreadQueries()

        // 添加数据库迁移（v1 → 未来版本）
        // .addMigrations(MIGRATION_1_2)

        // 兜底：迁移失败时重建数据库（仅开发阶段）
        .fallbackToDestructiveMigration()

        .build();
    }

    /**
     * 销毁单例（仅用于测试）
     */
    public static void destroyInstance() {
        INSTANCE = null;
    }
}