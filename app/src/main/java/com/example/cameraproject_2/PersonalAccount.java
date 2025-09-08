package com.example.cameraproject_2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
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
    private ImageView backArrow;
    private RegisterDatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private List<AlertDialog> invitationDialogs = new ArrayList<>();
    private ProgressDialog progressDialog;
    private ExecutorService executorService;
    private Handler mainHandler;
    private TextInputLayout textInputLayoutPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_account);

        // 初始化執行緒池和主執行緒 Handler
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        dbHelper = new RegisterDatabaseHelper(this);

        // 初始化 UI 組件
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);
        backArrow = findViewById(R.id.backArrow);
        textInputLayoutPassword = findViewById(R.id.textInputLayoutPassword);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) {
            editor.putString("loggedInUser", "訪客");
            editor.putString("userId", "訪客");
            editor.putString("profileImageUrl", null);
            editor.apply();
        }

        // 設置返回箭頭的點擊事件
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(PersonalAccount.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
            finish();
        });

        buttonLogin.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (username.isEmpty()) {
                editTextUsername.setError("帳號不能為空白");
                editTextUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                textInputLayoutPassword.setError("密碼不能為空白");
                editTextPassword.requestFocus();
                return;
            }

            progressDialog = new ProgressDialog(PersonalAccount.this);
            progressDialog.setMessage("正在登入...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            executorService.execute(() -> {
                boolean loginSuccess = dbHelper.checkUser(username, password);

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

                        checkGroupInvitations(username, password);

                        startBackgroundSync();

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
        executorService.execute(() -> {
            AtomicBoolean syncSuccess = new AtomicBoolean(false);
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
        if (dbHelper != null) {
            dbHelper.closeDatabase();
        }
        super.onDestroy();
    }
}