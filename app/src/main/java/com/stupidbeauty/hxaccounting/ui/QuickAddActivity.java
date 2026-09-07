package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.Category;
import com.stupidbeauty.hxaccounting.data.entity.PaymentMethod;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import com.stupidbeauty.hxaccounting.data.entity.TransactionType;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;
import com.stupidbeauty.hxaccounting.data.repository.CategoryRepository;
import com.stupidbeauty.hxaccounting.data.repository.TransactionRepository;

import java.util.List;
import java.util.Objects;

/**
 * 快速记账 / 编辑流水 Activity（B4 + 编辑流水功能）
 *
 * 模式说明：
 * 1. 新增模式（默认）：Intent 不带 EXTRA_TRANSACTION_ID → 记一笔新流水到当前账本
 * 2. 编辑模式：Intent 带 EXTRA_TRANSACTION_ID → 加载已有流水并允许修改（含账本切换）
 *
 * 编辑流水功能：feat/edit-transaction-move-account 分支
 */
public class QuickAddActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION_ID = "extra_transaction_id";

    private EditText etAmount;
    private EditText etDescription;
    private MaterialButtonToggleGroup toggleType;
    private MaterialButtonToggleGroup togglePayment;
    private MaterialCheckBox cbAnomaly;
    private MaterialButton btnSave;
    private MaterialButton btnAccountPicker;
    private LinearLayout accountPickerContainer;
    private RecyclerView rvCategories;
    private MaterialToolbar toolbar;
    private CategoryAdapter categoryAdapter;

    private TransactionType selectedType = TransactionType.EXPENSE;
    private PaymentMethod selectedPayment = PaymentMethod.CASH;
    private Category selectedCategory;

    private TransactionRepository transactionRepository;
    private CategoryRepository categoryRepository;
    private AccountRepository accountRepository;

    // 编辑模式相关字段
    private long editingTransactionId = -1L;        // -1 表示新增模式
    private Transaction editingTransaction;         // 编辑模式下加载的流水
    private Account selectedAccount;                 // 编辑模式下选中的目标账本
    private List<Account> allAccounts;              // 编辑模式下拉选项

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_add);

        transactionRepository = new TransactionRepository(this);
        categoryRepository = new CategoryRepository(this);
        accountRepository = new AccountRepository(this);

        bindViews();
        setupToolbar();
        setupTypeToggle();
        setupPaymentToggle();
        setupCategoryGrid();

        // 解析编辑模式 Intent
        parseIntent();

        // 加载账本列表（编辑模式用于账本切换器）
        if (editingTransactionId > 0) {
            loadAccountsForEdit();
        }

        btnSave.setOnClickListener(v -> saveTransaction());
    }

    private void bindViews() {
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        toggleType = findViewById(R.id.toggleType);
        togglePayment = findViewById(R.id.togglePayment);
        cbAnomaly = findViewById(R.id.cbAnomaly);
        btnSave = findViewById(R.id.btnSave);
        btnAccountPicker = findViewById(R.id.btnAccountPicker);
        accountPickerContainer = findViewById(R.id.accountPickerContainer);
        rvCategories = findViewById(R.id.rvCategories);
        toolbar = findViewById(R.id.toolbar);
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_TRANSACTION_ID)) {
            editingTransactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L);
            if (editingTransactionId > 0) {
                // 编辑模式：先显示账本选择器（账本列表由 loadAccountsForEdit 异步加载）
                // 流水数据由 loadTransactionForEdit 异步加载
                // 这样避免在 onCreate 主线程访问 Room 数据库
                accountPickerContainer.setVisibility(View.VISIBLE);
                toolbar.setTitle(R.string.title_edit_transaction);
                btnSave.setText(R.string.btn_save);
                loadTransactionForEdit(editingTransactionId);
            }
        }
    }

    /**
     * 异步加载待编辑的流水（修复主线程崩溃）
     * 之前用 getByIdSync 会在主线程访问 Room 数据库，导致 IllegalStateException
     * 修复方案：用 getByIdAsync 异步查询，结果在 callback 回调中处理
     */
    private void loadTransactionForEdit(long id) {
        transactionRepository.getByIdAsync(id, transaction -> {
            // 通过 runOnUiThread 切回主线程
            runOnUiThread(() -> {
                if (transaction == null) {
                    Toast.makeText(this, "流水不存在或已被删除", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editingTransaction = transaction;
                // 填充表单
                etAmount.setText(String.valueOf(editingTransaction.getAmount()));
                if (editingTransaction.getDescription() != null) {
                    etDescription.setText(editingTransaction.getDescription());
                }
                // 切换类型
                if (editingTransaction.getTransactionType() == TransactionType.INCOME) {
                    toggleType.check(R.id.btnIncome);
                    selectedType = TransactionType.INCOME;
                } else {
                    toggleType.check(R.id.btnExpense);
                    selectedType = TransactionType.EXPENSE;
                }
                // 切换支付方式
                PaymentMethod pm = editingTransaction.getPaymentMethodEnum();
                switch (pm) {
                    case CASH:     togglePayment.check(R.id.payCash); selectedPayment = PaymentMethod.CASH; break;
                    case WECHAT:   togglePayment.check(R.id.payWechat); selectedPayment = PaymentMethod.WECHAT; break;
                    case ALIPAY:   togglePayment.check(R.id.payAlipay); selectedPayment = PaymentMethod.ALIPAY; break;
                    case CARD:     togglePayment.check(R.id.payCard); selectedPayment = PaymentMethod.CARD; break;
                    default:       togglePayment.check(R.id.payOther); selectedPayment = PaymentMethod.OTHER; break;
                }
                cbAnomaly.setChecked(editingTransaction.isAnomaly());
                // 选中的分类在分类列表加载完后设置
                pendingCategoryId = editingTransaction.getCategoryId();
            });
        });
    }

    private Long pendingCategoryId;  // 分类列表加载完后回填

    /**
     * 加载所有账本（编辑模式用于账本切换 PopupMenu）
     */
    private void loadAccountsForEdit() {
        accountRepository.getActiveAccounts().observe(this, accounts -> {
            if (accounts == null || accounts.isEmpty()) return;
            allAccounts = accounts;
            // 默认选中原流水所属账本
            long originalAccountId = editingTransaction != null ? editingTransaction.getAccountId() : -1L;
            for (Account acc : accounts) {
                if (acc.getId() == originalAccountId) {
                    selectedAccount = acc;
                    btnAccountPicker.setText(acc.getName());
                    break;
                }
            }
            btnAccountPicker.setOnClickListener(v -> showAccountPickerMenu());
        });
    }

    private void showAccountPickerMenu() {
        if (allAccounts == null || allAccounts.isEmpty()) {
            Toast.makeText(this, "暂无可用账本", Toast.LENGTH_SHORT).show();
            return;
        }
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, btnAccountPicker);
        for (int i = 0; i < allAccounts.size(); i++) {
            Account acc = allAccounts.get(i);
            String label = (selectedAccount != null && acc.getId() == selectedAccount.getId() ? "✓ " : "   ") + acc.getName();
            popup.getMenu().add(0, i, i, label);
        }
        popup.setOnMenuItemClickListener(item -> {
            Account picked = allAccounts.get(item.getItemId());
            selectedAccount = picked;
            btnAccountPicker.setText(picked.getName());
            return true;
        });
        popup.show();
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTypeToggle() {
        toggleType.check(R.id.btnExpense);
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnExpense) {
                selectedType = TransactionType.EXPENSE;
            } else if (checkedId == R.id.btnIncome) {
                selectedType = TransactionType.INCOME;
            }
            loadCategories();
        });
    }

    private void setupPaymentToggle() {
        togglePayment.check(R.id.payCash);
        togglePayment.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.payCash) {
                selectedPayment = PaymentMethod.CASH;
            } else if (checkedId == R.id.payWechat) {
                selectedPayment = PaymentMethod.WECHAT;
            } else if (checkedId == R.id.payAlipay) {
                selectedPayment = PaymentMethod.ALIPAY;
            } else if (checkedId == R.id.payCard) {
                selectedPayment = PaymentMethod.CARD;
            } else if (checkedId == R.id.payOther) {
                selectedPayment = PaymentMethod.OTHER;
            }
        });
    }

    private void setupCategoryGrid() {
        rvCategories.setLayoutManager(new GridLayoutManager(this, 4));
        loadCategories();
    }

    private void loadCategories() {
        String typeStr = selectedType.name();
        LiveData<List<Category>> liveData = categoryRepository.getByType(typeStr);
        liveData.observe(this, categories -> {
            if (categories == null || categories.isEmpty()) return;
            categoryAdapter = new CategoryAdapter(categories);
            categoryAdapter.setOnCategoryClickListener(category -> {
                selectedCategory = category;
                categoryAdapter.setSelectedCategoryId(category.getId());
            });
            rvCategories.setAdapter(categoryAdapter);
            // 编辑模式：回填原分类选中
            if (pendingCategoryId != null) {
                categoryAdapter.setSelectedCategoryId(pendingCategoryId);
                for (Category c : categories) {
                    if (Objects.equals(c.getId(), pendingCategoryId)) {
                        selectedCategory = c;
                        break;
                    }
                }
                pendingCategoryId = null;
            }
        });
    }

    private void saveTransaction() {
        String amountStr = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(this, R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null) {
            Toast.makeText(this, R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingTransactionId > 0) {
            // 编辑模式：update
            saveEditedTransaction(amount);
        } else {
            // 新增模式：insert
            saveNewTransaction(amount);
        }
    }

    private void saveNewTransaction(double amount) {
        long currentAccountId = accountRepository.getCurrentAccountIdSync();
        if (currentAccountId == -1L) {
            LiveData<List<Account>> accountsLive = accountRepository.getActiveAccounts();
            accountsLive.observe(this, accounts -> {
                if (accounts == null || accounts.isEmpty()) {
                    Toast.makeText(this, R.string.error_no_account, Toast.LENGTH_LONG).show();
                } else {
                    insertTransaction(accounts.get(0).getId(), amount);
                }
            });
            return;
        }
        insertTransaction(currentAccountId, amount);
    }

    private void insertTransaction(long accountId, double amount) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setTransactionType(selectedType);
        transaction.setCategoryId(selectedCategory.getId());
        transaction.setPaymentMethodEnum(selectedPayment);
        transaction.setAnomaly(cbAnomaly.isChecked());

        String description = etDescription.getText() == null ? "" : etDescription.getText().toString().trim();
        if (!TextUtils.isEmpty(description)) {
            transaction.setDescription(description);
        }

        long now = System.currentTimeMillis();
        transaction.setTransactionTime(now);
        transaction.setUpdatedAt(now);

        transactionRepository.insert(transaction, id -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.msg_save_success, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private void saveEditedTransaction(double amount) {
        if (selectedAccount == null) {
            Toast.makeText(this, "请选择账本", Toast.LENGTH_SHORT).show();
            return;
        }
        Transaction t = editingTransaction;
        t.setAccountId(selectedAccount.getId());
        t.setAmount(amount);
        t.setTransactionType(selectedType);
        t.setCategoryId(selectedCategory.getId());
        t.setPaymentMethodEnum(selectedPayment);
        t.setAnomaly(cbAnomaly.isChecked());
        String description = etDescription.getText() == null ? "" : etDescription.getText().toString().trim();
        if (!TextUtils.isEmpty(description)) {
            t.setDescription(description);
        } else {
            t.setDescription(null);
        }
        t.setUpdatedAt(System.currentTimeMillis());

        transactionRepository.update(t, () -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.msg_update_success, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
    }
}
