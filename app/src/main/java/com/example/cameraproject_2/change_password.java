package com.example.cameraproject_2;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class change_password extends AppCompatActivity {

    private TextInputEditText editTextOldPassword;
    private TextInputEditText editTextNewPassword;
    private TextInputEditText editTextConfirmPassword;
    private Button buttonConfirmPassword;
    private TextView textViewError;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 綁定 UI 元素
        editTextOldPassword = findViewById(R.id.edit_text_old_password);
        editTextNewPassword = findViewById(R.id.edit_text_new_password);
        editTextConfirmPassword = findViewById(R.id.edit_text_confirm_password);
        buttonConfirmPassword = findViewById(R.id.button_confirm_password);
        textViewError = findViewById(R.id.text_view_error);

        // 獲取 Intent 數據
        userId = getIntent().getStringExtra("userId");

        // 設置返回箭頭點擊事件
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        // 設置確認按鈕點擊事件
        buttonConfirmPassword.setOnClickListener(v -> {
            String oldPassword = editTextOldPassword.getText().toString().trim();
            String newPassword = editTextNewPassword.getText().toString().trim();
            String confirmPassword = editTextConfirmPassword.getText().toString().trim();

            if (oldPassword.isEmpty()) {
                editTextOldPassword.setError("請輸入當前密碼");
                return;
            }

            if (newPassword.isEmpty()) {
                editTextNewPassword.setError("請輸入新密碼");
                return;
            }

            if (newPassword.length() < 6) {
                editTextNewPassword.setError("新密碼長度至少需要 6 個字符");
                return;
            }

            if (confirmPassword.isEmpty()) {
                editTextConfirmPassword.setError("請再次輸入新密碼");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                textViewError.setText("錯誤：新密碼與確認密碼不一致");
                return;
            }

            RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
            String currentPassword = dbHelper.getCurrentPassword(userId);
            if (currentPassword == null || !currentPassword.equals(oldPassword)) {
                editTextOldPassword.setError("當前密碼不正確");
                return;
            }

            boolean success = dbHelper.updatePassword(userId, newPassword);
            if (success) {
                Toast.makeText(this, "密碼已更新", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "更新密碼失敗", Toast.LENGTH_SHORT).show();
            }
        });
    }
}