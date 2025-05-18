package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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

public class CreateGroupActivity extends AppCompatActivity {

    private EditText editTextGroupName;
    private EditText editTextMemberId;
    private Button buttonSearchMember;
    private RecyclerView memberRecyclerView;
    private Button buttonCreateGroup;
    private MemberAdapter memberAdapter;
    private List<User> searchedUsers;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        // 初始化 SharedPreferences，使用與 BaseActivity 一致的名稱
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 初始化 UI
        editTextGroupName = findViewById(R.id.editTextGroupName);
        editTextMemberId = findViewById(R.id.editTextMemberId);
        buttonSearchMember = findViewById(R.id.buttonSearchMember);
        memberRecyclerView = findViewById(R.id.memberRecyclerView);
        buttonCreateGroup = findViewById(R.id.buttonCreateGroup);

        // 初始化資料庫和成員列表
        dbHelper = new DatabaseHelper(this);
        searchedUsers = new ArrayList<>();
        memberAdapter = new MemberAdapter(searchedUsers);
        memberRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        memberRecyclerView.setAdapter(memberAdapter);

        // 搜索成員按鈕點擊事件
        buttonSearchMember.setOnClickListener(v -> {
            String memberId = editTextMemberId.getText().toString().trim();
            if (memberId.isEmpty()) {
                Toast.makeText(this, "請輸入成員 ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // 檢查是否已達到 30 人上限
            if (searchedUsers.size() >= 30) {
                Toast.makeText(this, "群組成員最多 30 人", Toast.LENGTH_SHORT).show();
                return;
            }

            // 檢查是否已添加該 ID
            for (User user : searchedUsers) {
                if (user.getId().equals(memberId)) {
                    Toast.makeText(this, "該成員已添加", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // 搜索使用者
            User user = getUserById(memberId);
            if (user != null) {
                searchedUsers.add(user);
                memberAdapter.notifyItemInserted(searchedUsers.size() - 1);
                editTextMemberId.setText(""); // 清空輸入框
                Log.d("CreateGroupActivity", "Added user: " + user.getId() + ", " + user.getUsername());
            } else {
                Toast.makeText(this, "沒有該使用者，請確認使用者 ID", Toast.LENGTH_SHORT).show();
            }
        });

        // 創建群組按鈕點擊事件
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

            // 保存群組資訊到 SharedPreferences
            saveGroup(groupName, selectedMembers);

            // 將群組資訊傳遞給 Chatroom
            Intent intent = new Intent(CreateGroupActivity.this, Chatroom.class);
            intent.putExtra("groupName", groupName);
            intent.putStringArrayListExtra("members", new ArrayList<>(selectedMembers));
            startActivity(intent);
            finish();
        });
    }

    private void saveGroup(String groupName, List<String> members) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // 獲取現有的群組名稱列表
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        Set<String> newGroupNames = new HashSet<>(groupNames);
        if (!newGroupNames.contains(groupName)) {
            newGroupNames.add(groupName);
            editor.putStringSet("groupNames", newGroupNames);
            Log.d("CreateGroupActivity", "Added group: " + groupName);
        } else {
            Log.d("CreateGroupActivity", "Group " + groupName + " already exists");
        }

        // 保存群組的成員列表（以 "groupName_members" 為鍵）
        editor.putString(groupName + "_members", String.join(",", members));
        editor.apply(); // 確保同步提交更改
        Log.d("CreateGroupActivity", "Saved members for " + groupName + ": " + String.join(",", members));
    }

    private User getUserById(String memberId) {
        Cursor cursor = dbHelper.getRegisterDatabase().query(
                DatabaseHelper.TABLE_NAME,
                new String[]{"id", DatabaseHelper.COL_USERNAME},
                "id = ?",
                new String[]{memberId},
                null, null, null
        );

        User user = null;
        if (cursor.moveToFirst()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USERNAME));
            user = new User(id, username);
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