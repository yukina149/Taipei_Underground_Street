package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        // 初始化 drawerLayout 和 navigationView
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("taipei underground");  // 你想顯示的標題文字
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // 使用 ActionBarDrawerToggle 連結 DrawerLayout 與 Toolbar
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (drawerLayout == null || navigationView == null) {
            Toast.makeText(this, "Navigation setup failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 使用基底類方法設置 Drawer 和 Toggle
        setupDrawer();
        updateNavigationMenu(); // 初始加載選單

        // 初始化 UI 元素
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

        // 接收群組資訊
        Intent intent = getIntent();
        groupName = intent.getStringExtra("groupName");
        members = intent.getStringArrayListExtra("members");

        if (groupName == null || members == null) {
            Toast.makeText(this, "Failed to load group info", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 顯示群組名稱和成員
        if (textViewGroupName != null) {
            textViewGroupName.setText("Group: " + groupName);
        }
        if (textViewMembers != null) {
            textViewMembers.setText("Members: " + String.join(", ", members));
        }

        // 初始化訊息列表和適配器
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setAdapter(messageAdapter);

        // 顯示歡迎訊息
        String username = sharedPreferences.getString("loggedInUser", "User");
        Toast.makeText(this, "Welcome to " + groupName + ", " + username + "!", Toast.LENGTH_SHORT).show();

        // 發送按鈕點擊事件
        buttonSend.setOnClickListener(v -> {
            String message = editTextMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                messageList.add(username + ": " + message);
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                messageRecyclerView.scrollToPosition(messageList.size() - 1);
                editTextMessage.setText("");
            }
        });

        // 設置 Navigation Item 點擊事件
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
                // 處理動態添加的群組選項
                String groupName = item.getTitle().toString();
                Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
                if (groupNames.contains(groupName)) {
                    String membersString = sharedPreferences.getString(groupName + "_members", "");
                    List<String> membersList = new ArrayList<>();
                    if (!membersString.isEmpty()) {
                        String[] membersArray = membersString.split(",");
                        for (String member : membersArray) {
                            membersList.add(member);
                        }
                    }
                    navigationIntent = new Intent(Chatroom.this, Chatroom.class);
                    navigationIntent.putExtra("groupName", groupName);
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

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回 Chatroom 時更新選單
        updateNavigationMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle != null && toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}