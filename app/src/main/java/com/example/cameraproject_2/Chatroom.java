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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import java.util.concurrent.CountDownLatch;
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

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 初始化 UI 組件
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        messageRecyclerView = findViewById(R.id.messageRecyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        textViewGroupName = findViewById(R.id.textViewGroupName);
        textViewMembers = findViewById(R.id.textViewMembers);

        // 設置 Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("taipei underground");
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // 初始化 dbHelper
        dbHelper = new RegisterDatabaseHelper(this);

        // 檢查 UI 組件和 dbHelper 是否為 null
        if (drawerLayout == null || navigationView == null || toolbar == null ||
                messageRecyclerView == null || editTextMessage == null || buttonSend == null ||
                textViewGroupName == null || textViewMembers == null || dbHelper == null) {
            Log.e("Chatroom", "UI components or dbHelper are null, details - " +
                    "drawerLayout=" + (drawerLayout != null) +
                    ", navView=" + (navigationView != null) +
                    ", toolbar=" + (toolbar != null) +
                    ", recyclerView=" + (messageRecyclerView != null) +
                    ", editText=" + (editTextMessage != null) +
                    ", button=" + (buttonSend != null) +
                    ", groupName=" + (textViewGroupName != null) +
                    ", members=" + (textViewMembers != null) +
                    ", dbHelper=" + (dbHelper != null));
            Toast.makeText(this, "Initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 註冊廣播接收器
        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);

        // 設置 Drawer
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 後續初始化邏輯
        setupDrawer();
        updateNavigationMenu();
        checkInvitationStatus();

        // 設置 RecyclerView
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setAdapter(messageAdapter);

        // 處理 Intent 數據
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
                        String cleanMember = member.trim();
                        String userId = dbHelper.getUserIdFromUsername(cleanMember);
                        if (userId != null) members.add(userId);
                    }
                }
            }
            if (groupName == null || members.isEmpty()) {
                Toast.makeText(this, "Failed to load group info", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // 設置使用者信息
        Log.d("Chatroom", "Initial userId from prefs: " + sharedPreferences.getString("userId", null));
        currentUserId = sharedPreferences.getString("userId", null);
        if (currentUserId == null) {
            currentUserId = "1"; // 確保這是預設值
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("userId", currentUserId);
            editor.apply();
            Log.w("Chatroom", "userId was null, set to 1");
        }
        Log.d("Chatroom", "Final userId: " + currentUserId);
        currentUsername = dbHelper.getUsernameFromUserId(currentUserId);
        if (currentUsername == null) {
            currentUsername = "hilda111"; // 預設 username
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("loggedInUser", currentUsername);
            editor.apply();
            Log.w("Chatroom", "Username not found, set to hilda111");
        }
        Log.d("Chatroom", "Loaded userId: " + currentUserId + ", username: " + currentUsername);

        // 檢查是否為群組建立者
        if (isGroupCreator()) {
            hasAcceptedInvitation = true; // 建立者自動接受
            Log.d("Chatroom", "User " + currentUserId + " is the group creator, setting hasAcceptedInvitation to true");
        }

        // 設置 dbHelper 監聽器
        dbHelper.setOnMessageInsertedListener(groupName1 -> {
            if (groupName1 != null && groupName1.equals(groupName)) {
                runOnUiThread(this::loadMessagesFromDatabase);
                Log.d("Chatroom", "Message inserted for group: " + groupName1 + ", UI reloaded");
            }
        });

        // 初始化 ExecutorService
        executorService = Executors.newSingleThreadExecutor();

        // 更新 UI
        textViewGroupName.setText("Group: " + groupName);
        textViewMembers.setText("Members: " + String.join(", ", members));
        updateUI(); // 根據 hasAcceptedInvitation 更新 UI
        Toast.makeText(this, "Welcome to " + groupName + ", " + currentUsername + "!", Toast.LENGTH_SHORT).show();

        // 設置按鈕監聽器
        buttonSend.setOnClickListener(this::onSendButtonClick);
        loadMessagesFromDatabase();
        startMessagePolling();

        // 設置 NavigationView 監聽器
        navigationView.setNavigationItemSelectedListener(this::handleNavigationItemSelected);
    }

    private boolean isGroupCreator() {
        String creatorId = sharedPreferences.getString(groupName + "_creator", null);
        if (creatorId == null) {
            // 如果沒有記錄創建者，檢查資料庫中的第一個 accepted 邀請是否為當前用戶
            SQLiteDatabase db = dbHelper.getRegisterDatabase();
            Cursor cursor = db.query(TABLE_INVITATIONS,
                    new String[]{COL_INVITED_USER, COL_STATUS},
                    COL_GROUP_NAME + "=? AND " + COL_STATUS + "=?",
                    new String[]{groupName, "accepted"},
                    null, null, COL_INVITATION_ID + " ASC LIMIT 1");
            boolean isCreator = cursor.moveToFirst() && currentUserId.equals(cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITED_USER)));
            cursor.close();
            if (isCreator) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(groupName + "_creator", currentUserId);
                editor.apply();
                Log.d("Chatroom", "Set " + currentUserId + " as creator of group " + groupName);
            }
            return isCreator;
        }
        return currentUserId.equals(creatorId);
    }

    public void checkInvitationStatus() {
        dbHelper.syncInvitations(this, new RegisterDatabaseHelper.SyncCallback() {
            @Override
            public void onSyncComplete(boolean success) {
                runOnUiThread(() -> {
                    if (!success) {
                        Toast.makeText(Chatroom.this, "Failed to sync invitations", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Invitation> pendingInvitations = dbHelper.getPendingInvitations(currentUserId);
                    Log.d("Chatroom", "Pending invitations for " + currentUserId + ": " + pendingInvitations.size());

                    // 移除 hasAcceptedInvitation 檢查，直接允許發送
                    hasAcceptedInvitation = true; // 進入聊天室即視為已接受

                    if (!pendingInvitations.isEmpty()) {
                        for (Invitation invitation : pendingInvitations) {
                            if ("pending".equals(invitation.getStatus()) && invitation.getGroupName().equals(groupName)) {
                                showInvitationDialog(invitation);
                                break;
                            }
                        }
                    }

                    updateUI(); // 更新 UI 移除鎖定
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
                    hasAcceptedInvitation = true; // 立即更新邀請狀態
                    updateUI(); // 立即更新 UI
                    checkInvitationStatus(); // 重新檢查並同步
                    dialog.dismiss();
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
        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasAcceptedInvitation) {
            Toast.makeText(this, "Please accept the group invitation to send messages", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageId = dbHelper.generateRandomId();
        long timestamp = System.currentTimeMillis();
        Log.d("Chatroom", "dbHelper is " + (dbHelper != null ? "not null" : "null"));
        if (dbHelper != null) {
            dbHelper.insertMessage(messageId, groupName, currentUsername, message, timestamp);
            Log.d("Chatroom", "Message insertion attempted, ID: " + messageId);
            runOnUiThread(this::loadMessagesFromDatabase);
            lastMessageId = messageId;
            if (isNetworkAvailable()) {
                sendMessageToServerAndWait(groupName, currentUsername, message, messageId, timestamp, () -> runOnUiThread(() -> editTextMessage.setText("")));
            } else {
                Log.e("Chatroom", "No network available, upload aborted for ID: " + messageId);
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "No network connection", Toast.LENGTH_SHORT).show());
            }
        } else {
            Log.e("Chatroom", "dbHelper is null, cannot insert message");
            Toast.makeText(this, "Database helper is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void sendMessageToServerAndWait(String groupName, String sender, String message, String messageId, long timestamp, Runnable onComplete) {
        class UploadState {
            private boolean success = false;
            private int attempt = 0;
            private final int MAX_RETRIES = 2; // 減少重試次數

            public boolean isSuccess() { return success; }
            public int getAttempt() { return attempt; }
            public void setSuccess(boolean value) { success = value; }
            public void incrementAttempt() { attempt++; }
        }

        UploadState state = new UploadState();

        while (state.getAttempt() < state.MAX_RETRIES && !state.isSuccess()) {
            final int currentAttempt = state.getAttempt() + 1;
            Response response = null;
            String responseData = "No response body";
            int responseCode = -1;
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS) // 減少超時時間
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("group_name", groupName);
                jsonBody.put("sender", sender);
                jsonBody.put("message", message);
                jsonBody.put("message_id", messageId);

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/send_message.php")
                        .post(requestBody)
                        .build();

                Log.d("Chatroom", "Attempting to send request (attempt " + currentAttempt + ") to: " + RegisterDatabaseHelper.getServerUrl() + "/send_message.php");
                response = client.newCall(request).execute();
                if (response != null) {
                    responseCode = response.code();
                    responseData = response.body() != null ? response.body().string() : "No response body";
                    Log.d("Chatroom", "Server response (attempt " + currentAttempt + "): Status=" + responseCode + ", Body=" + responseData);
                    if (response.isSuccessful()) {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.getBoolean("success")) {
                            Log.d("Chatroom", "Message uploaded successfully to cloud, ID: " + messageId);
                            if (dbHelper != null) {
                                dbHelper.updateMessageSyncStatus(messageId, 1);
                            }
                            state.setSuccess(true);
                        } else {
                            final String errorMessage = jsonResponse.optString("message", "Unknown server error");
                            Log.e("Chatroom", "Server reported failure (attempt " + currentAttempt + "): " + errorMessage);
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器失敗 (嘗試 " + currentAttempt + "): " + errorMessage, Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        final int finalResponseCode = responseCode;
                        Log.e("Chatroom", "Server error (attempt " + currentAttempt + "): " + finalResponseCode + ", Body=" + responseData);
                        runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤 (嘗試 " + currentAttempt + "): " + finalResponseCode, Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e("Chatroom", "No response from server (attempt " + currentAttempt + ")");
                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "無伺服器回應 (嘗試 " + currentAttempt + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                final String errorMsg = e.getMessage();
                Log.e("Chatroom", "IO Error sending message (attempt " + currentAttempt + "): " + errorMsg + ", StackTrace: " + Log.getStackTraceString(e));
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "網路錯誤 (嘗試 " + currentAttempt + "): " + errorMsg, Toast.LENGTH_SHORT).show());
            } catch (JSONException e) {
                final String errorMsg = e.getMessage();
                Log.e("Chatroom", "JSON Error (attempt " + currentAttempt + "): " + errorMsg + ", Response: " + responseData, e);
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "資料解析錯誤 (嘗試 " + currentAttempt + ")", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                Log.e("Chatroom", "Unexpected error (attempt " + currentAttempt + "): " + errorMsg + ", StackTrace: " + Log.getStackTraceString(e));
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "意外錯誤 (嘗試 " + currentAttempt + "): " + errorMsg, Toast.LENGTH_SHORT).show());
            } finally {
                if (response != null) {
                    response.close();
                }
            }

            if (!state.isSuccess() && state.getAttempt() < state.MAX_RETRIES - 1) {
                try {
                    Log.w("Chatroom", "Retrying upload for ID: " + messageId + " (attempt " + (state.getAttempt() + 2) + ")");
                    Thread.sleep(2000); // 減少重試間隔至 2 秒
                } catch (InterruptedException e) {
                    Log.e("Chatroom", "Interrupted during retry: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
                state.incrementAttempt();
            }
        }

        if (state.isSuccess() && dbHelper != null) {
            runOnUiThread(() -> dbHelper.updateMessageSyncStatus(messageId, 1));
        }
        runOnUiThread(onComplete); // 執行回調，例如清空輸入框
    }


    private void sendMessageToServer(String groupName, String sender, String message, String messageId, long timestamp) {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("group_name", groupName);
                jsonBody.put("sender", sender);
                jsonBody.put("message", message);
                jsonBody.put("message_id", messageId);
                jsonBody.put("timestamp", timestamp);

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/send_message.php")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseData = response.body() != null ? response.body().string() : "";
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                            Log.d("Chatroom", "Message uploaded successfully, ID: " + messageId + ", Response: " + responseData);
                            if (dbHelper != null) {
                                dbHelper.updateMessageSyncStatus(messageId, 1); // 標記為已同步
                            }
                        } else {
                            String errorMessage = jsonResponse.has("message") ? jsonResponse.getString("message") : "Unknown error";
                            Log.e("Chatroom", "Server reported failure: " + errorMessage + ", Response: " + responseData);
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "Server failed: " + errorMessage, Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        Log.e("Chatroom", "Server error: " + response.code() + ", Response: " + response.body().string());
                        runOnUiThread(() -> Toast.makeText(Chatroom.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e("Chatroom", "Error sending message: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "Error sending message: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
                    .connectTimeout(15, TimeUnit.SECONDS)
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
                                if (data.length() > 0) {
                                    for (int i = 0; i < data.length(); i++) {
                                        JSONObject messageObj = data.getJSONObject(i);
                                        String messageId = messageObj.getString("message_id");
                                        String sender = messageObj.has("sender") ? messageObj.getString("sender") : "Unknown";
                                        String messageText = messageObj.has("message") ? messageObj.getString("message") : "";
                                        long timestamp = messageObj.has("timestamp") ? messageObj.getLong("timestamp") : System.currentTimeMillis();

                                        if (!messageId.equals(lastMessageId)) {
                                            if (dbHelper != null) {
                                                dbHelper.insertMessage(messageId, groupName, sender, messageText, timestamp);
                                            }
                                            lastMessageId = messageId; // 更新到最新 messageId
                                        }
                                    }
                                }
                            } else {
                                Log.w("Chatroom", "Polling response not successful: " + responseData);
                            }
                        } catch (JSONException e) {
                            Log.e("Chatroom", "JSON parse error: " + e.getMessage() + ", Response: " + responseData);
                        }
                    } else {
                        Log.e("Chatroom", "Polling failed with code: " + response.code());
                    }
                } catch (IOException e) {
                    Log.e("Chatroom", "IO error: " + e.getMessage());
                }

                try {
                    Thread.sleep(500); // 減少到 500 毫秒 =>延遲大概5分鐘
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
                null, null, RegisterDatabaseHelper.COL_TIMESTAMP + " ASC");

        List<String> newMessages = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
                String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
                String fullMessage = sender + ": " + message + " (" + formatTimestamp(timestamp) + ")";
                newMessages.add(fullMessage);
                lastMessageId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE_ID));
            } while (cursor.moveToNext());
        }
        cursor.close();

        runOnUiThread(() -> {
            if (!messageList.equals(newMessages)) {
                messageList.clear();
                messageList.addAll(newMessages);
                messageAdapter.notifyDataSetChanged();
                messageRecyclerView.scrollToPosition(messageList.size() - 1);
                Log.d("Chatroom", "UI updated with " + messageList.size() + " messages, lastMessageId: " + lastMessageId);
            }
        });
    }

    public void onInvitationAccepted(String invitationId) {
        if (dbHelper == null) {
            Log.e("Chatroom", "dbHelper is null, cannot process invitation");
            return;
        }
        dbHelper.updateInvitationStatus(invitationId, "accepted");
        checkInvitationStatus();

        runOnUiThread(() -> {
            String groupName = getGroupNameFromInvitation(invitationId);
            if (groupName != null) {
                String membersString = sharedPreferences.getString(groupName + "_members", "");
                members = new ArrayList<>();
                if (!membersString.isEmpty()) {
                    String[] membersArray = membersString.split(",");
                    for (String member : membersArray) {
                        String cleanMember = member.trim();
                        String userId = cleanMember.contains("(") ? cleanMember.substring(cleanMember.indexOf("(") + 1, cleanMember.indexOf(")")) : dbHelper.getUserIdFromUsername(cleanMember);
                        if (userId != null) members.add(userId);
                    }
                }
                textViewMembers.setText("Members: " + String.join(", ", members)); // 更新成員列表
            } else {
                Log.e("Chatroom", "Failed to get group name for invitation ID: " + invitationId);
                Toast.makeText(Chatroom.this, "無法找到群組", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getGroupNameFromInvitation(String invitationId) {
        if (dbHelper == null) {
            Log.e("Chatroom", "dbHelper is null, cannot query group name");
            return null;
        }
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
        /*
        if (hasAcceptedInvitation) {
            buttonSend.setEnabled(true);
            editTextMessage.setEnabled(true);
            editTextMessage.setHint("輸入訊息...");
        } else {
            buttonSend.setEnabled(false);
            editTextMessage.setEnabled(false);
            editTextMessage.setHint("請先接受群組邀請才能發送訊息");
        }
        Log.d("Chatroom", "UI updated, hasAcceptedInvitation: " + hasAcceptedInvitation);
        */
        // 直接啟用訊息輸入
        editTextMessage.setEnabled(true);
        editTextMessage.setHint("輸入訊息...");
        buttonSend.setEnabled(true); // 確保發送按鈕可用

    }


    @Override
    protected void onResume() {
        super.onResume();
        updateNavigationMenu();
        checkInvitationStatus();
        isPollingActive = true;
        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        loadMessagesFromDatabase();
        startMessagePolling();

        RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
        dbHelper.checkInvitationStatus(currentUserId); // currentUserId 為 XDGXC 或 1
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
        if (executorService != null) {
            executorService.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.setOnMessageInsertedListener(null);
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
        // 實現抽屜菜單的設置邏輯（目前留空）
    }

    private boolean handleNavigationItemSelected(MenuItem item) {
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