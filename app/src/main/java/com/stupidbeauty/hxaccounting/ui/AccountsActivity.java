package com.stupidbeauty.hxaccounting.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.AccountType;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;

import java.util.List;

public class AccountsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvAccounts;
    private View emptyView;
    private FloatingActionButton fabAddAccount;
    private AccountAdapter accountAdapter;
    private AccountRepository accountRepository;

    private final String[] presetColors = {
        "#FF6B6B", "#FF4ECDC4", "#FFFFA07A", "#FF95E1D3",
        "#FFF38181", "#FFAA96DA", "#FF6C5CE7", "#FFFDA7DF",
        "#FF26DE81", "#FF778CA3", "#FFFFD93D", "#FF6A0572"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);

        accountRepository = new AccountRepository(this);

        bindViews();
        setupToolbar();
        loadAccounts();
        fabAddAccount.setOnClickListener(v -> showCreateDialog());
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        rvAccounts = findViewById(R.id.rvAccounts);
        emptyView = findViewById(R.id.emptyView);
        fabAddAccount = findViewById(R.id.fabAddAccount);
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadAccounts() {
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        accountRepository.getActiveAccounts().observe(this, accounts -> {
            if (accounts == null || accounts.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                rvAccounts.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                rvAccounts.setVisibility(View.VISIBLE);
                if (accountAdapter == null) {
                    accountAdapter = new AccountAdapter(accounts);
                    accountAdapter.setOnAccountClickListener(new AccountAdapter.OnAccountClickListener() {
                        @Override
                        public void onAccountClick(Account account) {
                            setCurrentAccount(account);
                        }

                        @Override
                        public void onMoreClick(Account account, View view) {
                            showMoreMenu(account, view);
                        }
                    });
                    rvAccounts.setAdapter(accountAdapter);
                } else {
                    accountAdapter.notifyDataSetChanged();
                }
                long currentId = accountRepository.getCurrentAccountIdSync();
                accountAdapter.setCurrentAccountId(currentId);
            }
        });
    }

    private void setCurrentAccount(Account account) {
        accountRepository.setCurrentAccountId(account.getId());
        accountAdapter.setCurrentAccountId(account.getId());
        Toast.makeText(this, R.string.msg_account_set_current, Toast.LENGTH_SHORT).show();
    }

    private void showMoreMenu(Account account, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.action_set_current);
        popup.getMenu().add(0, 2, 1, R.string.action_edit_account);
        popup.getMenu().add(0, 3, 2, R.string.action_delete_account);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: setCurrentAccount(account); return true;
                case 2: showEditDialog(account); return true;
                case 3: confirmDelete(account); return true;
            }
            return false;
        });
        popup.show();
    }

    private void showCreateDialog() {
        showAccountDialog(null);
    }

    private void showEditDialog(Account account) {
        showAccountDialog(account);
    }

    private void showAccountDialog(Account existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account, null);
        EditText etName = dialogView.findViewById(R.id.etAccountName);
        TextView tvType = dialogView.findViewById(R.id.tvAccountType);
        TextView tvColor = dialogView.findViewById(R.id.tvAccountColor);

        final AccountType[] selectedType = {AccountType.CASH};
        final String[] selectedColor = {presetColors[0]};

        if (existing != null) {
            etName.setText(existing.getName());
            try {
                selectedType[0] = existing.getAccountType();
            } catch (Exception ignored) {}
            selectedColor[0] = existing.getColor();
        }

        updateTypeDisplay(tvType, selectedType[0]);
        updateColorDisplay(tvColor, selectedColor[0]);

        tvType.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.label_account_type);
            String[] typeNames = new String[AccountType.values().length];
            for (int i = 0; i < AccountType.values().length; i++) {
                typeNames[i] = AccountType.values()[i].getDisplayName();
            }
            builder.setItems(typeNames, (dialog, which) -> {
                selectedType[0] = AccountType.values()[which];
                updateTypeDisplay(tvType, selectedType[0]);
            });
            builder.show();
        });

        tvColor.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.label_account_color);
            String[] colorNames = new String[presetColors.length];
            for (int i = 0; i < presetColors.length; i++) {
                colorNames[i] = "●  颜色 " + (i + 1);
            }
            builder.setItems(colorNames, (dialog, which) -> {
                selectedColor[0] = presetColors[which];
                updateColorDisplay(tvColor, selectedColor[0]);
            });
            builder.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.dialog_create_account_title : R.string.dialog_edit_account_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(this, R.string.error_account_name_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (accountRepository.existsByName(name) && (existing == null || !existing.getName().equals(name))) {
                    Toast.makeText(this, R.string.error_account_name_exists, Toast.LENGTH_SHORT).show();
                    return;
                }
                Account account = existing != null ? existing : new Account();
                account.setName(name);
                account.setAccountType(selectedType[0]);
                account.setColor(selectedColor[0]);
                if (existing == null) {
                    accountRepository.createAndSetCurrent(account, id -> runOnUiThread(() -> {
                        Toast.makeText(this, R.string.msg_account_created, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }));
                } else {
                    accountRepository.update(account);
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.msg_account_updated, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                }
            });
        });
        dialog.show();
    }

    private void updateTypeDisplay(TextView tv, AccountType type) {
        tv.setText(type.getDisplayName());
        tv.setTag(type);
    }

    private void updateColorDisplay(TextView tv, String color) {
        tv.setText("●  " + color);
        tv.setBackgroundColor(android.graphics.Color.parseColor(color));
    }

    private void confirmDelete(Account account) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_account_title)
                .setMessage(getString(R.string.dialog_delete_account_message, account.getName()))
                .setPositiveButton(R.string.action_delete_account, (dialog, which) -> {
                    accountRepository.delete(account);
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.msg_account_deleted, Toast.LENGTH_SHORT).show();
                        if (account.getId() == accountRepository.getCurrentAccountIdSync()) {
                            accountRepository.setCurrentAccountId(-1L);
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
    }
}
