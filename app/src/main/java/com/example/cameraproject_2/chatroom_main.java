package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class chatroom_main extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private BottomNavigationView bottomNavigationView;
    private RecyclerView recyclerViewGroups;
    private GroupAdapter groupAdapter;
    private RegisterDatabaseHelper dbHelper;
    private String currentUserId;
    private EditText editTextSearch;
    private List<Group> originalGroupList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chatroom_main);

        // Initialize SharedPreferences and DatabaseHelper
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        dbHelper = new RegisterDatabaseHelper(this);
        currentUserId = sharedPreferences.getString("userId", null);

        // Set back arrow click listener
        ImageView backArrow = findViewById(R.id.backArrow);
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(chatroom_main.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
            finish();
        });

        // Initialize RecyclerView
        recyclerViewGroups = findViewById(R.id.recycler_view_groups);
        recyclerViewGroups.setLayoutManager(new LinearLayoutManager(this));
        originalGroupList = loadGroupList();
        groupAdapter = new GroupAdapter(originalGroupList, group -> {
            Intent intent = new Intent(chatroom_main.this, Chatroom.class);
            intent.putExtra("groupName", group.getGroupName());
            ArrayList<String> members = new ArrayList<>(group.getMembers());
            intent.putStringArrayListExtra("members", members);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
        });
        recyclerViewGroups.setAdapter(groupAdapter);

        // Initialize search edit text
        editTextSearch = findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterGroups(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set message container click listener
        FrameLayout messageContainer = findViewById(R.id.message_container);
        messageContainer.setOnClickListener(v -> {
            Intent intent = new Intent(chatroom_main.this, receive_group.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
        });

        // Update empty state visibility
        updateEmptyStateVisibility(originalGroupList);

        // Set BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.chat);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.homefill) {
                Intent intent = new Intent(chatroom_main.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
                finish();
                return true;
            } else if (id == R.id.chat) {
                Toast.makeText(this, R.string.home_page, Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_member) {
                boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
                Intent intent = new Intent(chatroom_main.this, isLoggedIn ? UserProfileActivity.class : PersonalAccount.class);
                intent.putExtra("isLoggedIn", isLoggedIn);
                intent.putExtra("userId", sharedPreferences.getString("userId", "訪客"));
                intent.putExtra("loggedInUser", sharedPreferences.getString("loggedInUser", "訪客"));
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                return true;
            } else if (id == R.id.nav_info) {
                Toast.makeText(this, R.string.taipei_info, Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(chatroom_main.this, SettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                return true;
            }
            return false;
        });

        // Add click listener for fab_container
        FrameLayout fabContainer = findViewById(R.id.fab_container);
        fabContainer.setOnClickListener(v -> {
            Intent intent = new Intent(chatroom_main.this, CreateGroupActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload group list on resume
        originalGroupList = loadGroupList();
        groupAdapter.updateGroups(originalGroupList);
        updateEmptyStateVisibility(originalGroupList);
        filterGroups(editTextSearch.getText().toString()); // Apply current filter
    }

    private List<Group> loadGroupList() {
        List<Group> groupList = new ArrayList<>();
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Log.e("chatroom_main", "No userId found, cannot load groups");
            return groupList;
        }

        // Load user's groups from database
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                new String[]{RegisterDatabaseHelper.COL_GROUP_NAME, RegisterDatabaseHelper.COL_INVITED_USER},
                RegisterDatabaseHelper.COL_INVITED_USER + "=? AND " + RegisterDatabaseHelper.COL_STATUS + "=?",
                new String[]{currentUserId, "accepted"},
                null, null, null);

        while (cursor.moveToNext()) {
            String groupName = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_GROUP_NAME));
            String creatorId = getGroupCreatorId(groupName);
            String creatorAvatarUrl = dbHelper.getUserAvatarUrl(creatorId);
            List<String> members = dbHelper.getGroupMembers(groupName);
            Group group = new Group(groupName, creatorId, creatorAvatarUrl, members);

            // Get latest message and time
            String lastMessage = dbHelper.getLastMessage(groupName);
            String lastMessageTime = dbHelper.getLastMessageTime(groupName);
            group.setLastMessage(lastMessage);
            group.setLastMessageTime(lastMessageTime);

            groupList.add(group);
        }
        cursor.close();
        Log.d("chatroom_main", "Loaded " + groupList.size() + " groups for user: " + currentUserId);
        return groupList;
    }

    private String getGroupCreatorId(String groupName) {
        String creatorId = sharedPreferences.getString(groupName + "_creator", null);
        if (creatorId == null) {
            SQLiteDatabase db = dbHelper.getRegisterDatabase();
            Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                    new String[]{RegisterDatabaseHelper.COL_INVITED_USER},
                    RegisterDatabaseHelper.COL_GROUP_NAME + "=? AND " + RegisterDatabaseHelper.COL_STATUS + "=?",
                    new String[]{groupName, "accepted"},
                    null, null, RegisterDatabaseHelper.COL_INVITATION_ID + " ASC LIMIT 1");
            if (cursor.moveToFirst()) {
                creatorId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_INVITED_USER));
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(groupName + "_creator", creatorId);
                editor.apply();
            }
            cursor.close();
        }
        return creatorId;
    }

    private void updateEmptyStateVisibility(List<Group> groupList) {
        findViewById(R.id.text_view_empty).setVisibility(groupList.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.text_view_subtitle).setVisibility(groupList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerViewGroups.setVisibility(groupList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void filterGroups(String query) {
        if (originalGroupList == null) return;

        List<Group> filteredList = originalGroupList.stream()
                .filter(group -> group.getGroupName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());

        groupAdapter.updateGroups(filteredList);
        updateEmptyStateVisibility(filteredList);
    }
}