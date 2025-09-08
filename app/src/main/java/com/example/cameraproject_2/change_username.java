package com.example.cameraproject_2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class change_username extends AppCompatActivity {

    private EditText editTextNewUsername;
    private Button buttonConfirmUsername;
    private String userId;
    private String originalUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_username);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定 UI 元素
        editTextNewUsername = findViewById(R.id.edit_text_new_username);
        buttonConfirmUsername = findViewById(R.id.button_confirm_username);

        // 獲取 Intent 數據
        userId = getIntent().getStringExtra("userId");
        originalUsername = getIntent().getStringExtra("originalUsername");

        // 設置返回箭頭點擊事件
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        // 設置確認按鈕點擊事件
        buttonConfirmUsername.setOnClickListener(v -> {
            String newUsername = editTextNewUsername.getText().toString().trim();
            if (newUsername.isEmpty()) {
                Toast.makeText(this, "用戶名不能為空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newUsername.equals(originalUsername)) {
                Toast.makeText(this, "新用戶名與原用戶名相同", Toast.LENGTH_SHORT).show();
                return;
            }

            RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
            boolean success = dbHelper.updateUsername(userId, newUsername);
            if (success) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("newUsername", newUsername);
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                Toast.makeText(this, "更新用戶名失敗", Toast.LENGTH_SHORT).show();
            }
        });
    }
}