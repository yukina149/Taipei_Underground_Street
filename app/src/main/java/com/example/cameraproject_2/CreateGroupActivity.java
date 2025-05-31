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

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;

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

            for (String member : selectedMembers) {
                if (!member.equals(currentUser)) {
                    Log.d("CreateGroupActivity", "Attempting to add invitation for user: " + member + " to group: " + groupName);
                    dbHelper.addGroupInvitation(groupName, member);
                    Log.d("CreateGroupActivity", "Invitation added for user: " + member + ", will be synced");
                }
            }

            Toast.makeText(this, "群組創建成功，已發送邀請", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(CreateGroupActivity.this, Chatroom.class);
            intent.putExtra("groupName", groupName);
            intent.putStringArrayListExtra("members", new ArrayList<>(selectedMembers));
            startActivity(intent);
            finish();
        });
    }

    private void saveGroup(String groupName, List<String> members) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        Set<String> newGroupNames = new HashSet<>(groupNames);
        if (!newGroupNames.contains(groupName)) {
            newGroupNames.add(groupName);
            editor.putStringSet("groupNames", newGroupNames);
            Log.d("CreateGroupActivity", "Added group: " + groupName);
        } else {
            Log.d("CreateGroupActivity", "Group " + groupName + " already exists");
        }

        String membersString = String.join(",", members);
        editor.putString(groupName + "_members", membersString);
        editor.apply();
        Log.d("CreateGroupActivity", "Saved members for " + groupName + ": " + membersString);

        // 上傳群組資料到伺服器
        new Thread(() -> {
            try {
                uploadGroupToServer(groupName, membersString);
                Log.d("CreateGroupActivity", "Group uploaded to server: " + groupName);
            } catch (Exception e) {
                Log.e("CreateGroupActivity", "Failed to upload group to server: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "無法上傳群組到伺服器", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    private void uploadGroupToServer(String groupName, String members) throws IOException, JSONException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("group_name", groupName);
        jsonBody.put("members", members);

        String url = "http://192.168.10.15/android_studio/create_group.php";
        RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body().string();
            Log.d("CreateGroupActivity", "Server response: " + responseData);
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upload group: " + response.code() + " - " + response.message());
            }

            JSONObject jsonResponse = new JSONObject(responseData);
            if (!jsonResponse.getBoolean("success")) {
                throw new IOException(jsonResponse.getString("message"));
            }
        }
    }

    private User getUserById(String memberId) {
        Cursor cursor = dbHelper.getRegisterDatabase().query(
                RegisterDatabaseHelper.TABLE_NAME,
                new String[]{"id", RegisterDatabaseHelper.COL_USERNAME},
                "id = ?",
                new String[]{memberId},
                null, null, null
        );

        User user = null;
        if (cursor.moveToFirst()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_USERNAME));
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