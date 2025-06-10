package com.example.cameraproject_2;

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

            String currentUser = sharedPreferences.getString("loggedInUser", "Unknown");
            if (!selectedMembers.contains(currentUser)) {
                selectedMembers.add(currentUser);
            }

            saveGroup(groupName, selectedMembers);

            CountDownLatch latch = new CountDownLatch(selectedMembers.size() - 1);
            for (String member : selectedMembers) {
                if (!member.equals(currentUser)) {
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
                } else {
                    latch.countDown();
                }
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

    public void onCreateGroupClick(View view) {
        EditText editTextGroupName = findViewById(R.id.editTextGroupName);
        String groupName = editTextGroupName.getText().toString().trim();
        if (groupName.isEmpty()) {
            Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedMembers = memberAdapter.getSelectedMembers();
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show();
            return;
        }

        dbHelper = new RegisterDatabaseHelper(this);
        for (String userId : selectedMembers) {
            dbHelper.addGroupInvitation(groupName, userId, new RegisterDatabaseHelper.SyncCallback() {
                @Override
                public void onSyncComplete(boolean success) {
                    if (success) {
                        Log.d("CreateGroupActivity", "Successfully added invitation for user: " + userId);
                    } else {
                        Log.e("CreateGroupActivity", "Invitation sync failed for user: " + userId);
                    }
                }
            });
        }

        // 保存群組和成員到 SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> groupNames = prefs.getStringSet("groupNames", new HashSet<>());
        groupNames.add(groupName);
        editor.putStringSet("groupNames", groupNames);
        editor.putString(groupName + "_members", String.join(",", selectedMembers));
        editor.apply();

        Log.d("CreateGroupActivity", "Saved group: " + groupName + " with members: " + String.join(", ", selectedMembers));
        Toast.makeText(this, "Group created: " + groupName, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveGroup(String groupName, List<String> members) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        groupNames.add(groupName);
        editor.putStringSet("groupNames", groupNames);
        editor.putString(groupName + "_members", String.join(",", members));
        editor.apply();
        Log.d("CreateGroupActivity", "Saved group: " + groupName + " with members: " + String.join(", ", members));
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
        }
    }
}