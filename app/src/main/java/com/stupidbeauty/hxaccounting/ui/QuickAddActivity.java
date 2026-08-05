package com.stupidbeauty.hxaccounting.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
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

/**
 * 快速记账 Activity（B4 核心）
 * 5-10秒完成一笔
 */
public class QuickAddActivity extends AppCompatActivity {

    private EditText etAmount;
    private EditText etDescription;
    private MaterialButtonToggleGroup toggleType;
    private MaterialButtonToggleGroup togglePayment;
    private MaterialCheckBox cbAnomaly;
    private MaterialButton btnSave;
    private RecyclerView rvCategories;
    private CategoryAdapter categoryAdapter;

    private TransactionType selectedType = TransactionType.EXPENSE;
    private PaymentMethod selectedPayment = PaymentMethod.CASH;
    private Category selectedCategory;

    private TransactionRepository transactionRepository;
    private CategoryRepository categoryRepository;
    private AccountRepository accountRepository;

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

        btnSave.setOnClickListener(v -> saveTransaction());
    }

    private void bindViews() {
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        toggleType = findViewById(R.id.toggleType);
        togglePayment = findViewById(R.id.togglePayment);
        cbAnomaly = findViewById(R.id.cbAnomaly);
        btnSave = findViewById(R.id.btnSave);
        rvCategories = findViewById(R.id.rvCategories);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
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

        long currentAccountId = accountRepository.getCurrentAccountIdSync();
        if (currentAccountId == -1L) {
            LiveData<List<Account>> accountsLive = accountRepository.getActiveAccounts();
            accountsLive.observe(this, accounts -> {
                if (accounts == null || accounts.isEmpty()) {
                    Toast.makeText(this, R.string.error_no_account, Toast.LENGTH_LONG).show();
                } else {
                    saveToAccount(accounts.get(0).getId(), amount);
                }
            });
            return;
        }

        saveToAccount(currentAccountId, amount);
    }

    private void saveToAccount(long accountId, double amount) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
    }
}
