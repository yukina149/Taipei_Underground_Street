package com.example.cameraproject_2;

import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_GROUP_NAME;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_INVITATION_ID;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_INVITED_USER;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_IS_SYNCED_INV;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_STATUS;
import static com.example.cameraproject_2.RegisterDatabaseHelper.TABLE_INVITATIONS;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Chatroom extends AppCompatActivity {
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
    private String lastMessageId = "0";
    private ExecutorService executorService;
    private volatile boolean isPollingActive = true;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

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

        // 檢查所有 UI 組件
        if (messageRecyclerView == null || editTextMessage == null || buttonSend == null ||
                textViewGroupName == null || textViewMembers == null) {
            Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            Log.e("Chatroom", "One or more UI components are null: recyclerView=" + (messageRecyclerView != null) +
                    ", editText=" + (editTextMessage != null) + ", button=" + (buttonSend != null) +
                    ", groupName=" + (textViewGroupName != null) + ", members=" + (textViewMembers != null));
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
                        members.add(member.trim());
                    }
                }
            }
            if (groupName == null || members.isEmpty()) {
                Toast.makeText(this, "Failed to load group info", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        dbHelper = new RegisterDatabaseHelper(this);
        if (dbHelper == null) {
            Toast.makeText(this, "Database helper initialization failed", Toast.LENGTH_SHORT).show();
            Log.e("Chatroom", "dbHelper is null");
            finish();
            return;
        }

        currentUserId = sharedPreferences.getString("userId", null);
        if (currentUserId == null) {
            Log.e("Chatroom", "userId is null, checking login status");
            currentUserId = "1"; // 臨時值，應從登錄流程獲取
        }
        currentUsername = sharedPreferences.getString("loggedInUser", "Unknown");
        if ("Unknown".equals(currentUsername)) {
            currentUsername = dbHelper.getUsernameFromUserId(currentUserId);
            if (currentUsername != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("loggedInUser", currentUsername);
                editor.apply();
                Log.d("Chatroom", "Updated currentUsername from DB: " + currentUsername);
            }
        }
        Log.d("Chatroom", "Loaded userId: " + currentUserId + ", username: " + currentUsername);
        executorService = Executors.newSingleThreadExecutor();

        messageList = new ArrayList<>();
        loadMessagesFromDatabase();
        messageAdapter = new MessageAdapter(messageList);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setAdapter(messageAdapter);
        messageRecyclerView.scrollToPosition(messageList.size() - 1);

        if (textViewGroupName != null) textViewGroupName.setText("Group: " + groupName);
        if (textViewMembers != null) textViewMembers.setText("Members: " + String.join(", ", members));

        Toast.makeText(this, "Welcome to " + groupName + ", " + currentUsername + "!", Toast.LENGTH_SHORT).show();

        checkInvitationStatus();
        buttonSend.setOnClickListener(v -> onSendButtonClick(v));
        startMessagePolling();

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
                            membersList.add(member.trim());
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

    private void checkInvitationStatus() {

        dbHelper.syncInvitations(this, new RegisterDatabaseHelper.SyncCallback() {
            @Override
            public void onSyncComplete(boolean success) {
                runOnUiThread(() -> {
                    if (!success) {
                        Toast.makeText(Chatroom.this, "Failed to sync invitations", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Invitation> pendingInvitations = dbHelper.getPendingInvitations(currentUserId);
                    hasAcceptedInvitation = true;

                    if (members != null && !members.isEmpty() && members.get(0).equals(currentUsername)) {
                        hasAcceptedInvitation = true;
                        Log.d("Chatroom", "User is group creator, auto-accepted");
                    } else {
                        for (Invitation invitation : pendingInvitations) {
                            if ("pending".equals(invitation.getStatus())) {
                                hasAcceptedInvitation = false;
                                showInvitationDialog(invitation);
                                break;
                            }
                        }
                    }

                    if (hasAcceptedInvitation) {
                        SQLiteDatabase db = dbHelper.getRegisterDatabase();
                        Cursor cursor = db.query(TABLE_INVITATIONS,
                                new String[]{COL_STATUS},
                                COL_INVITED_USER + "=? AND " + COL_GROUP_NAME + "=?",
                                new String[]{currentUsername, groupName},
                                null, null, null);
                        if (cursor.moveToFirst()) {
                            String status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));
                            hasAcceptedInvitation = "accepted".equals(status);
                        } else {
                            hasAcceptedInvitation = members.contains(currentUsername);
                        }
                        cursor.close();
                    }

                    Log.d("Chatroom", "User: " + currentUsername + ", Group: " + groupName + ", hasAcceptedInvitation: " + hasAcceptedInvitation);
                    updateUI();
                    updateNavigationMenu();
                });
            }
        });
    }

    private BroadcastReceiver invitationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.cameraproject_2.INVITATION_UPDATED".equals(intent.getAction())) {
                checkInvitationStatus();
            }
        }
    };

    private void showInvitationDialog(Invitation invitation) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("群組邀請")
                .setMessage("您已被邀請加入群組: " + invitation.getGroupName())
                .setPositiveButton("接受", (dialog, which) -> {
                    dbHelper.updateInvitationStatus(invitation.getInvitationId(), "accepted");
                    checkInvitationStatus();
                    dialog.dismiss();
                    Intent intent = new Intent(Chatroom.this, Chatroom.class);
                    intent.putExtra("groupName", invitation.getGroupName());
                    String membersString = sharedPreferences.getString(invitation.getGroupName() + "_members", "");
                    List<String> membersList = new ArrayList<>();
                    if (!membersString.isEmpty()) {
                        String[] membersArray = membersString.split(",");
                        for (String member : membersArray) {
                            membersList.add(member.trim());
                        }
                    }
                    intent.putStringArrayListExtra("members", new ArrayList<>(membersList));
                    startActivity(intent);
                })
                .setNegativeButton("拒絕", (dialog, which) -> {
                    dbHelper.updateInvitationStatus(invitation.getInvitationId(), "rejected");
                    checkInvitationStatus();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    public void onSendButtonClick(View view) {
        EditText editTextMessage = findViewById(R.id.editTextMessage);
        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        sendMessageToServer(groupName, currentUsername, message);
        editTextMessage.setText("");
    }

    private void sendMessageToServer(String groupName, String sender, String message) {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("group_name", groupName);
                jsonBody.put("sender", sender);
                jsonBody.put("message", message);

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/send_message.php")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseData = response.body() != null ? response.body().string() : "";
                        if (!responseData.isEmpty()) {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                                String messageId = jsonResponse.has("message_id") ? jsonResponse.getString("message_id") : "";
                                if (!messageId.isEmpty()) {
                                    Log.d("Chatroom", "Message sent successfully, ID: " + messageId);
                                    if (dbHelper != null) {
                                        dbHelper.insertMessage(messageId, groupName, sender, message, System.currentTimeMillis());
                                    }
                                    // 即時加載所有訊息
                                    runOnUiThread(this::loadMessagesFromDatabase);
                                } else {
                                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "No message ID received", Toast.LENGTH_SHORT).show());
                                    Log.e("Chatroom", "No message_id in response");
                                }
                            } else {
                                String errorMessage = jsonResponse.has("message") ? jsonResponse.getString("message") : "Unknown error";
                                runOnUiThread(() -> Toast.makeText(Chatroom.this, "Failed to send message: " + errorMessage, Toast.LENGTH_SHORT).show());
                                Log.e("Chatroom", "Server reported failure: " + errorMessage);
                            }
                        } else {
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "Empty server response", Toast.LENGTH_SHORT).show());
                            Log.e("Chatroom", "Empty response from server");
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(Chatroom.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                        Log.e("Chatroom", "Server error: " + response.code());
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                Log.e("Chatroom", "Error sending message: " + e.getMessage());
            }
        });
    }

    private void updateMessageList(String message) {
        if (messageList != null && messageAdapter != null && messageRecyclerView != null) {
            messageList.add(message);
            messageAdapter.notifyItemInserted(messageList.size() - 1);
            messageRecyclerView.scrollToPosition(messageList.size() - 1);
            Log.d("Chatroom", "Message added to UI: " + message);
        } else {
            Log.e("Chatroom", "UI components are null, cannot update");
        }
    }

    private void startMessagePolling() {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS) // 增加超時時間
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            while (isPollingActive) {
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/fetch_messages.php?group_name=" + Uri.encode(groupName) + "&last_message_id=" + lastMessageId)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseData = response.body() != null ? response.body().string() : "";
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            if (jsonResponse.getBoolean("success")) {
                                JSONArray data = jsonResponse.getJSONArray("data");
                                boolean updated = false;
                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject messageObj = data.getJSONObject(i);
                                    String messageId = messageObj.getString("message_id");
                                    String sender = messageObj.has("sender") ? messageObj.getString("sender") : "Unknown";
                                    String messageText = messageObj.has("message") ? messageObj.getString("message") : "";
                                    long timestamp = messageObj.has("timestamp") ? messageObj.getLong("timestamp") : System.currentTimeMillis();

                                    if (!messageId.equals(lastMessageId)) {
                                        String fullMessage = sender + ": " + messageText + " (" + formatTimestamp(timestamp) + ")";
                                        runOnUiThread(() -> {
                                            if (!messageList.contains(fullMessage)) { // 避免重複添加
                                                updateMessageList(fullMessage);
                                            }
                                        });
                                        lastMessageId = messageId;
                                        updated = true;
                                    }
                                }
                                if (!updated && data.length() > 0) {
                                    runOnUiThread(this::loadMessagesFromDatabase); // 確保顯示所有訊息
                                }
                            }
                        } catch (JSONException e) {
                            Log.e("Chatroom", "JSON parse error polling messages: " + e.getMessage() + ", Response: " + responseData);
                            runOnUiThread(this::loadMessagesFromDatabase);
                        }
                    } else {
                        Log.e("Chatroom", "Failed to poll messages: " + response.code() + ", Response: " + (response.body() != null ? response.body().string() : "No body"));
                        runOnUiThread(this::loadMessagesFromDatabase);
                    }
                } catch (IOException e) {
                    Log.e("Chatroom", "IO error polling messages: " + e.getMessage());
                    runOnUiThread(this::loadMessagesFromDatabase);
                }

                try {
                    Thread.sleep(2000); // 縮短為 2 秒間隔
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private void loadMessagesFromDatabase() {
        if (dbHelper == null || messageList == null || messageAdapter == null || messageRecyclerView == null) {
            Log.e("Chatroom", "UI components or dbHelper are null, skipping load");
            return;
        }

        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_MESSAGES,
                new String[]{RegisterDatabaseHelper.COL_MESSAGE_ID, RegisterDatabaseHelper.COL_SENDER,
                        RegisterDatabaseHelper.COL_MESSAGE, RegisterDatabaseHelper.COL_TIMESTAMP},
                COL_GROUP_NAME + "=?",
                new String[]{groupName},
                null, null, RegisterDatabaseHelper.COL_TIMESTAMP + " DESC");

        List<String> newMessages = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
                String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
                String fullMessage = sender + ": " + message + " (" + formatTimestamp(timestamp) + ")";
                newMessages.add(fullMessage);
                Log.d("Chatroom", "Loaded message from DB: " + fullMessage);
            } while (cursor.moveToNext());
        }
        cursor.close();

        runOnUiThread(() -> {
            messageList.clear();
            messageList.addAll(newMessages);
            messageAdapter.notifyDataSetChanged();
            messageRecyclerView.scrollToPosition(messageList.size() - 1);
        });
    }

    public void onInvitationAccepted(String invitationId) {
        dbHelper.updateInvitationStatus(invitationId, "accepted");
        checkInvitationStatus();

        runOnUiThread(() -> {
            String groupName = getGroupNameFromInvitation(invitationId);
            if (groupName != null) {
                String membersString = sharedPreferences.getString(groupName + "_members", "");
                List<String> members = new ArrayList<>();
                if (!membersString.isEmpty()) {
                    String[] membersArray = membersString.split(",");
                    for (String member : membersArray) {
                        members.add(member.trim());
                    }
                }
                Intent intent = new Intent(Chatroom.this, Chatroom.class);
                intent.putExtra("groupName", groupName);
                intent.putStringArrayListExtra("members", new ArrayList<>(members));
                startActivity(intent);
                finish();
            } else {
                Log.e("Chatroom", "Failed to get group name for invitation ID: " + invitationId);
                Toast.makeText(Chatroom.this, "無法找到群組", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getGroupNameFromInvitation(String invitationId) {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                new String[]{RegisterDatabaseHelper.COL_GROUP_NAME},
                RegisterDatabaseHelper.COL_INVITATION_ID + " = ?",
                new String[]{invitationId},
                null, null, null);
        String groupName = null;
        if (cursor.moveToFirst()) {
            groupName = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_GROUP_NAME));
        }
        cursor.close();
        return groupName;
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        updateNavigationMenu();
        checkInvitationStatus();
        isPollingActive = true;
        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPollingActive = false;
        unregisterReceiver(invitationReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPollingActive = false;
        executorService.shutdown();
        if (dbHelper != null) {
            dbHelper.closeDatabase();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle != null && toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupDrawer() {
        // 實現抽屜菜單的設置邏輯
    }

    private void updateNavigationMenu() {
        if (navigationView == null) return;

        Menu menu = navigationView.getMenu();
        menu.clear();

        menu.add("Chat Room").setIcon(R.drawable.chat_icon).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(Chatroom.this, MainActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        menu.add("Create Group").setIcon(R.drawable.chat_add_icon).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(Chatroom.this, CreateGroupActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        menu.add("Logout").setIcon(R.drawable.login_icon).setOnMenuItemClickListener(item -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isLoggedIn", false);
            editor.putString("loggedInUser", "訪客");
            editor.putString("userId", "訪客");
            editor.apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Chatroom.this, PersonalAccount.class));
            finish();
            return true;
        });

        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        if (groupNames != null) {
            for (String groupName : groupNames) {
                menu.add(groupName).setIcon(R.drawable.ic_go).setOnMenuItemClickListener(item -> {
                    String membersString = sharedPreferences.getString(groupName + "_members", "");
                    List<String> membersList = new ArrayList<>();
                    if (!membersString.isEmpty()) {
                        String[] membersArray = membersString.split(",");
                        for (String member : membersArray) {
                            membersList.add(member.trim());
                        }
                    }
                    Intent intent = new Intent(Chatroom.this, Chatroom.class);
                    intent.putExtra("groupName", groupName);
                    intent.putStringArrayListExtra("members", new ArrayList<>(membersList));
                    startActivity(intent);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                });
            }
        }
        Log.d("Chatroom", "Navigation menu updated with groups: " + groupNames);
    }
}