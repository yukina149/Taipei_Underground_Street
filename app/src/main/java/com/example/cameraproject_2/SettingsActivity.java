package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private Spinner languageSpinner;
    private RegisterDatabaseHelper registerDbHelper;
    private SharedPreferences sharedPreferences;
    private String defaultLang;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 根據設備區域設置初始語言
        defaultLang = Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en";
        currentLang = sharedPreferences.getString("language", defaultLang);

        // 應用當前語言
        applyLanguage(currentLang);

        // 設置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // 初始化資料庫
        registerDbHelper = new RegisterDatabaseHelper(this);

        languageSpinner = findViewById(R.id.language_spinner);

        String[] languages = {
                getString(R.string.language_english),
                getString(R.string.language_japanese),
                getString(R.string.language_chinese)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        // 設置當前語言
        int selectedPosition = 0;
        switch (currentLang) {
            case "ja":
                selectedPosition = 1;
                break;
            case "zh":
                selectedPosition = 2;
                break;
            default:
                selectedPosition = 0;
                break;
        }
        languageSpinner.setSelection(selectedPosition);

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newLangCode;
                switch (position) {
                    case 0:
                        newLangCode = "en";
                        break;
                    case 1:
                        newLangCode = "ja";
                        break;
                    case 2:
                        newLangCode = "zh";
                        break;
                    default:
                        newLangCode = "en";
                }

                // 只有當語言改變時才顯示確認對話框
                if (!newLangCode.equals(currentLang)) {
                    showLanguageChangeConfirmation(newLangCode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 使用說明
        findViewById(R.id.help_text).setOnClickListener(v -> {
            new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle(R.string.user_guide)
                    .setMessage(R.string.user_guide_content)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        // 刪除帳號
        findViewById(R.id.delete_account_text).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_account)
                    .setMessage(R.string.delete_account_confirm)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        String userId = sharedPreferences.getString("userId", null);
                        if (userId != null) {
                            registerDbHelper.deleteUserData(userId);
                            sharedPreferences.edit()
                                    .clear()
                                    .putString("userId", getString(R.string.guest))
                                    .putBoolean("isLoggedIn", false)
                                    .apply();
                            Toast.makeText(this, R.string.account_deleted, Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(this, R.string.user_info_not_found, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyLanguage(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = getResources().getConfiguration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        sharedPreferences.edit().putString("language", langCode).apply();
    }

    private void showLanguageChangeConfirmation(String newLangCode) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_language_change)
                .setMessage(getString(R.string.confirm_language_message, getLanguageDisplayName(newLangCode)))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    currentLang = newLangCode;
                    applyLanguage(newLangCode);
                    restartApp();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    int selectedPosition = 0;
                    switch (currentLang) {
                        case "ja":
                            selectedPosition = 1;
                            break;
                        case "zh":
                            selectedPosition = 2;
                            break;
                        default:
                            selectedPosition = 0;
                            break;
                    }
                    languageSpinner.setSelection(selectedPosition);
                    Toast.makeText(this, R.string.language_change_cancelled, Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private String getLanguageDisplayName(String langCode) {
        switch (langCode) {
            case "en":
                return getString(R.string.language_english);
            case "ja":
                return getString(R.string.language_japanese);
            case "zh":
                return getString(R.string.language_chinese);
            default:
                return "Unknown";
        }
    }

    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registerDbHelper != null) {
            registerDbHelper.closeDatabase();
        }
    }
}