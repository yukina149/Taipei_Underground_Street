package com.example.cameraproject_2;

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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Chatroom extends AppCompatActivity {
    private RecyclerView messageRecyclerView;
    private EditText editTextMessage;
    private Button buttonSend;
    private ImageView refreshIcon;
    private ImageView backArrow;
    private TextView textViewGroupName;
    private List<Object> messageList; // 支持訊息和日期標籤
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
    private int pollingRetryCount = 0;
    private static final int MAX_POLLING_RETRIES = 3;
    private static final long POLLING_INTERVAL_MS = 5000;
    private static final long POLLING_INTERVAL_RETRY_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        executorService = Executors.newSingleThreadExecutor();

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        backArrow = findViewById(R.id.backArrow);
        messageRecyclerView = findViewById(R.id.messageRecyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        refreshIcon = findViewById(R.id.refreshIcon);
        textViewGroupName = findViewById(R.id.textViewGroupName);

        dbHelper = new RegisterDatabaseHelper(this);

        if (drawerLayout == null || navigationView == null || backArrow == null ||
                messageRecyclerView == null || editTextMessage == null || buttonSend == null ||
                refreshIcon == null || textViewGroupName == null || dbHelper == null) {
            Log.e("Chatroom", "初始化失敗: " +
                    "drawerLayout=" + (drawerLayout != null) +
                    ", navView=" + (navigationView != null) +
                    ", backArrow=" + (backArrow != null) +
                    ", recyclerView=" + (messageRecyclerView != null) +
                    ", editText=" + (editTextMessage != null) +
                    ", buttonSend=" + (buttonSend != null) +
                    ", refreshIcon=" + (refreshIcon != null) +
                    ", groupName=" + (textViewGroupName != null) +
                    ", dbHelper=" + (dbHelper != null));
            Toast.makeText(this, "初始化失敗", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);

        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(Chatroom.this, chatroom_main.class);
            startActivity(intent);
            overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
            finish();
        });

        refreshIcon.setOnClickListener(v -> {
            refreshMessages();
            if (isNetworkAvailable()) {
                fetchMessagesFromServer();
            } else {
                Toast.makeText(this, "無網路連線，僅從本地載入訊息", Toast.LENGTH_SHORT).show();
            }
        });

        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList, currentUsername);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messageRecyclerView.setLayoutManager(layoutManager);
        messageRecyclerView.setAdapter(messageAdapter);

        Intent intent = getIntent();
        groupName = intent.getStringExtra("groupName");
        String locationMessage = intent.getStringExtra("locationMessage");
        if (groupName == null) {
            Log.e("Chatroom", "groupName is null from Intent");
            groupName = sharedPreferences.getString("lastGroupName", "taipei underground");
            if (groupName.isEmpty()) {
                Toast.makeText(this, "未選擇群組", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        members = intent.getStringArrayListExtra("members");
        if (members == null) {
            members = new ArrayList<>();
            String membersString = sharedPreferences.getString(groupName + "_members", "");
            if (!membersString.isEmpty()) {
                String[] membersArray = membersString.split(",");
                for (String member : membersArray) {
                    String cleanMember = member.trim();
                    String userId = dbHelper.getUserIdFromUsername(cleanMember);
                    if (userId != null && !members.contains(userId)) {
                        members.add(userId);
                    }
                }
            }
        }

        if (members.isEmpty()) {
            Log.w("Chatroom", "Members list is empty for group: " + groupName);
            Toast.makeText(this, "群組無成員", Toast.LENGTH_SHORT).show();
        }

        currentUserId = sharedPreferences.getString("userId", null);
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Log.e("Chatroom", "No userId found in SharedPreferences, redirecting to login");
            Toast.makeText(this, "請先登錄", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PersonalAccount.class));
            finish();
            return;
        }

        currentUsername = dbHelper.getUsernameFromUserId(currentUserId);
        if (currentUsername == null) {
            Log.e("Chatroom", "Username not found for userId: " + currentUserId);
            currentUsername = "訪客";
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("loggedInUser", currentUsername);
            editor.apply();
        }
        Log.d("Chatroom", "User initialized: userId=" + currentUserId + ", username=" + currentUsername);

        messageAdapter = new MessageAdapter(messageList, currentUsername);
        messageRecyclerView.setAdapter(messageAdapter);

        if (isGroupCreator()) {
            hasAcceptedInvitation = true;
            Log.d("Chatroom", "User " + currentUserId + " is the group creator, setting hasAcceptedInvitation to true");
        } else {
            hasAcceptedInvitation = dbHelper.isInvitationAccepted(currentUserId, groupName);
            Log.d("Chatroom", "User " + currentUserId + " hasAcceptedInvitation: " + hasAcceptedInvitation);
        }

        if (locationMessage != null && !locationMessage.isEmpty()) {
            String messageId = dbHelper.generateRandomId();
            long timestamp = System.currentTimeMillis();
            dbHelper.insertMessage(messageId, groupName, "System", locationMessage, timestamp);
            Log.d("Chatroom", "Inserted location message: " + locationMessage + ", ID: " + messageId);

            if (isNetworkAvailable()) {
                sendMessageToServer(groupName, "System", locationMessage, messageId, timestamp);
            } else {
                Log.w("Chatroom", "No network available, location message stored locally");
                Toast.makeText(this, "無網路連線，位置訊息已儲存至本地", Toast.LENGTH_SHORT).show();
            }

            loadMessagesFromDatabase();
        }

        dbHelper.setOnMessageInsertedListener(groupName1 -> {
            if (groupName1 != null && groupName1.equals(groupName)) {
                runOnUiThread(this::loadMessagesFromDatabase);
                Log.d("Chatroom", "Message inserted for group: " + groupName1 + ", UI reloaded");
            }
        });

        textViewGroupName.setText(groupName);
        updateUI();
        Toast.makeText(this, "歡迎來到 " + groupName + ", " + currentUsername + "!", Toast.LENGTH_SHORT).show();

        buttonSend.setOnClickListener(this::onSendButtonClick);
        loadMessagesFromDatabase();
        debugLocalMessages();
        startMessagePolling();

        navigationView.setNavigationItemSelectedListener(this::handleNavigationItemSelected);
        updateNavigationMenu();
        checkInvitationStatus();
    }

    private void updateUI(JSONArray messages) {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                String messageId = message.getString("message_id");
                String groupName = message.getString("group_name");
                String sender = message.getString("sender");
                String content = message.getString("message");
                long timestampMs = message.getLong("timestamp");

                // 格式化時間戳記為本地時間 (Asia/Taipei)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
                String formattedTime = sdf.format(new Date(timestampMs));

                Log.d("Chatroom", "Loaded message: ID=" + messageId + ", sender=" + sender + ", content=" + content + ", formattedTime=" + formattedTime);

                // 添加到訊息列表，使用 MessageAdapter.Message
                messageList.add(new MessageAdapter.Message(sender, content, formattedTime));
            }
            messageAdapter.notifyDataSetChanged();
            messageRecyclerView.scrollToPosition(messageList.size() - 1);
            Log.d("Chatroom", "UI updated with " + messageList.size() + " messages, lastMessageId: " + lastMessageId);
        } catch (Exception e) {
            Log.e("Chatroom", "Error updating UI: " + e.getMessage());
            Toast.makeText(this, "Error updating messages", Toast.LENGTH_SHORT).show();
        }
    }

    private void startMessagePolling() {
        new Thread(() -> {
            long retryDelay = POLLING_INTERVAL_MS;
            while (isPollingActive) {
                try {
                    String url = RegisterDatabaseHelper.getServerUrl() + "/fetch_messages.php?group_name=" + Uri.encode(groupName) + "&last_message_id=" + Uri.encode(lastMessageId);
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject jsonResponse = new JSONObject(response.toString());
                        if (jsonResponse.getBoolean("success")) {
                            pollingRetryCount = 0; // 重置重試計數
                            retryDelay = POLLING_INTERVAL_MS; // 重置延遲
                            JSONArray messages = jsonResponse.getJSONArray("data");
                            if (messages.length() > 0) {
                                runOnUiThread(() -> updateUI(messages));
                                lastMessageId = jsonResponse.getString("last_message_id");
                            }
                        }
                        conn.disconnect();
                        Thread.sleep(retryDelay);
                    } else {
                        pollingRetryCount++;
                        Log.e("Chatroom", "Polling failed with response code: " + responseCode);
                        if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                            pollingRetryCount = 0;
                            retryDelay = POLLING_INTERVAL_MS; // 重置延遲
                            lastMessageId = "0"; // 僅在必要時重置
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤，重試拉取全部訊息", Toast.LENGTH_SHORT).show());
                        } else {
                            retryDelay *= 2; // 指數退避
                        }
                        Thread.sleep(retryDelay);
                    }
                } catch (Exception e) {
                    pollingRetryCount++;
                    Log.e("Chatroom", "Polling error: " + e.getMessage());
                    if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                        pollingRetryCount = 0;
                        retryDelay = POLLING_INTERVAL_MS;
                        lastMessageId = "0";
                    } else {
                        retryDelay *= 2;
                    }
                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "網路錯誤，將稍後重試", Toast.LENGTH_SHORT).show());
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    private boolean isGroupCreator() {
        String creatorId = sharedPreferences.getString(groupName + "_creator", null);
        if (creatorId == null) {
            SQLiteDatabase db = dbHelper.getRegisterDatabase();
            Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                    new String[]{RegisterDatabaseHelper.COL_INVITED_USER, RegisterDatabaseHelper.COL_STATUS},
                    RegisterDatabaseHelper.COL_GROUP_NAME + "=? AND " + RegisterDatabaseHelper.COL_STATUS + "=?",
                    new String[]{groupName, "accepted"},
                    null, null, RegisterDatabaseHelper.COL_INVITATION_ID + " ASC LIMIT 1");
            boolean isCreator = cursor.moveToFirst() && currentUserId.equals(cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_INVITED_USER)));
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
        if (dbHelper == null) {
            dbHelper = new RegisterDatabaseHelper(this);
            Log.w("Chatroom", "dbHelper was null, reinitialized");
        }
        if (currentUserId == null) {
            currentUserId = sharedPreferences.getString("userId", null);
            if (currentUserId == null) {
                Log.e("Chatroom", "currentUserId is null, redirecting to login");
                runOnUiThread(() -> {
                    Toast.makeText(Chatroom.this, "請先登錄", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(Chatroom.this, PersonalAccount.class));
                    finish();
                });
                return;
            }
        }

        dbHelper.syncInvitations(this, new RegisterDatabaseHelper.SyncCallback() {
            @Override
            public void onSyncComplete(boolean success) {
                runOnUiThread(() -> {
                    Log.d("Chatroom", "Server invitation sync status: " + success);
                    if (!success) {
                        Log.e("Chatroom", "Failed to sync invitations");
                        Toast.makeText(Chatroom.this, "無法同步邀請，請檢查網路或稍後重試", Toast.LENGTH_LONG).show();
                        new android.app.AlertDialog.Builder(Chatroom.this)
                                .setTitle("同步失敗")
                                .setMessage("無法同步群組邀請，是否重試？")
                                .setPositiveButton("重試", (dialog, which) -> checkInvitationStatus())
                                .setNegativeButton("取消", null)
                                .show();
                        return;
                    }

                    List<Invitation> pendingInvitations = dbHelper.getPendingInvitations(currentUserId);
                    Log.d("Chatroom", "Pending invitations for " + currentUserId + ": " + (pendingInvitations != null ? pendingInvitations.size() : 0));

                    if (!isGroupCreator()) {
                        hasAcceptedInvitation = dbHelper.isInvitationAccepted(currentUserId, groupName);
                        Log.d("Chatroom", "Updated hasAcceptedInvitation: " + hasAcceptedInvitation + " for user " + currentUserId + " in group " + groupName);
                    }

                    if (pendingInvitations != null && !pendingInvitations.isEmpty()) {
                        for (Invitation invitation : pendingInvitations) {
                            if ("pending".equals(invitation.getStatus()) && invitation.getGroupName().equals(groupName)) {
                                showInvitationDialog(invitation);
                                break;
                            }
                        }
                    } else {
                        Log.d("Chatroom", "No pending invitations found for user " + currentUserId);
                    }

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
        runOnUiThread(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("群組邀請")
                    .setMessage("您已被邀請加入群組: " + invitation.getGroupName())
                    .setPositiveButton("接受", (dialog, which) -> {
                        dbHelper.updateInvitationStatus(invitation.getInvitationId(), "accepted");
                        hasAcceptedInvitation = true;
                        updateUI();
                        checkInvitationStatus();
                        dialog.dismiss();
                        Log.d("Chatroom", "Invitation accepted: " + invitation.getInvitationId());
                    })
                    .setNegativeButton("拒絕", (dialog, which) -> {
                        dbHelper.updateInvitationStatus(invitation.getInvitationId(), "rejected");
                        checkInvitationStatus();
                        dialog.dismiss();
                        //Log.d("Chatroom", "Invitation rejected: " + instance.getInvitationId());
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    public void onSendButtonClick(View view) {
        if (!hasAcceptedInvitation) {
            Toast.makeText(this, "請先接受群組邀請以發送訊息", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "訊息不能為空", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = dbHelper.generateRandomId();
        long timestamp = System.currentTimeMillis();
        dbHelper.insertMessage(messageId, groupName, currentUsername, message, timestamp);
        Log.d("Chatroom", "Message inserted locally: ID=" + messageId + ", group=" + groupName);

        loadMessagesFromDatabase();
        editTextMessage.setText("");

        if (isNetworkAvailable()) {
            sendMessageToServer(groupName, currentUsername, message, messageId, timestamp);
        } else {
            Log.w("Chatroom", "No network available, message stored locally");
            Toast.makeText(this, "無網路連線，訊息已儲存至本地", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        boolean isAvailable = networkInfo != null && networkInfo.isConnected();
        Log.d("Chatroom", "Network available: " + isAvailable);
        return isAvailable;
    }

    private void sendMessageToServer(String groupName, String sender, String message, String messageId, long timestamp) {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("group_name", groupName);
                jsonBody.put("sender", sender);
                jsonBody.put("message", message);
                jsonBody.put("message_id", messageId);
                jsonBody.put("timestamp", timestamp);

                Log.d("Chatroom", "Sending message to server: " + jsonBody.toString());

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/sync_messages.php")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("Chatroom", "Failed to send message: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(Chatroom.this, "無法發送訊息: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String responseData = response.body() != null ? response.body().string() : "";
                        Log.d("Chatroom", "Send message response: " + responseData + ", code: " + response.code());
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseData);
                                if (jsonResponse.getBoolean("success")) {
                                    dbHelper.updateMessageSyncStatus(messageId, 1);
                                    Log.d("Chatroom", "Message synced successfully: ID=" + messageId + ", message=" + message);
                                } else {
                                    String errorMessage = jsonResponse.optString("message", "未知錯誤");
                                    Log.e("Chatroom", "Server error: " + errorMessage);
                                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤: " + errorMessage, Toast.LENGTH_SHORT).show());
                                }
                            } catch (JSONException e) {
                                Log.e("Chatroom", "JSON parse error: " + e.getMessage());
                                runOnUiThread(() -> Toast.makeText(Chatroom.this, "無法解析伺服器回應", Toast.LENGTH_SHORT).show());
                            }
                        } else {
                            Log.e("Chatroom", "Server error: " + response.code() + ", response: " + responseData);
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤: " + response.code(), Toast.LENGTH_SHORT).show());
                        }
                        response.close();
                    }
                });
            } catch (JSONException e) {
                Log.e("Chatroom", "JSON error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(Chatroom.this, "無法準備訊息", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void fetchMessagesFromServer() {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            try {
                Log.d("Chatroom", "Fetching messages for group: " + groupName + ", lastMessageId: " + lastMessageId);
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/fetch_messages.php?group_name=" + Uri.encode(groupName) + "&last_message_id=" + Uri.encode(lastMessageId))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseData = response.body() != null ? response.body().string() : "";
                    Log.d("Chatroom", "Fetch response code: " + response.code() + ", response: " + responseData);
                    if (response.isSuccessful()) {
                        pollingRetryCount = 0;
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            Log.d("Chatroom", "Parsed JSON response: " + jsonResponse.toString());
                            if (jsonResponse.getBoolean("success")) {
                                JSONArray data = jsonResponse.getJSONArray("data");
                                Log.d("Chatroom", "Received " + data.length() + " messages: " + data.toString());
                                // 其他邏輯保持不變
                                if (data.length() > 0) {
                                    for (int i = 0; i < data.length(); i++) {
                                        JSONObject messageObj = data.getJSONObject(i);
                                        String messageId = messageObj.getString("message_id");
                                        String sender = messageObj.optString("sender", "未知用戶");
                                        String messageText = messageObj.optString("message", "");
                                        long timestamp;
                                        try {
                                            String timestampStr = messageObj.optString("timestamp", "");
                                            if (timestampStr.isEmpty() || timestampStr.equals("null")) {
                                                timestamp = System.currentTimeMillis();
                                                Log.w("Chatroom", "Invalid timestamp for message ID=" + messageId + ", using current time: " + timestamp);
                                            } else {
                                                timestamp = Long.parseLong(timestampStr);
                                                if (timestampStr.length() <= 10) {
                                                    timestamp *= 1000;
                                                    Log.d("Chatroom", "Converted seconds to milliseconds for message ID=" + messageId + ": " + timestamp);
                                                }
                                            }
                                        } catch (NumberFormatException e) {
                                            Log.e("Chatroom", "Invalid timestamp format for message ID=" + messageId + ": " + e.getMessage());
                                            timestamp = System.currentTimeMillis();
                                        }

                                        // 檢查本地是否已有該訊息
                                        if (!dbHelper.isMessageExists(messageId)) {
                                            dbHelper.insertMessage(messageId, groupName, sender, messageText, timestamp);
                                            Log.d("Chatroom", "Inserting new message: ID=" + messageId + ", content=" + messageText + ", timestamp=" + timestamp);
                                        } else {
                                            // 如果訊息存在，檢查是否需要更新
                                            String localMessage = dbHelper.getMessageContent(messageId);
                                            if (!messageText.equals(localMessage)) {
                                                dbHelper.updateMessageContent(messageId, messageText);
                                                Log.d("Chatroom", "Updated message content: ID=" + messageId + ", new content=" + messageText);
                                            }
                                        }
                                        lastMessageId = messageId;
                                    }
                                    runOnUiThread(this::loadMessagesFromDatabase);
                                } else {
                                    Log.d("Chatroom", "No new messages received for group: " + groupName);
                                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "無新訊息", Toast.LENGTH_SHORT).show());
                                }
                                String newLastMessageId = jsonResponse.optString("last_message_id", lastMessageId);
                                if (!newLastMessageId.equals(lastMessageId)) {
                                    lastMessageId = newLastMessageId;
                                    Log.d("Chatroom", "Updated lastMessageId to: " + lastMessageId);
                                }
                            } else {
                                String errorMessage = jsonResponse.optString("message", "未知錯誤");
                                Log.w("Chatroom", "Fetch response not successful: " + errorMessage);
                                runOnUiThread(() -> Toast.makeText(Chatroom.this, "拉取訊息失敗: " + errorMessage, Toast.LENGTH_SHORT).show());
                            }
                        } catch (JSONException e) {
                            Log.e("Chatroom", "JSON parse error: " + e.getMessage());
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "無法解析伺服器回應: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        Log.e("Chatroom", "Fetch failed with code: " + response.code() + ", response: " + responseData);
                        pollingRetryCount++;
                        if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                            Log.w("Chatroom", "Max retries reached, resetting lastMessageId to 0");
                            lastMessageId = "0";
                            pollingRetryCount = 0;
                            runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤，重試拉取全部訊息", Toast.LENGTH_SHORT).show());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("Chatroom", "Fetch error: " + e.getMessage());
                pollingRetryCount++;
                if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                    Log.w("Chatroom", "Max retries reached due to error, resetting lastMessageId to 0");
                    lastMessageId = "0";
                    pollingRetryCount = 0;
                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "網路錯誤，重試拉取全部訊息", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void refreshMessages() {
        lastMessageId = "0";
        pollingRetryCount = 0;
        loadMessagesFromDatabase();
        Log.d("Chatroom", "Manual refresh triggered, lastMessageId reset to 0");
        Toast.makeText(this, "正在重新載入訊息", Toast.LENGTH_SHORT).show();
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        return sdf.format(new Date(timestamp));
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        return sdf.format(new Date(timestamp));
    }

    private void debugLocalMessages() {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_MESSAGES,
                null, "group_name=?", new String[]{groupName}, null, null, null);
        Log.d("Debug", "Local messages for group: " + groupName);
        while (cursor.moveToNext()) {
            String messageId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE_ID));
            String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
            String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
            int isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_IS_SYNCED_MSG));
            Log.d("Debug", "ID=" + messageId + ", sender=" + sender + ", content=" + message + ", timestamp=" + timestamp + ", isSynced=" + isSynced);
        }
        cursor.close();
    }

    private void loadMessagesFromDatabase() {
        if (dbHelper == null) {
            dbHelper = new RegisterDatabaseHelper(this);
            Log.w("Chatroom", "dbHelper was null, reinitialized");
        }
        if (messageList == null || messageAdapter == null || messageRecyclerView == null) {
            Log.e("Chatroom", "UI components are null, skipping load");
            return;
        }

        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_MESSAGES,
                new String[]{RegisterDatabaseHelper.COL_MESSAGE_ID, RegisterDatabaseHelper.COL_SENDER,
                        RegisterDatabaseHelper.COL_MESSAGE, RegisterDatabaseHelper.COL_TIMESTAMP},
                RegisterDatabaseHelper.COL_GROUP_NAME + "=?",
                new String[]{groupName},
                null, null, RegisterDatabaseHelper.COL_TIMESTAMP + " ASC");

        List<Object> newMessages = new ArrayList<>();
        String lastDate = null;

        if (cursor.moveToFirst()) {
            do {
                String messageId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE_ID));
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
                String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
                String formattedTimestamp = formatTimestamp(timestamp);
                String currentDate = formatDate(timestamp);

                if (!currentDate.equals(lastDate)) {
                    newMessages.add(currentDate);
                    lastDate = currentDate;
                }

                newMessages.add(new MessageAdapter.Message(sender, message, formattedTimestamp));
                lastMessageId = messageId;
                Log.d("Chatroom", "Loaded message: ID=" + messageId + ", sender=" + sender + ", content=" + message + ", timestamp=" + timestamp);
            } while (cursor.moveToNext());
        } else {
            Log.d("Chatroom", "No messages found for group: " + groupName);
        }
        cursor.close();

        runOnUiThread(() -> {
            messageList.clear();
            messageList.addAll(newMessages);
            messageAdapter.notifyDataSetChanged();
            messageRecyclerView.scrollToPosition(messageList.size() - 1);
            Log.d("Chatroom", "UI updated with " + messageList.size() + " messages:");
            for (Object item : messageList) {
                if (item instanceof MessageAdapter.Message) {
                    MessageAdapter.Message msg = (MessageAdapter.Message) item;
                    Log.d("Chatroom", "Message: sender=" + msg.sender + ", content=" + msg.content);
                } else {
                    Log.d("Chatroom", "Date: " + item);
                }
            }
        });
    }

    private void updateUI() {
        runOnUiThread(() -> {
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
        });
    }

    private void updateNavigationMenu() {
        runOnUiThread(() -> {
            if (navigationView == null) {
                Log.e("Chatroom", "navigationView is null");
                return;
            }

            Menu menu = navigationView.getMenu();
            menu.clear();

            menu.add(Menu.NONE, R.id.Chat_room, Menu.NONE, "主頁")
                    .setIcon(R.drawable.store_icon)
                    .setOnMenuItemClickListener(item -> {
                        startActivity(new Intent(Chatroom.this, Chatroom.class));
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    });

            menu.add(Menu.NONE, R.id.Create_Group, Menu.NONE, "創建群組")
                    .setIcon(R.drawable.store_icon)
                    .setOnMenuItemClickListener(item -> {
                        startActivity(new Intent(Chatroom.this, CreateGroupActivity.class));
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    });

            Set<String> groupNames = new HashSet<>();
            SQLiteDatabase db = dbHelper.getRegisterDatabase();
            Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                    new String[]{RegisterDatabaseHelper.COL_GROUP_NAME},
                    RegisterDatabaseHelper.COL_INVITED_USER + "=? AND " + RegisterDatabaseHelper.COL_STATUS + "=?",
                    new String[]{currentUserId, "accepted"},
                    null, null, null);
            while (cursor.moveToNext()) {
                String group = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_GROUP_NAME));
                groupNames.add(group);
            }
            cursor.close();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet("groupNames", groupNames);
            editor.apply();

            for (String group : groupNames) {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, group)
                        .setIcon(R.drawable.ic_go)
                        .setOnMenuItemClickListener(item -> {
                            String selectedGroupName = item.getTitle().toString();
                            Intent intent = new Intent(Chatroom.this, Chatroom.class);
                            intent.putExtra("groupName", selectedGroupName);
                            intent.putStringArrayListExtra("members", new ArrayList<>(members));
                            startActivity(intent);
                            drawerLayout.closeDrawer(GravityCompat.START);
                            return true;
                        });
            }

            menu.add(Menu.NONE, R.id.nav_logout, Menu.NONE, "登出")
                    .setIcon(R.drawable.login_icon)
                    .setOnMenuItemClickListener(item -> {
                        SharedPreferences.Editor editor1 = sharedPreferences.edit();
                        editor1.putBoolean("isLoggedIn", false);
                        editor1.putString("loggedInUser", "訪客");
                        editor1.putString("userId", null);
                        editor1.apply();
                        Toast.makeText(Chatroom.this, "已登出", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Chatroom.this, PersonalAccount.class));
                        finish();
                        return true;
                    });

            Log.d("Chatroom", "Navigation menu updated with groups: " + groupNames);
        });
    }

    private boolean handleNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_logout) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isLoggedIn", false);
            editor.putString("loggedInUser", "訪客");
            editor.putString("userId", null);
            editor.apply();
            Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PersonalAccount.class));
            finish();
        } else if (id == R.id.Chat_room) {
            startActivity(new Intent(this, Chatroom.class));
        } else if (id == R.id.Create_Group) {
            startActivity(new Intent(this, CreateGroupActivity.class));
        } else {
            String selectedGroupName = item.getTitle().toString();
            Intent intent = new Intent(this, Chatroom.class);
            intent.putExtra("groupName", selectedGroupName);
            intent.putStringArrayListExtra("members", new ArrayList<>(members));
            startActivity(intent);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPollingActive = true;
        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        checkInvitationStatus();
        loadMessagesFromDatabase();
        debugLocalMessages();
        startMessagePolling();
        if (isNetworkAvailable()) {
            dbHelper.syncPendingMessages(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPollingActive = false;
        try {
            unregisterReceiver(invitationReceiver);
        } catch (IllegalArgumentException e) {
            Log.w("Chatroom", "Receiver not registered: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPollingActive = false;
        if (executorService != null && !executorService.isShutdown()) {
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
}