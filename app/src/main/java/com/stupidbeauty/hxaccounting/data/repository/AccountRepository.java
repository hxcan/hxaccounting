package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.stupidbeauty.hxaccounting.dao.AccountDao;
import com.stupidbeauty.hxaccounting.bean.account;
import java.util.concurrent.Executor;

/**
 * 账本仓库，负责管理当前账本 ID。
 */
public class AccountRepository {
    private static final String PREFS_NAME = "account_prefs";
    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";

    private final AccountDao accountDao;
    private final Executor ioExecutor;
    private final Context appContext;
    private final MutableLiveData<Long> currentAccountIdLive = new MutableLiveData<>();

    public AccountRepository(Context context, AccountDao accountDao, Executor ioExecutor) {
        this.appContext = context.getApplicationContext();
        this.accountDao = accountDao;
        this.ioExecutor = ioExecutor;

        // 构造函数在主线程，可以直接 setValue
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long savedId = prefs.getLong(KEY_CURRENT_ACCOUNT_ID, -1L);
        currentAccountIdLive.setValue(savedId);
    }

    public LiveData<Long> getCurrentAccountIdLive() {
        return currentAccountIdLive;
    }

    /**
     * 设置当前账本 ID（必须在主线程调用）。
     */
    public void setCurrentAccountId(long accountId) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_CURRENT_ACCOUNT_ID, accountId)
                .apply();
        currentAccountIdLive.setValue(accountId);
    }

    /**
     * 创建新账本并设为当前账本。
     * 在后台 IO 线程中执行，必须使用 postValue（线程安全）。
     */
    public void createAndSetCurrent(Account account, @Nullable InsertCallback callback) {
        ioExecutor.execute(() -> {
            long id = accountDao.insert(account);
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_CURRENT_ACCOUNT_ID, id)
                    .apply();
            // 修复 #859841173960：用 postValue 而不是 setValue（后台线程必须用 postValue，线程安全）
            currentAccountIdLive.postValue(id);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    public interface InsertCallback {
        void onInserted(long id);
    }
}