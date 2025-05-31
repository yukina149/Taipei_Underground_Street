package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Chatroom extends BaseActivity {

    private RecyclerView messageRecyclerView;
    private EditText editTextMessage;
    private Button buttonSend;
    private TextView textViewGroupName;
    private TextView textViewMembers;
    private List<String> messageList;
    private MessageAdapter messageAdapter;
    private String groupName;
    private ArrayList<String> members;
    private boolean hasAcceptedInvitation;
    private RegisterDatabaseHelper dbHelper;
    private String currentUserId;
    private String currentUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("taipei underground");
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (drawerLayout == null || navigationView == null) {
            Toast.makeText(this, "Navigation setup failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupDrawer();
        updateNavigationMenu();

        messageRecyclerView = findViewById(R.id.messageRecyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        textViewGroupName = findViewById(R.id.textViewGroupName);
        textViewMembers = findViewById(R.id.textViewMembers);

        if (messageRecyclerView == null || editTextMessage == null || buttonSend == null) {
            Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        groupName = intent.getStringExtra("groupName");
        members = intent.getStringArrayListExtra("members");

        if (groupName == null || members == null) {
            groupName = intent.getStringExtra("groupName");
            if (groupName != null) {
                String membersString = sharedPreferences.getString(groupName + "_members", "");
                members = new ArrayList<>();
                if (!membersString.isEmpty()) {
                    String[] membersArray = membersString.split(",");
                    for (String member : membersArray) {
                        members.add(member);
                    }
                }
            }
            if (groupName == null || members.isEmpty()) {
                Toast.makeText(this, "Failed to load group info", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // 初始化資料庫和用戶資訊
        dbHelper = new RegisterDatabaseHelper(this);
        currentUserId = sharedPreferences.getString("userId", "Unknown");
        currentUsername = sharedPreferences.getString("loggedInUser", "Unknown");

        // 檢查邀請狀態
        checkInvitationStatus();

        if (textViewGroupName != null) {
            textViewGroupName.setText("Group: " + groupName);
        }
        if (textViewMembers != null) {
            textViewMembers.setText("Members: " + String.join(", ", members));
        }

        messageList = new ArrayList<>();
        Set<String> savedMessages = sharedPreferences.getStringSet(groupName + "_messages", new HashSet<>());
        messageList.addAll(savedMessages);
        messageAdapter = new MessageAdapter(messageList);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setAdapter(messageAdapter);
        messageRecyclerView.scrollToPosition(messageList.size() - 1);

        Toast.makeText(this, "Welcome to " + groupName + ", " + currentUsername + "!", Toast.LENGTH_SHORT).show();

        if (!hasAcceptedInvitation) {
            buttonSend.setEnabled(false);
            editTextMessage.setEnabled(false);
            editTextMessage.setHint("請先接受群組邀請才能發送訊息");
        } else {
            buttonSend.setEnabled(true);
            editTextMessage.setEnabled(true);
            editTextMessage.setHint("輸入訊息...");
        }

        buttonSend.setOnClickListener(v -> {
            if (!hasAcceptedInvitation) {
                Toast.makeText(this, "請先接受群組邀請", Toast.LENGTH_SHORT).show();
                return;
            }

            String message = editTextMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                String fullMessage = currentUsername + ": " + message;
                messageList.add(fullMessage);
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                messageRecyclerView.scrollToPosition(messageList.size() - 1);
                editTextMessage.setText("");

                Set<String> updatedMessages = new HashSet<>(messageList);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putStringSet(groupName + "_messages", updatedMessages);
                editor.apply();
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent navigationIntent = null;

            if (id == R.id.nav_logout) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("isLoggedIn", false);
                editor.putString("loggedInUser", "訪客");
                editor.putString("userId", "訪客");
                editor.apply();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                navigationIntent = new Intent(Chatroom.this, PersonalAccount.class);
                startActivity(navigationIntent);
                finish();
            } else if (id == R.id.Chat_room) {
                navigationIntent = new Intent(Chatroom.this, MainActivity.class);
                startActivity(navigationIntent);
            } else if (id == R.id.Create_Group) {
                navigationIntent = new Intent(Chatroom.this, CreateGroupActivity.class);
                startActivity(navigationIntent);
            } else {
                String selectedGroupName = item.getTitle().toString();
                Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
                if (groupNames.contains(selectedGroupName)) {
                    String membersString = sharedPreferences.getString(selectedGroupName + "_members", "");
                    List<String> membersList = new ArrayList<>();
                    if (!membersString.isEmpty()) {
                        String[] membersArray = membersString.split(",");
                        for (String member : membersArray) {
                            membersList.add(member);
                        }
                    }
                    navigationIntent = new Intent(Chatroom.this, Chatroom.class);
                    navigationIntent.putExtra("groupName", selectedGroupName);
                    navigationIntent.putStringArrayListExtra("members", new ArrayList<>(membersList));
                    startActivity(navigationIntent);
                }
            }

            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }

    // 檢查邀請狀態
    private void checkInvitationStatus() {
        // 同步邀請資料
        dbHelper.syncInvitations();

        // 查詢當前用戶的所有邀請
        List<Invitation> pendingInvitations = dbHelper.getPendingInvitations(currentUserId);
        hasAcceptedInvitation = true; // 預設為已接受

        // 如果有 pending 邀請，則未接受
        for (Invitation invitation : pendingInvitations) {
            if (invitation.getGroupName().equals(groupName)) {
                hasAcceptedInvitation = false;
                break;
            }
        }

        // 如果沒有 pending 邀請，進一步檢查是否已接受
        if (hasAcceptedInvitation) {
            SQLiteDatabase db = dbHelper.getRegisterDatabase();
            Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                    new String[]{RegisterDatabaseHelper.COL_STATUS},
                    RegisterDatabaseHelper.COL_INVITED_USER + "=? AND " + RegisterDatabaseHelper.COL_GROUP_NAME + "=?",
                    new String[]{currentUsername, groupName},
                    null, null, null);

            if (cursor.moveToFirst()) {
                String status = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_STATUS));
                hasAcceptedInvitation = "accepted".equals(status);
            } else {
                // 如果沒有邀請記錄，檢查是否在成員名單中
                hasAcceptedInvitation = members.contains(currentUsername);
            }
            cursor.close();
        }

        Log.d("Chatroom", "User: " + currentUsername + ", Group: " + groupName + ", hasAcceptedInvitation: " + hasAcceptedInvitation);
        updateUI();
    }

    // 更新 UI 狀態
    private void updateUI() {
        if (hasAcceptedInvitation) {
            buttonSend.setEnabled(true);
            editTextMessage.setEnabled(true);
            editTextMessage.setHint("輸入訊息...");
        } else {
            buttonSend.setEnabled(false);
            editTextMessage.setEnabled(false);
            editTextMessage.setHint("請先接受群組邀請才能發送訊息");
        }
    }

    // 假設有一個方法在接受邀請後被呼叫
    public void onInvitationAccepted(String invitationId) {
        dbHelper.updateInvitationStatus(invitationId, "accepted");
        checkInvitationStatus(); // 重新檢查狀態並更新 UI
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavigationMenu();
        checkInvitationStatus(); // 每次恢復時重新檢查
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle != null && toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.closeDatabase();
        }
    }
}