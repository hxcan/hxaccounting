package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.stupidbeauty.hxaccounting.R;

/**
 * 主页面
 * 1. 顶部 AppBar（标题"太极记账"）
 * 2. 右下角 FAB 按钮：点击进入快速记账页面（B4 核心）
 *
 * 后续 B5 任务会在此处接入流水列表（RecyclerView）
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 FAB：点击 → 进入快速记账页面
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuickAddActivity.class);
            startActivity(intent);
        });
    }
}
