package com.example.cameraproject_2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PersonalAccount extends AppCompatActivity {

    private EditText editTextUsername;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonRegister;
    private RegisterDatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private List<AlertDialog> invitationDialogs = new ArrayList<>();
    private ProgressDialog progressDialog;
    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_account);

        // 初始化執行緒池和主執行緒 Handler
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

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
            editor.putString("profileImageUrl", null);
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

            // 顯示載入對話框
            progressDialog = new ProgressDialog(PersonalAccount.this);
            progressDialog.setMessage("正在登入...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // 在背景執行緒中執行登錄
            executorService.execute(() -> {
                // 優先檢查本地數據庫中的用戶憑證
                boolean loginSuccess = dbHelper.checkUser(username, password);

                // 在主執行緒中更新 UI
                mainHandler.post(() -> {
                    progressDialog.dismiss();

                    if (loginSuccess) {
                        Toast.makeText(PersonalAccount.this, "登入成功", Toast.LENGTH_SHORT).show();
                        Log.d("PersonalAccount", "Login attempt for " + username + ": Success");

                        String userId = dbHelper.getUserId(username, password);
                        String profileImageUrl = dbHelper.getProfileImageUrl(userId);

                        SharedPreferences.Editor loginEditor = sharedPreferences.edit();
                        loginEditor.putString("loggedInUser", username);
                        loginEditor.putString("userId", userId != null ? userId : "未知 ID");
                        loginEditor.putBoolean("isLoggedIn", true);
                        loginEditor.putString("profileImageUrl", profileImageUrl);
                        loginEditor.apply();

                        // 檢查群組邀請（本地數據）
                        checkGroupInvitations(username, password);

                        // 啟動後台同步，獨立於登錄流程
                        startBackgroundSync();

                        // 跳轉到 UserProfileActivity
                        Set<String> groupNames = sharedPreferences.getStringSet("group_names", new HashSet<>());
                        Intent intent = new Intent(PersonalAccount.this, UserProfileActivity.class);
                        intent.putExtra("username", username);
                        intent.putExtra("userId", userId != null ? userId : "未知 ID");
                        intent.putExtra("profileImageUrl", profileImageUrl);
                        intent.putStringArrayListExtra("groupNames", new ArrayList<>(groupNames));
                        if (!groupNames.isEmpty()) {
                            String firstGroup = groupNames.iterator().next();
                            String membersString = sharedPreferences.getString(firstGroup + "_members", "");
                            List<String> membersList = new ArrayList<>();
                            if (!membersString.isEmpty()) {
                                String[] membersArray = membersString.split(",");
                                for (String member : membersArray) {
                                    membersList.add(member.trim());
                                }
                            }
                            intent.putExtra("groupName", firstGroup);
                            intent.putStringArrayListExtra("members", new ArrayList<>(membersList));
                        }
                        startActivity(intent);
                        overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                        finish();
                    } else {
                        Toast.makeText(PersonalAccount.this, "登入失敗，請檢查帳號或密碼", Toast.LENGTH_SHORT).show();
                        Log.d("PersonalAccount", "Login attempt for " + username + ": Failed");
                    }
                });
            });
        });

        buttonRegister.setOnClickListener(v -> {
            Intent intent = new Intent(PersonalAccount.this, CreatAccount.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
        });
    }

    private void startBackgroundSync() {
        // 在背景執行緒中執行同步
        executorService.execute(() -> {
            // 使用 SyncCallback 處理同步結果
            AtomicBoolean syncSuccess = new AtomicBoolean(false); // 使用 AtomicBoolean 跨執行緒共享狀態
            dbHelper.syncDatabase(new RegisterDatabaseHelper.SyncCallback() {
                @Override
                public void onSyncComplete(boolean success) {
                    syncSuccess.set(success);
                }
            });

            mainHandler.post(() -> {
                if (!syncSuccess.get()) {
                    Log.w("PersonalAccount", "Background database sync failed");
                    Toast.makeText(PersonalAccount.this, "資料庫同步失敗，將在下次嘗試", Toast.LENGTH_LONG).show();
                } else {
                    Log.d("PersonalAccount", "Background database sync completed");
                    // 同步完成後重新檢查邀請
                    String username = sharedPreferences.getString("loggedInUser", null);
                    String userId = dbHelper.getUserIdFromUsername(username);
                    if (userId != null) {
                        checkGroupInvitations(username, null);
                    }
                }
            });
        });
    }
    private void checkGroupInvitations(String username, String password) {
        String userId = dbHelper.getUserId(username, password);
        Log.d("PersonalAccount", "Checking invitations for userId: " + userId);
        if (userId != null) {
            List<Invitation> invitations = dbHelper.getPendingInvitations(userId);
            Log.d("PersonalAccount", "Found " + (invitations != null ? invitations.size() : 0) + " invitations");
            if (invitations != null && !invitations.isEmpty()) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        for (Invitation invitation : invitations) {
                            Log.d("PersonalAccount", "Displaying invitation: id=" + invitation.getInvitationId() + ", group=" + invitation.getGroupName());
                            AlertDialog.Builder builder = new AlertDialog.Builder(PersonalAccount.this);
                            builder.setTitle("群組邀請");
                            builder.setMessage("您被邀請加入群組: " + invitation.getGroupName());
                            builder.setPositiveButton("接受", (dialog, which) -> {
                                dbHelper.updateInvitationStatus(invitation.getInvitationId(), "accepted");
                                addGroupToSharedPreferences(invitation.getGroupName());
                                dialog.dismiss();
                            });
                            builder.setNegativeButton("拒絕", (dialog, which) -> {
                                dbHelper.updateInvitationStatus(invitation.getInvitationId(), "rejected");
                                dialog.dismiss();
                            });
                            builder.setOnDismissListener(dialog -> invitationDialogs.remove(dialog));
                            AlertDialog dialog = builder.create();
                            invitationDialogs.add(dialog);
                            dialog.show();
                        }
                    } else {
                        Log.d("PersonalAccount", "Activity is finishing, skipping dialog display");
                    }
                });
            }
        }
    }

    private void addGroupToSharedPreferences(String groupName) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        Set<String> groups = new HashSet<>(prefs.getStringSet("group_names", new HashSet<>()));
        groups.add(groupName);
        prefs.edit().putStringSet("group_names", groups).apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (AlertDialog dialog : new ArrayList<>(invitationDialogs)) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        invitationDialogs.clear();
    }

    @Override
    protected void onDestroy() {
        for (AlertDialog dialog : new ArrayList<>(invitationDialogs)) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        invitationDialogs.clear();
        executorService.shutdown();
        super.onDestroy();
    }
}