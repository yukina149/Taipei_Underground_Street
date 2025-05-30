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
    private RegisterDatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_account);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        dbHelper = new RegisterDatabaseHelper(this);

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) {
            editor.putString("loggedInUser", "訪客");
            editor.putString("userId", "訪客");
            editor.apply();
        }

        buttonLogin.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

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

            // 在檢查用戶之前同步資料庫
            boolean syncSuccess = dbHelper.syncDatabase();
            if (!syncSuccess) {
                Toast.makeText(PersonalAccount.this, "無法同步資料庫，請檢查網路連線", Toast.LENGTH_LONG).show();
                return;
            }

            if (dbHelper.checkUser(username, password)) {
                Toast.makeText(PersonalAccount.this, "Login successful", Toast.LENGTH_SHORT).show();
                Log.d("RegisterDatabaseHelper", "Login attempt for " + username + ": Success");

                String userId = dbHelper.getUserId(username, password);

                SharedPreferences.Editor loginEditor = sharedPreferences.edit();
                loginEditor.putString("loggedInUser", username);
                loginEditor.putString("userId", userId != null ? userId : "未知 ID");
                loginEditor.putBoolean("isLoggedIn", true);
                loginEditor.apply();

                Intent intent = new Intent(PersonalAccount.this, Chatroom.class);
                intent.putExtra("username", username);
                intent.putExtra("userId", userId != null ? userId : "未知 ID");
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(PersonalAccount.this, "Login failed", Toast.LENGTH_SHORT).show();
                Log.d("RegisterDatabaseHelper", "Login attempt for " + username + ": Failed");
            }
        });

        buttonRegister.setOnClickListener(v -> {
            Intent intent = new Intent(PersonalAccount.this, CreatAccount.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.closeDatabase();
        }
    }
}