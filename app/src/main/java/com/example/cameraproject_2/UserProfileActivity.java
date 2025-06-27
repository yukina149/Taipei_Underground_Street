package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class UserProfileActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private ImageView profileImage;
    private EditText editTextUsername;
    private TextView textViewUserId;
    private Button buttonLogout;
    private Button buttonChangeProfile;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 綁定 UI 元素
        profileImage = findViewById(R.id.profile_image);
        editTextUsername = findViewById(R.id.edit_text_username);
        textViewUserId = findViewById(R.id.text_view_user_id);
        buttonLogout = findViewById(R.id.button_logout);
        buttonChangeProfile = findViewById(R.id.button_change_profile);

        // 設置圖片選擇啟動器
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            profileImage.setImageURI(selectedImageUri);
                            Toast.makeText(this, "頭像已更新", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 從 Intent 獲取狀態（優先級高於 SharedPreferences）
        Intent intent = getIntent();
        boolean isLoggedIn = intent.getBooleanExtra("isLoggedIn", sharedPreferences.getBoolean("isLoggedIn", false));
        String loggedInUser = intent.getStringExtra("loggedInUser");
        String userId = intent.getStringExtra("userId");

        // 如果 Intent 數據為 null，則從 SharedPreferences 獲取
        if (loggedInUser == null) loggedInUser = sharedPreferences.getString("loggedInUser", "未知用戶");
        if (userId == null) userId = sharedPreferences.getString("userId", "未知 ID");

        // 根據狀態顯示內容
        if (isLoggedIn) {
            editTextUsername.setText(loggedInUser);
            editTextUsername.setEnabled(true); // 允許編輯
            textViewUserId.setText("帳號: " + userId);

            buttonChangeProfile.setOnClickListener(v -> changeProfileImage());
            buttonLogout.setOnClickListener(v -> logout());
            editTextUsername.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !editTextUsername.getText().toString().isEmpty()) {
                    updateUsername(editTextUsername.getText().toString());
                }
            });
        } else {
            // 未登入時顯示提示並跳轉
            editTextUsername.setText("");
            textViewUserId.setText("帳號: 未登入");
            buttonChangeProfile.setVisibility(View.GONE);
            buttonLogout.setVisibility(View.GONE);
            Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show();
            Intent personalIntent = new Intent(this, PersonalAccount.class);
            startActivity(personalIntent);
            finish();
        }
    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void updateUsername(String newUsername) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("loggedInUser", newUsername);
        editor.apply();
        Toast.makeText(this, "姓名已更新為: " + newUsername, Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", false);
        editor.remove("loggedInUser");
        editor.putString("userId", "訪客");
        editor.apply();
        Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, UserProfileActivity.class);
        startActivity(intent);
        finish();
    }
}