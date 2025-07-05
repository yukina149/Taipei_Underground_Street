package com.example.cameraproject_2;

import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_GROUP_NAME;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_INVITATION_ID;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_INVITED_USER;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_IS_SYNCED_INV;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_STATUS;
import static com.example.cameraproject_2.RegisterDatabaseHelper.TABLE_INVITATIONS;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class CreateGroupActivity extends AppCompatActivity {
    private EditText editTextGroupName;
    private EditText editTextMemberId;
    private Button buttonSearchMember;
    private RecyclerView memberRecyclerView;
    private Button buttonCreateGroup;
    private MemberAdapter memberAdapter;
    private List<User> searchedUsers;
    private RegisterDatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        editTextGroupName = findViewById(R.id.editTextGroupName);
        editTextMemberId = findViewById(R.id.editTextMemberId);
        buttonSearchMember = findViewById(R.id.buttonSearchMember);
        memberRecyclerView = findViewById(R.id.memberRecyclerView);
        buttonCreateGroup = findViewById(R.id.buttonCreateGroup);

        dbHelper = new RegisterDatabaseHelper(this);
        searchedUsers = new ArrayList<>();
        memberAdapter = new MemberAdapter(searchedUsers);
        memberRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        memberRecyclerView.setAdapter(memberAdapter);

        if (dbHelper == null) {
            dbHelper = new RegisterDatabaseHelper(this);
        }

        buttonSearchMember.setOnClickListener(v -> {
            String memberId = editTextMemberId.getText().toString().trim();
            if (memberId.isEmpty()) {
                Toast.makeText(this, "請輸入成員 ID", Toast.LENGTH_SHORT).show();
                return;
            }

            if (searchedUsers.size() >= 30) {
                Toast.makeText(this, "群組成員最多 30 人", Toast.LENGTH_SHORT).show();
                return;
            }

            for (User user : searchedUsers) {
                if (user.getId().equals(memberId)) {
                    Toast.makeText(this, "該成員已添加", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            User user = getUserById(memberId);
            if (user != null) {
                searchedUsers.add(user);
                memberAdapter.notifyItemInserted(searchedUsers.size() - 1);
                editTextMemberId.setText("");
                Log.d("CreateGroupActivity", "Added user: " + user.getId() + ", " + user.getUsername());
            } else {
                Toast.makeText(this, "沒有該使用者，請確認使用者 ID", Toast.LENGTH_SHORT).show();
            }
        });

        buttonCreateGroup.setOnClickListener(v -> {
            String groupName = editTextGroupName.getText().toString().trim();
            if (groupName.isEmpty()) {
                Toast.makeText(this, "請輸入群組名稱", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> selectedMembers = memberAdapter.getSelectedMembers();
            if (selectedMembers.isEmpty()) {
                Toast.makeText(this, "請至少選擇一名成員", Toast.LENGTH_SHORT).show();
                return;
            }

            String currentUserId = sharedPreferences.getString("userId", null);
            if (currentUserId == null) {
                Toast.makeText(this, "請先登錄", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, PersonalAccount.class));
                finish();
                return;
            }

            if (!selectedMembers.contains(currentUserId)) {
                selectedMembers.add(currentUserId);
            }

            // 調用 createGroup 方法來處理群組創建和邀請插入
            createGroup(groupName, selectedMembers);

            // 同步邀請到伺服器
            CountDownLatch latch = new CountDownLatch(selectedMembers.size());
            for (String member : selectedMembers) {
                Log.d("CreateGroupActivity", "Attempting to add invitation for user: " + member + " to group: " + groupName);
                dbHelper.addGroupInvitation(groupName, member, new RegisterDatabaseHelper.SyncCallback() {
                    @Override
                    public void onSyncComplete(boolean success) {
                        if (success) {
                            Log.d("CreateGroupActivity", "Invitation synced for user: " + member);
                        } else {
                            Log.e("CreateGroupActivity", "Invitation sync failed for user: " + member);
                        }
                        latch.countDown();
                    }
                });
            }

            new Thread(() -> {
                try {
                    latch.await();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "群組創建成功，已發送邀請", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(CreateGroupActivity.this, Chatroom.class);
                        intent.putExtra("groupName", groupName);
                        intent.putStringArrayListExtra("members", new ArrayList<>(selectedMembers));
                        startActivity(intent);
                        finish();
                    });
                } catch (InterruptedException e) {
                    Log.e("CreateGroupActivity", "Interrupted while waiting for invitations: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "群組創建失敗", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
    }

    public void createGroup(String groupName, List<String> memberUserIds) {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();

        // 獲取當前用戶作為建立者
        String creatorId = sharedPreferences.getString("userId", null);
        if (creatorId == null) {
            Log.e("CreateGroupActivity", "Creator ID is null, redirecting to login");
            runOnUiThread(() -> {
                Toast.makeText(this, "請先登錄", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, PersonalAccount.class));
                finish();
            });
            return;
        }

        // 為建立者插入 accepted 邀請
        String invitationId = dbHelper.generateRandomId();
        ContentValues creatorValues = new ContentValues();
        creatorValues.put(COL_INVITATION_ID, invitationId);
        creatorValues.put(COL_GROUP_NAME, groupName);
        creatorValues.put(COL_INVITED_USER, creatorId);
        creatorValues.put(COL_STATUS, "accepted");
        creatorValues.put(COL_IS_SYNCED_INV, 0);
        long result = db.insert(TABLE_INVITATIONS, null, creatorValues);
        if (result == -1) {
            Log.e("CreateGroupActivity", "Failed to insert creator invitation for group: " + groupName);
            runOnUiThread(() -> Toast.makeText(this, "無法為創建者插入邀請", Toast.LENGTH_SHORT).show());
            return;
        } else {
            Log.d("CreateGroupActivity", "Inserted creator invitation for group: " + groupName + ", invitationId: " + invitationId);
        }

        // 為其他成員添加 pending 邀請
        for (String memberId : memberUserIds) {
            if (!memberId.equals(creatorId)) {
                String memberInvitationId = dbHelper.generateRandomId();
                ContentValues memberValues = new ContentValues();
                memberValues.put(COL_INVITATION_ID, memberInvitationId);
                memberValues.put(COL_GROUP_NAME, groupName);
                memberValues.put(COL_INVITED_USER, memberId);
                memberValues.put(COL_STATUS, "pending");
                memberValues.put(COL_IS_SYNCED_INV, 0);
                result = db.insert(TABLE_INVITATIONS, null, memberValues);
                if (result == -1) {
                    Log.e("CreateGroupActivity", "Failed to insert invitation for member: " + memberId);
                } else {
                    Log.d("CreateGroupActivity", "Inserted invitation for member: " + memberId + ", group: " + groupName);
                }
            }
        }

        // 更新 SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(groupName + "_creator", creatorId);
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        groupNames.add(groupName);
        editor.putStringSet("groupNames", groupNames);
        editor.putString(groupName + "_members", String.join(",", memberUserIds));
        editor.apply();

        Log.d("CreateGroupActivity", "Saved group: " + groupName + " with members: " + String.join(", ", memberUserIds));
    }

    private User getUserById(String userId) {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_NAME,
                new String[]{RegisterDatabaseHelper.COL_ID, RegisterDatabaseHelper.COL_USERNAME},
                RegisterDatabaseHelper.COL_ID + "=?",
                new String[]{userId},
                null, null, null);

        User user = null;
        if (cursor.moveToFirst()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_ID));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_USERNAME));
            user = new User(id, username);
            Log.d("CreateGroupActivity", "Found user: " + id + ", " + username);
        } else {
            Log.e("CreateGroupActivity", "No user found with ID: " + userId);
        }
        cursor.close();
        return user;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.closeDatabase();
            dbHelper = null;
        }
    }
}