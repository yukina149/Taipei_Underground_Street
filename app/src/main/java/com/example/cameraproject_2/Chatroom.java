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
    private Button buttonRefresh;
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
        Toolbar toolbar = findViewById(R.id.toolbar);
        messageRecyclerView = findViewById(R.id.messageRecyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonRefresh = findViewById(R.id.buttonRefresh);
        textViewGroupName = findViewById(R.id.textViewGroupName);
        textViewMembers = findViewById(R.id.textViewMembers);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("taipei underground");
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        dbHelper = new RegisterDatabaseHelper(this);

        if (drawerLayout == null || navigationView == null || toolbar == null ||
                messageRecyclerView == null || editTextMessage == null || buttonSend == null ||
                buttonRefresh == null || textViewGroupName == null || textViewMembers == null || dbHelper == null) {
            Log.e("Chatroom", "Initialization failed: " +
                    "drawerLayout=" + (drawerLayout != null) +
                    ", navView=" + (navigationView != null) +
                    ", toolbar=" + (toolbar != null) +
                    ", recyclerView=" + (messageRecyclerView != null) +
                    ", editText=" + (editTextMessage != null) +
                    ", buttonSend=" + (buttonSend != null) +
                    ", buttonRefresh=" + (buttonRefresh != null) +
                    ", groupName=" + (textViewGroupName != null) +
                    ", members=" + (textViewMembers != null) +
                    ", dbHelper=" + (dbHelper != null));
            Toast.makeText(this, "初始化失敗", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        registerReceiver(invitationReceiver, new IntentFilter("com.example.cameraproject_2.INVITATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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

        textViewGroupName.setText("群組: " + groupName);
        textViewMembers.setText("成員: " + String.join(", ", members));
        updateUI();
        Toast.makeText(this, "歡迎來到 " + groupName + ", " + currentUsername + "!", Toast.LENGTH_SHORT).show();

        buttonSend.setOnClickListener(this::onSendButtonClick);
        buttonRefresh.setOnClickListener(v -> refreshMessages());
        loadMessagesFromDatabase();
        debugLocalMessages();
        startMessagePolling();

        navigationView.setNavigationItemSelectedListener(this::handleNavigationItemSelected);
        updateNavigationMenu();
        checkInvitationStatus();
    }

    private boolean isGroupCreator() {
        String creatorId = sharedPreferences.getString(groupName + "_creator", null);
        if (creatorId == null) {
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
                        Toast.makeText(Chatroom.this, "無法同步邀請", Toast.LENGTH_SHORT).show();
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
                        Log.d("Chatroom", "Invitation rejected: " + invitation.getInvitationId());
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

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(RegisterDatabaseHelper.getServerUrl() + "/send_message.php")
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
                                    Log.d("Chatroom", "Message synced successfully: ID=" + messageId);
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

    private void updateMessageList(String message) {
        runOnUiThread(() -> {
            if (messageList != null && messageAdapter != null && messageRecyclerView != null) {
                messageList.add(message);
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                messageRecyclerView.scrollToPosition(messageList.size() - 1);
                Log.d("Chatroom", "Message added to UI: " + message);
            } else {
                Log.e("Chatroom", "UI components are null, cannot update message list");
            }
        });
    }

    private void startMessagePolling() {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            while (isPollingActive) {
                try {
                    Log.d("Chatroom", "Polling for group: " + groupName + ", lastMessageId: " + lastMessageId + ", userId: " + currentUserId);
                    Request request = new Request.Builder()
                            .url(RegisterDatabaseHelper.getServerUrl() + "/fetch_messages.php?group_name=" + Uri.encode(groupName) + "&last_message_id=" + Uri.encode(lastMessageId))
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        String responseData = response.body() != null ? response.body().string() : "";
                        Log.d("Chatroom", "Polling response code: " + response.code() + ", response: " + responseData);
                        if (response.isSuccessful()) {
                            pollingRetryCount = 0;
                            try {
                                JSONObject jsonResponse = new JSONObject(responseData);
                                if (jsonResponse.getBoolean("success")) {
                                    JSONArray data = jsonResponse.getJSONArray("data");
                                    Log.d("Chatroom", "Received " + data.length() + " messages for group: " + groupName);
                                    if (data.length() > 0) {
                                        for (int i = 0; i < data.length(); i++) {
                                            JSONObject messageObj = data.getJSONObject(i);
                                            String messageId = messageObj.getString("message_id");
                                            String sender = messageObj.optString("sender", "未知用戶");
                                            String messageText = messageObj.optString("message", "");
                                            long timestamp = messageObj.optLong("timestamp", System.currentTimeMillis());

                                            timestamp = timestamp + TimeZone.getDefault().getOffset(timestamp);

                                            if (!dbHelper.isMessageExists(messageId)) {
                                                dbHelper.insertMessage(messageId, groupName, sender, messageText, timestamp);
                                                Log.d("Chatroom", "Inserted new message: ID=" + messageId + ", content=" + messageText);
                                                lastMessageId = messageId; // 更新 lastMessageId
                                            }
                                        }
                                        runOnUiThread(this::loadMessagesFromDatabase);
                                    } else {
                                        Log.d("Chatroom", "No new messages received for group: " + groupName);
                                    }
                                    // 更新 lastMessageId 為伺服器返回的最後一個 message_id
                                    String newLastMessageId = jsonResponse.optString("last_message_id", lastMessageId);
                                    if (!newLastMessageId.equals(lastMessageId)) {
                                        lastMessageId = newLastMessageId;
                                        Log.d("Chatroom", "Updated lastMessageId to: " + lastMessageId);
                                    }
                                } else {
                                    String errorMessage = jsonResponse.optString("message", "未知錯誤");
                                    Log.w("Chatroom", "Polling response not successful: " + errorMessage);
                                    runOnUiThread(() -> Toast.makeText(Chatroom.this, "拉取訊息失敗: " + errorMessage, Toast.LENGTH_SHORT).show());
                                }
                            } catch (JSONException e) {
                                Log.e("Chatroom", "JSON parse error: " + e.getMessage());
                                runOnUiThread(() -> Toast.makeText(Chatroom.this, "無法解析伺服器回應: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        } else {
                            Log.e("Chatroom", "Polling failed with code: " + response.code() + ", response: " + responseData);
                            pollingRetryCount++;
                            if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                                Log.w("Chatroom", "Max retries reached, resetting lastMessageId to 0");
                                lastMessageId = "0";
                                pollingRetryCount = 0;
                                runOnUiThread(() -> Toast.makeText(Chatroom.this, "伺服器錯誤，重試拉取全部訊息", Toast.LENGTH_SHORT).show());
                            }
                            Thread.sleep(POLLING_INTERVAL_RETRY_MS);
                            continue;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    Log.e("Chatroom", "Polling error: " + e.getMessage());
                    pollingRetryCount++;
                    if (pollingRetryCount >= MAX_POLLING_RETRIES) {
                        Log.w("Chatroom", "Max retries reached due to error, resetting lastMessageId to 0");
                        lastMessageId = "0";
                        pollingRetryCount = 0;
                        runOnUiThread(() -> Toast.makeText(Chatroom.this, "網路錯誤，重試拉取全部訊息", Toast.LENGTH_SHORT).show());
                    }
                    try {
                        Thread.sleep(POLLING_INTERVAL_RETRY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                try {
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
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
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private void debugLocalMessages() {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_MESSAGES,
                null, "group_name=?", new String[]{groupName}, null, null, null);
        while (cursor.moveToNext()) {
            String messageId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE_ID));
            String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
            String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
            Log.d("Debug", "Local message: ID=" + messageId + ", sender=" + sender + ", content=" + message + ", timestamp=" + timestamp);
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
                COL_GROUP_NAME + "=?",
                new String[]{groupName},
                null, null, RegisterDatabaseHelper.COL_TIMESTAMP + " ASC");

        List<String> newMessages = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                String messageId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE_ID));
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_SENDER));
                String message = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_MESSAGE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_TIMESTAMP));
                String fullMessage = sender + ": " + message + " (" + formatTimestamp(timestamp) + ")";
                newMessages.add(fullMessage);
                lastMessageId = messageId; // 更新 lastMessageId
                Log.d("Chatroom", "Loaded message: ID=" + messageId + ", sender=" + sender + ", content=" + message);
            } while (cursor.moveToNext());
        } else {
            Log.d("Chatroom", "No messages found for group: " + groupName);
        }
        cursor.close();

        runOnUiThread(() -> {
            if (!messageList.equals(newMessages)) {
                messageList.clear();
                messageList.addAll(newMessages);
                messageAdapter.notifyDataSetChanged();
                messageRecyclerView.scrollToPosition(messageList.size() - 1);
                Log.d("Chatroom", "UI updated with " + messageList.size() + " messages, lastMessageId: " + lastMessageId);
            } else {
                Log.d("Chatroom", "No UI update needed, message list unchanged");
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
                        startActivity(new Intent(Chatroom.this, MainActivity.class));
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
            Cursor cursor = db.query(TABLE_INVITATIONS,
                    new String[]{COL_GROUP_NAME},
                    COL_INVITED_USER + "=? AND " + COL_STATUS + "=?",
                    new String[]{currentUserId, "accepted"},
                    null, null, null);
            while (cursor.moveToNext()) {
                String group = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
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
            startActivity(new Intent(this, MainActivity.class));
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