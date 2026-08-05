package com.stupidbeauty.hxaccounting.data.dao;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.stupidbeauty.hxaccounting.data.database.AppDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.AccountType;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * AccountDao 单元测试（Android Instrumentation）
 *
 * 使用内存数据库测试，确保 DAO 的所有方法都工作正常。
 *
 * 命名约定：匹配 Repository 已使用的方法名
 * - getActiveAccounts / getAllAccounts / getAccountById / archive
 */
@RunWith(AndroidJUnit4.class)
public class AccountDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private AccountDao accountDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
            .allowMainThreadQueries()
            .build();
        accountDao = database.accountDao();
    }

    @After
    public void closeDb() {
        database.close();
    }

    // ============ INSERT 测试 ============

    @Test
    public void insertAndRetrieve() throws Exception {
        Account account = new Account("教育基金", AccountType.SAVINGS);
        long id = accountDao.insert(account);

        assertTrue("Generated ID should be > 0", id > 0);

        Account retrieved = accountDao.findByName("教育基金");
        assertNotNull(retrieved);
        assertEquals("教育基金", retrieved.getName());
        assertEquals(AccountType.SAVINGS, retrieved.getAccountType());
    }

    @Test
    public void insertAll() throws Exception {
        accountDao.insertAll(List.of(
            new Account("教育基金", AccountType.SAVINGS),
            new Account("日常开销", AccountType.CASH),
            new Account("信用卡", AccountType.CREDIT)
        ));

        assertEquals(3, accountDao.countAll());
    }

    // ============ UPDATE 测试 ============

    @Test
    public void updateName() throws Exception {
        Account account = new Account("原名", AccountType.CASH);
        long id = accountDao.insert(account);

        long now = System.currentTimeMillis();
        accountDao.updateName(id, "新名", now);

        Account updated = accountDao.findByName("新名");
        assertNotNull(updated);
        assertEquals("新名", updated.getName());
        assertEquals(now, updated.getUpdatedAt());
    }

    @Test
    public void updateBudget() throws Exception {
        Account account = new Account("测试", AccountType.CASH);
        long id = accountDao.insert(account);

        accountDao.updateBudget(id, 5000.0, System.currentTimeMillis());

        Account updated = accountDao.findByName("测试");
        assertNotNull(updated);
        assertEquals(5000.0, updated.getBudget(), 0.001);
    }

    @Test
    public void archiveAndUnarchive() throws Exception {
        Account account = new Account("测试", AccountType.CASH);
        long id = accountDao.insert(account);

        // 归档
        accountDao.archive(id, true, System.currentTimeMillis());
        List<Account> archived = getValue(accountDao.findArchived());
        assertEquals(1, archived.size());

        // 取消归档
        accountDao.archive(id, false, System.currentTimeMillis());
        List<Account> active = getValue(accountDao.getActiveAccounts());
        assertEquals(1, active.size());
    }

    // ============ DELETE 测试 ============

    @Test
    public void deleteById() throws Exception {
        Account account = new Account("待删除", AccountType.CASH);
        long id = accountDao.insert(account);

        accountDao.deleteById(id);

        assertEquals(0, accountDao.countAll());
    }

    @Test
    public void deleteAllArchived() throws Exception {
        Account a1 = new Account("活跃1", AccountType.CASH);
        Account a2 = new Account("活跃2", AccountType.SAVINGS);
        Account a3 = new Account("归档", AccountType.CREDIT);

        long id1 = accountDao.insert(a1);
        long id2 = accountDao.insert(a2);
        long id3 = accountDao.insert(a3);

        accountDao.archive(id3, true, System.currentTimeMillis());
        accountDao.deleteAllArchived();

        assertEquals(2, accountDao.countAll());
    }

    // ============ QUERY 测试 ============

    @Test
    public void findByName() throws Exception {
        accountDao.insert(new Account("教育基金", AccountType.SAVINGS));
        accountDao.insert(new Account("日常开销", AccountType.CASH));

        Account found = accountDao.findByName("教育基金");
        assertNotNull(found);
        assertEquals(AccountType.SAVINGS, found.getAccountType());
    }

    @Test
    public void findFirstByType() throws Exception {
        accountDao.insert(new Account("教育基金", AccountType.SAVINGS));
        accountDao.insert(new Account("储蓄罐", AccountType.SAVINGS));
        accountDao.insert(new Account("现金", AccountType.CASH));

        Account first = accountDao.findFirstByType(AccountType.SAVINGS.name());
        assertNotNull(first);
        assertEquals("教育基金", first.getName());
    }

    @Test
    public void getActiveAccounts() throws Exception {
        accountDao.insert(new Account("活跃1", AccountType.CASH));
        accountDao.insert(new Account("活跃2", AccountType.SAVINGS));

        Account archived = new Account("归档", AccountType.CREDIT);
        long archivedId = accountDao.insert(archived);
        accountDao.archive(archivedId, true, System.currentTimeMillis());

        List<Account> all = getValue(accountDao.getActiveAccounts());
        assertEquals(2, all.size());
    }

    @Test
    public void getAllAccounts() throws Exception {
        accountDao.insert(new Account("活跃1", AccountType.CASH));
        Account archived = new Account("归档", AccountType.CREDIT);
        long archivedId = accountDao.insert(archived);
        accountDao.archive(archivedId, true, System.currentTimeMillis());

        List<Account> all = getValue(accountDao.getAllAccounts());
        assertEquals(2, all.size());
    }

    @Test
    public void findByAccountType() throws Exception {
        accountDao.insert(new Account("现金1", AccountType.CASH));
        accountDao.insert(new Account("现金2", AccountType.CASH));
        accountDao.insert(new Account("教育", AccountType.SAVINGS));

        List<Account> cashAccounts = getValue(accountDao.findByAccountType(AccountType.CASH));
        assertEquals(2, cashAccounts.size());
    }

    @Test
    public void searchByName() throws Exception {
        accountDao.insert(new Account("教育基金", AccountType.SAVINGS));
        accountDao.insert(new Account("教育储蓄", AccountType.SAVINGS));
        accountDao.insert(new Account("现金", AccountType.CASH));

        List<Account> results = getValue(accountDao.searchByName("教育"));
        assertEquals(2, results.size());
    }

    // ============ 统计测试 ============

    @Test
    public void countActiveAndAll() throws Exception {
        accountDao.insert(new Account("活跃1", AccountType.CASH));
        accountDao.insert(new Account("活跃2", AccountType.SAVINGS));

        Account archived = new Account("归档", AccountType.CREDIT);
        long id = accountDao.insert(archived);
        accountDao.archive(id, true, System.currentTimeMillis());

        assertEquals(2, accountDao.countActive());
        assertEquals(3, accountDao.countAll());
    }

    @Test
    public void countByType() throws Exception {
        accountDao.insert(new Account("现金1", AccountType.CASH));
        accountDao.insert(new Account("现金2", AccountType.CASH));
        accountDao.insert(new Account("教育", AccountType.SAVINGS));

        assertEquals(2, accountDao.countByType(AccountType.CASH.name()));
        assertEquals(1, accountDao.countByType(AccountType.SAVINGS.name()));
    }

    @Test
    public void existsByName() throws Exception {
        accountDao.insert(new Account("教育基金", AccountType.SAVINGS));

        assertTrue(accountDao.existsByName("教育基金"));
        assertFalse(accountDao.existsByName("不存在的账本"));
    }

    // ============ 工具方法 ============

    /**
     * 获取 LiveData 的值（同步等待）
     */
    private <T> T getValue(LiveData<T> liveData) throws InterruptedException {
        final T[] value = (T[]) new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T o) {
                value[0] = o;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        liveData.observeForever(observer);
        latch.await(2, TimeUnit.SECONDS);
        return value[0];
    }
}