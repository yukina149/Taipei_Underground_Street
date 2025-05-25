package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PersonalAccount extends AppCompatActivity {

    private EditText editTextUsername;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonRegister;
    private RegisterDatabaseHelper dbHelper; // Change to RegisterDatabaseHelper
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_account);

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 初始化資料庫
        dbHelper = new RegisterDatabaseHelper(this); // Change to RegisterDatabaseHelper

        // 初始化 UI 元素
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);

        // 設置初始狀態為訪客
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) {
            editor.putString("loggedInUser", "訪客");
            editor.putString("userId", "訪客");
            editor.apply();
        }

        // 登入按鈕的點擊事件（必須填寫帳號和密碼）
        buttonLogin.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            // 檢查帳號和密碼是否為空
            if (username.isEmpty()) {
                editTextUsername.setError("帳號不能為空白");
                editTextUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                editTextPassword.setError("密碼不能為空白");
                editTextPassword.requestFocus();
                return;
            }

            if (dbHelper.checkUser(username, password)) {
                Toast.makeText(PersonalAccount.this, "Login successful", Toast.LENGTH_SHORT).show();
                Log.d("RegisterDatabaseHelper", "Login attempt for " + username + ": Success");

                // 獲取用戶 ID
                String userId = dbHelper.getUserId(username, password);

                // 儲存登入狀態
                SharedPreferences.Editor loginEditor = sharedPreferences.edit();
                loginEditor.putString("loggedInUser", username);
                loginEditor.putString("userId", userId != null ? userId : "未知 ID");
                loginEditor.putBoolean("isLoggedIn", true);
                loginEditor.apply();

                // 跳轉到 Chatroom Activity 並傳遞用戶名和 ID
                Intent intent = new Intent(PersonalAccount.this, Chatroom.class);
                intent.putExtra("username", username);
                intent.putExtra("userId", userId != null ? userId : "未知 ID");
                startActivity(intent);
                finish(); // 關閉登入畫面
            } else {
                Toast.makeText(PersonalAccount.this, "Login failed", Toast.LENGTH_SHORT).show();
                Log.d("RegisterDatabaseHelper", "Login attempt for " + username + ": Failed");
            }
            dbHelper.syncDatabase();
        });

        // 註冊按鈕的點擊事件（無需檢查，直接跳轉到 CreatAccount）
        buttonRegister.setOnClickListener(v -> {
            Intent intent = new Intent(PersonalAccount.this, CreatAccount.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.closeDatabase(); // Use closeDatabase() from RegisterDatabaseHelper
        }
    }
}