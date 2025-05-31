package com.example.cameraproject_2;

import static org.opencv.android.NativeCameraView.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

public class PersonalAccount extends AppCompatActivity {

    private EditText editTextUsername;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonRegister;
    private RegisterDatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private List<AlertDialog> invitationDialogs = new ArrayList<>();

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

            boolean syncSuccess = dbHelper.syncDatabase();
            if (!syncSuccess) {
                Log.w("PersonalAccount", "Database sync failed, proceeding with login");
                Toast.makeText(PersonalAccount.this, "資料庫同步失敗，但仍可繼續登入", Toast.LENGTH_LONG).show();
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

                if (syncSuccess) {
                    checkGroupInvitations(username, password);
                } else {
                    Log.w("PersonalAccount", "Sync failed, skipping invitation check");
                }

                Set<String> groupNames = sharedPreferences.getStringSet("group_names", new HashSet<>());
                Intent intent = new Intent(PersonalAccount.this, Chatroom.class);
                intent.putExtra("username", username);
                intent.putExtra("userId", userId != null ? userId : "未知 ID");
                intent.putStringArrayListExtra("groupNames", new ArrayList<>(new ArrayList<>(groupNames)));
                if (!groupNames.isEmpty()) {
                    String firstGroup = groupNames.iterator().next();
                    String membersString = sharedPreferences.getString(firstGroup + "_members", "");
                    List<String> membersList = new ArrayList<>();
                    if (!membersString.isEmpty()) {
                        String[] membersArray = membersString.split(",");
                        for (String member : membersArray) {
                            membersList.add(member);
                        }
                    }
                    intent.putExtra("groupName", firstGroup);
                    intent.putStringArrayListExtra("members", new ArrayList<>(membersList));
                }

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

    private void checkGroupInvitations(String username, String password) {
        RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
        String userId = getUserId(username, password);
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
                            builder.setOnDismissListener(dialog -> {
                                invitationDialogs.remove(dialog);
                            });
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
    private void checkInvitations() {
        String userId = sharedPreferences.getString("userId", null);
        if (userId != null) {
            Log.d(TAG, "Checking invitations for userId: " + userId);
            List<Invitation> invitations = dbHelper.getPendingInvitations(userId);
            Log.d(TAG, "Found " + invitations.size() + " invitations");
            if (!invitations.isEmpty()) {
                for (Invitation invitation : invitations) {
                    Log.d(TAG, "Displaying invitation: id=" + invitation.getInvitationId() + ", group=" + invitation.getGroupName());
                    // 更新 SharedPreferences 中的群組列表
                    Set<String> groupNames = new HashSet<>(sharedPreferences.getStringSet("groupNames", new HashSet<>()));
                    groupNames.add(invitation.getGroupName());
                    sharedPreferences.edit().putStringSet("groupNames", groupNames).apply();
                    // 顯示邀請對話框或其他 UI 更新
                    showInvitationDialog(invitation);
                }
            }
        }
    }

    private void showInvitationDialog(Invitation invitation) {
        new AlertDialog.Builder(this)
                .setTitle("新邀請")
                .setMessage("您已被邀請加入群組: " + invitation.getGroupName())
                .setPositiveButton("接受", (dialog, which) -> {
                    acceptInvitation(invitation);
                    dialog.dismiss();
                })
                .setNegativeButton("拒絕", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void acceptInvitation(Invitation invitation) {
        RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
        dbHelper.updateInvitationStatus(invitation.getInvitationId(), "accepted");
        addGroupToSharedPreferences(invitation.getGroupName());
        Log.d(TAG, "Accepted invitation for group: " + invitation.getGroupName());
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
        super.onDestroy();
    }

    private void addGroupToSharedPreferences(String groupName) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        Set<String> groups = new HashSet<>(prefs.getStringSet("group_names", new HashSet<>()));
        groups.add(groupName);
        prefs.edit().putStringSet("group_names", groups).apply();
    }

    private String getUserId(String username, String password) {
        return dbHelper.getUserId(username, password);
    }
}