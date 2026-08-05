package com.stupidbeauty.hxaccounting.data.repository;
import android.content.Context;
import androidx.lifecycle.LiveData;
import com.stupidbeauty.hxaccounting.data.dao.AccountDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 账本数据仓库
 * 封装 AccountDao 的业务逻辑
 */
public class AccountRepository {
    private final AccountDao accountDao;
    private final ExecutorService ioExecutor;

    public AccountRepository(Context context) {
        TaijiDatabase db = TaijiDatabase.getInstance(context);
        this.accountDao = db.accountDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    // --- 查询（LiveData，自动响应UI）---
    public LiveData<List<Account>> getActiveAccounts() {
        return accountDao.getActiveAccounts();
    }

    public LiveData<List<Account>> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    public LiveData<Account> getAccountById(long id) {
        return accountDao.getAccountById(id);
    }

    // --- 写入（异步执行）---
    public void insert(Account account, InsertCallback callback) {
        ioExecutor.execute(() -> {
            long id = accountDao.insert(account);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    public void update(Account account) {
        ioExecutor.execute(() -> accountDao.update(account));
    }

    public void archive(long id) {
        ioExecutor.execute(() -> accountDao.archive(id, true, System.currentTimeMillis()));
    }

    public void delete(Account account) {
        ioExecutor.execute(() -> accountDao.delete(account));
    }

    public interface InsertCallback {
        void onInserted(long id);
    }
}