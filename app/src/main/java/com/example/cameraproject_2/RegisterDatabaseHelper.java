package com.example.cameraproject_2;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "RegisterDatabaseHelper";
    public static final String REGISTER_DB_NAME = "register.db";
    private static final int DATABASE_VERSION = 7;

    public static final String TABLE_NAME = "Users";
    public static final String COL_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_LAST_MODIFIED = "last_modified";
    public static final String COL_IS_SYNCED = "is_synced";
    public static final String COL_SYNC_ACTION = "sync_action";

    public static final String TABLE_INVITATIONS = "groupinvitations";
    public static final String COL_INVITATION_ID = "invitation_id";
    public static final String COL_GROUP_NAME = "group_name";
    public static final String COL_INVITED_USER = "invited_user";
    public static final String COL_STATUS = "status";
    public static final String COL_IS_SYNCED_INV = "is_synced";

    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_MESSAGE_ID = "message_id";
    public static final String COL_SENDER = "sender";
    public static final String COL_MESSAGE = "message";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IS_SYNCED_MSG = "is_synced";
    public static final String COL_PROFILE_IMAGE_URL = "profile_image_url"; // 新增欄位

    private final Context context;
    private SQLiteDatabase registerDatabase;

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static String SERVER_URL = "http://13.239.232.58/android_studio";

    private SQLiteDatabase db; // 類成員變數
    private static final Object dbLock = new Object(); // 用於同步的鎖

    private Toast toast;


    /*
    public RegisterDatabaseHelper(Context context) {
        super(context, REGISTER_DB_NAME, null, DATABASE_VERSION);
        this.context = context;
        loadServerUrl();
        checkDatabaseIntegrity();
    }

     */

    public RegisterDatabaseHelper(Context context) {
        super(context, REGISTER_DB_NAME, null, DATABASE_VERSION);
        this.context = context;
        SERVER_URL = "http://13.239.232.58/android_studio"; // 強制設置
        loadServerUrl(); // 載入後確認
        checkDatabaseIntegrity();
        cleanInvalidUsers(); // 清理無效數據
        Log.d(TAG, "初始化 SERVER_URL: " + SERVER_URL);
    }

    public void cleanInvalidUsers() {
        SQLiteDatabase db = getRegisterDatabase();
        db.beginTransaction();
        try {
            // 刪除 id 為空或包含空格的記錄
            int deletedRows = db.delete(TABLE_NAME, COL_ID + " IS NULL OR " + COL_ID + " LIKE ?",
                    new String[]{"% %"}); // % % 匹配包含空格的 id
            Log.d(TAG, "Cleaned " + deletedRows + " invalid users from " + TABLE_NAME);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning invalid users: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    public void fetchInvitationsFromServer(Context context, String userId, String lastSyncTime, SyncCallback callback) {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            String url = getServerUrl() + "/fetch_invitations.php?invited_user=" + Uri.encode(userId) + "&last_sync_time=" + lastSyncTime;
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    if (jsonResponse.getBoolean("success")) {
                        JSONArray data = jsonResponse.getJSONArray("data");
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject inv = data.getJSONObject(i);
                            String invitationId = inv.getString("invitation_id");
                            String groupName = inv.getString("group_name");
                            String status = inv.getString("status");
                            insertInvitation(invitationId, groupName, userId, status, 0);
                        }
                        callback.onSyncComplete(true);
                    } else {
                        callback.onSyncComplete(false);
                    }
                } else {
                    callback.onSyncComplete(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching invitations: " + e.getMessage());
                callback.onSyncComplete(false);
            }
        });
    }
    public boolean isInvitationAccepted(String userId, String groupName) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS, new String[]{COL_STATUS},
                COL_INVITED_USER + "=? AND " + COL_GROUP_NAME + "=? AND " + COL_STATUS + "=?",
                new String[]{userId, groupName, "accepted"}, null, null, null);
        boolean isAccepted = cursor.getCount() > 0;
        cursor.close();
        return isAccepted;
    }

    public void insertInvitation(String invitationId, String groupName, String invitedUser, String status, int isSynced) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_INVITATION_ID, invitationId);
        values.put(COL_GROUP_NAME, groupName);
        values.put(COL_INVITED_USER, invitedUser);
        values.put(COL_STATUS, status);
        values.put(COL_IS_SYNCED_INV, isSynced);

        long result = db.insertWithOnConflict(TABLE_INVITATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (result != -1) {
            Log.d(TAG, "Inserted invitation: " + invitationId);
        } else {
            Log.e(TAG, "Failed to insert invitation: " + invitationId);
        }
    }
    /*

    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultUrl = "http://192.168.10.15/android_studio";
        SERVER_URL = prefs.getString(KEY_SERVER_URL, defaultUrl);
        Log.d(TAG, "Loaded server URL from prefs: " + SERVER_URL);
    }

     */
    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultUrl = "http://13.239.232.58/android_studio";
        SERVER_URL = prefs.getString(KEY_SERVER_URL, defaultUrl);
        Log.d(TAG, "載入的 SERVER_URL: " + SERVER_URL);
        // 清除舊值（僅在調試時使用）
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_SERVER_URL); // 移除舊地址
        editor.apply();
        SERVER_URL = defaultUrl; // 強制使用預設值
        Log.d(TAG, "更新後的 SERVER_URL: " + SERVER_URL);
    }

    public void setServerUrl(String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SERVER_URL, url);
        editor.apply();
        loadServerUrl();
        Log.d(TAG, "Server URL updated to: " + url);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_USERNAME + " TEXT NOT NULL, " +
                COL_EMAIL + " TEXT, " +
                COL_PASSWORD + " TEXT NOT NULL, " +
                COL_LAST_MODIFIED + " INTEGER, " +
                COL_IS_SYNCED + " INTEGER DEFAULT 0, " +
                COL_SYNC_ACTION + " TEXT, " +
                COL_PROFILE_IMAGE_URL + " TEXT)"; // 新增 profile_image_url
        db.execSQL(createTable);
        Log.d(TAG, "Database created with table: " + TABLE_NAME);

        String createInvitationsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_INVITATIONS + " (" +
                COL_INVITATION_ID + " TEXT PRIMARY KEY, " +
                COL_GROUP_NAME + " TEXT NOT NULL, " +
                COL_INVITED_USER + " TEXT NOT NULL, " +
                COL_STATUS + " TEXT NOT NULL, " +
                COL_IS_SYNCED_INV + " INTEGER DEFAULT 0)";
        db.execSQL(createInvitationsTable);
        Log.d(TAG, "Database created with table: " + TABLE_INVITATIONS);

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGES + " (" +
                COL_MESSAGE_ID + " TEXT PRIMARY KEY, " +
                COL_GROUP_NAME + " TEXT NOT NULL, " +
                COL_SENDER + " TEXT NOT NULL, " +
                COL_MESSAGE + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_IS_SYNCED_MSG + " INTEGER DEFAULT 0)";
        db.execSQL(createMessagesTable);
        Log.d(TAG, "Database created with table: " + TABLE_MESSAGES);

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invited_user ON " + TABLE_INVITATIONS + " (" + COL_INVITED_USER + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 現有升級邏輯
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_EMAIL + " TEXT");
                Log.d(TAG, "Added email column to Users table during upgrade");
            } catch (SQLiteException e) {
                Log.e(TAG, "Error adding email column during upgrade: " + e.getMessage());
            }
        }
        if (oldVersion < 3) {
            String tempTableName = TABLE_NAME + "_temp";
            String createTempTable = "CREATE TABLE " + tempTableName + " (" +
                    COL_ID + " TEXT PRIMARY KEY, " +
                    COL_USERNAME + " TEXT NOT NULL, " +
                    COL_EMAIL + " TEXT, " +
                    COL_PASSWORD + " TEXT NOT NULL, " +
                    COL_LAST_MODIFIED + " BIGINT, " +
                    COL_IS_SYNCED + " INTEGER DEFAULT 0, " +
                    COL_SYNC_ACTION + " TEXT)";
            db.execSQL(createTempTable);

            String copyData = "INSERT INTO " + tempTableName + " (" +
                    COL_ID + ", " + COL_USERNAME + ", " + COL_EMAIL + ", " +
                    COL_PASSWORD + ", " + COL_LAST_MODIFIED + ", " +
                    COL_IS_SYNCED + ", " + COL_SYNC_ACTION + ") " +
                    "SELECT " + COL_ID + ", " + COL_USERNAME + ", " + COL_EMAIL + ", " +
                    COL_PASSWORD + ", " + COL_LAST_MODIFIED + ", " +
                    COL_IS_SYNCED + ", " + COL_SYNC_ACTION + " FROM " + TABLE_NAME;
            db.execSQL(copyData);

            db.execSQL("DROP TABLE " + TABLE_NAME);
            db.execSQL("ALTER TABLE " + tempTableName + " RENAME TO " + TABLE_NAME);
            Log.d(TAG, "Upgraded last_modified to BIGINT");
        }
        if (oldVersion < 4) {
            String createInvitationsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_INVITATIONS + " (" +
                    COL_INVITATION_ID + " TEXT PRIMARY KEY, " +
                    COL_GROUP_NAME + " TEXT NOT NULL, " +
                    COL_INVITED_USER + " TEXT NOT NULL, " +
                    COL_STATUS + " TEXT NOT NULL, " +
                    COL_IS_SYNCED_INV + " INTEGER DEFAULT 0)";
            db.execSQL(createInvitationsTable);
            Log.d(TAG, "Created GroupInvitations table during upgrade");
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_INVITATIONS + " ADD COLUMN " + COL_IS_SYNCED_INV + " INTEGER DEFAULT 0");
                Log.d(TAG, "Added is_synced column to GroupInvitations table during upgrade");
            } catch (SQLiteException e) {
                Log.e(TAG, "Error adding is_synced column during upgrade: " + e.getMessage());
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_invited_user ON " + TABLE_INVITATIONS + " (" + COL_INVITED_USER + ")");
        }
        if (oldVersion < 6) {
            String createMessagesTable = "CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGES + " (" +
                    COL_MESSAGE_ID + " TEXT PRIMARY KEY, " +
                    COL_GROUP_NAME + " TEXT NOT NULL, " +
                    COL_SENDER + " TEXT NOT NULL, " +
                    COL_MESSAGE + " TEXT NOT NULL, " +
                    COL_TIMESTAMP + " INTEGER NOT NULL, " +
                    COL_IS_SYNCED_MSG + " INTEGER DEFAULT 0)";
            db.execSQL(createMessagesTable);
            Log.d(TAG, "Added messages table during upgrade to version 6");
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PROFILE_IMAGE_URL + " TEXT");
                Log.d(TAG, "Added profile_image_url column to Users table during upgrade");
            } catch (SQLiteException e) {
                Log.e(TAG, "Error adding profile_image_url column during upgrade: " + e.getMessage());
            }
        }
    }

    //更改姓名
    public boolean updateUsername(String userId, String newUsername) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", newUsername);
        values.put("is_synced", 0); // 標記為未同步
        values.put("sync_action", "update");
        values.put("last_modified", System.currentTimeMillis() / 1000);
        int rowsAffected = db.update("users", values, "id = ?", new String[]{userId});
        Log.d(TAG, "updateUsername: userId=" + userId + ", newUsername=" + newUsername + ", rowsAffected=" + rowsAffected);
        db.close();
        return rowsAffected > 0;
    }

    public void uploadUnsyncedUsers(String priorityUserId) {
        int retryCount = 0;
        final int maxRetries = 3;
        long backoffDelay = 2000;

        while (retryCount < maxRetries) {
            try {
                SQLiteDatabase db = getRegisterDatabase();
                // 優先查詢指定 userId
                String selection = COL_IS_SYNCED + " = 0";
                String[] selectionArgs = null;
                if (priorityUserId != null) {
                    selection += " AND " + COL_ID + " = ?";
                    selectionArgs = new String[]{priorityUserId};
                }
                Cursor cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, null);
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                db.beginTransaction();
                try {
                    boolean hasProcessedPriority = false;
                    while (cursor.moveToNext()) {
                        String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                        String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
                        String profileImageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PROFILE_IMAGE_URL));
                        String syncAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_ACTION));

                        Log.d(TAG, "Starting sync for userId: " + id + ", syncAction: " + syncAction);

                        JSONObject jsonBody = new JSONObject();
                        jsonBody.put(COL_ID, id);
                        jsonBody.put(COL_USERNAME, username);
                        jsonBody.put(COL_PROFILE_IMAGE_URL, profileImageUrl != null ? profileImageUrl : JSONObject.NULL);
                        jsonBody.put(COL_SYNC_ACTION, syncAction != null ? syncAction : "update");

                        RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                        Request request = new Request.Builder()
                                .url(SERVER_URL + "/sync_users.php")
                                .post(requestBody)
                                .build();

                        try (Response response = client.newCall(request).execute()) {
                            String responseData = response.body().string();
                            Log.d(TAG, "Server response for userId " + id + ": " + responseData);
                            if (response.isSuccessful()) {
                                JSONObject jsonResponse = new JSONObject(responseData);
                                if (jsonResponse.getBoolean("success")) {
                                    ContentValues values = new ContentValues();
                                    values.put(COL_IS_SYNCED, 1);
                                    db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{id});
                                    Log.d(TAG, "Uploaded unsynced user with ID: " + id);
                                    if (id.equals(priorityUserId)) {
                                        hasProcessedPriority = true;
                                    }
                                } else {
                                    Log.e(TAG, "Server rejected user with ID: " + id + ", message: " + jsonResponse.getString("message"));
                                    if (id.equals(priorityUserId)) {
                                        throw new IOException(jsonResponse.getString("message"));
                                    }
                                }
                            } else {
                                Log.e(TAG, "Failed to upload user with ID: " + id + ": " + response.code() + " - " + response.message());
                                if (id.equals(priorityUserId)) {
                                    throw new IOException("Upload failed: " + response.code() + " - " + response.message());
                                }
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                    if (priorityUserId == null || hasProcessedPriority) {
                        return; // 如果沒有指定優先用戶或優先用戶已處理，則退出
                    }
                } finally {
                    db.endTransaction();
                    cursor.close();
                }
            } catch (Exception e) {
                retryCount++;
                Log.e(TAG, "Upload attempt " + retryCount + " failed: " + e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(backoffDelay);
                        backoffDelay *= 2;
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "Retry interrupted: " + ie.getMessage());
                    }
                } else {
                    Log.e(TAG, "Upload failed after " + maxRetries + " attempts: " + e.getMessage());
                    showToast("Failed to sync users: " + e.getMessage());
                }
            }
        }
    }

    public void clearUnsyncedUsersExcept(String userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COL_IS_SYNCED + " = 0 AND " + COL_ID + " != ?", new String[]{userId});
        Log.d(TAG, "Cleared unsynced users except userId: " + userId);
    }

    public void logUnsyncedUsers() {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID, COL_USERNAME, COL_SYNC_ACTION},
                COL_IS_SYNCED + " = 0", null, null, null, null);
        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
            String syncAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_ACTION));
            Log.d(TAG, "Unsynced user: id=" + id + ", username=" + username + ", syncAction=" + syncAction);
        }
        cursor.close();
    }

    // 新增方法：獲取用戶頭像 URL
    public String getProfileImageUrl(String userId) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_PROFILE_IMAGE_URL},
                COL_ID + " = ?", new String[]{userId}, null, null, null);
        String imageUrl = null;
        if (cursor.moveToFirst()) {
            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PROFILE_IMAGE_URL));
        }
        cursor.close();
        return imageUrl;
    }

    // 新增方法：更新用戶頭像 URL
    public void updateProfileImageUrl(String userId, String imageUrl) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROFILE_IMAGE_URL, imageUrl);
        values.put(COL_LAST_MODIFIED, System.currentTimeMillis());
        values.put(COL_IS_SYNCED, 0);
        values.put(COL_SYNC_ACTION, "update");
        int rowsAffected = db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{userId});
        if (rowsAffected > 0) {
            Log.d(TAG, "Updated profile_image_url for userId: " + userId);
            // 觸發同步，傳入 userId 作為 priorityUserId
            executorService.execute(() -> {
                try {
                    uploadUnsyncedUsers(userId); // 傳入 userId
                    Log.d(TAG, "Database synced after updating profile_image_url");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync database after updating profile_image_url: " + e.getMessage());
                    showToast("Sync failed: " + e.getMessage());
                }
            });
        } else {
            Log.e(TAG, "Failed to update profile_image_url for userId: " + userId);
        }
    }



    public SQLiteDatabase getRegisterDatabase() {
        synchronized (dbLock) {
            int retries = 3;
            while (retries > 0) {
                try {
                    if (db == null || !db.isOpen()) {
                        db = getWritableDatabase();
                    }
                    return db;
                } catch (SQLiteDatabaseLockedException e) {
                    Log.e(TAG, "Database locked, retrying... (" + retries + " attempts left)");
                    retries--;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "Interrupted during retry: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                    }
                } catch (SQLiteException e) {
                    Log.e(TAG, "Database access error: " + e.getMessage());
                    throw e;
                }
            }
            throw new SQLiteDatabaseLockedException("Failed to open database after retries");
        }
    }

    public void closeDatabase() {
        synchronized (dbLock) {
            if (db != null && db.isOpen()) {
                db.close();
                db = null;
            }
        }
    }

    public String generateRandomId() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder randomId = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            randomId.append(characters.charAt(random.nextInt(characters.length())));
        }
        return randomId.toString();
    }

    public String getUserIdFromUsername(String username) {
        SQLiteDatabase db = getRegisterDatabase();
        String[] columns = {COL_ID};
        String selection = COL_USERNAME + " = ?";
        String[] selectionArgs = {username};
        Cursor cursor = db.query(TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String userId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
            cursor.close();
            Log.d(TAG, "Found userId: " + userId + " for username: " + username);
            return userId;
        }
        Log.e(TAG, "No userId found for username: " + username);
        if (cursor != null) cursor.close();
        return null;
    }

    public String getUsernameFromUserId(String userId) {
        SQLiteDatabase db = getRegisterDatabase();
        Log.d(TAG, "Querying username for userId: " + userId);
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_USERNAME},
                COL_ID + "=?", new String[]{userId}, null, null, null);
        String username = null;
        if (cursor.moveToFirst()) {
            username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
            Log.d(TAG, "Found username: " + username + " for userId: " + userId);
        } else {
            Log.e(TAG, "No username found for userId: " + userId);
            Cursor allUsers = db.query(TABLE_NAME, new String[]{COL_ID, COL_USERNAME},
                    null, null, null, null, null);
            while (allUsers.moveToNext()) {
                String id = allUsers.getString(allUsers.getColumnIndexOrThrow(COL_ID));
                String name = allUsers.getString(allUsers.getColumnIndexOrThrow(COL_USERNAME));
                Log.d(TAG, "User in DB: id=" + id + ", username=" + name);
            }
            allUsers.close();
        }
        cursor.close();
        return username;
    }

    public void checkDatabaseIntegrity() {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID, COL_USERNAME},
                null, null, null, null, null);
        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
            if (id != null && id.matches(".*\\s+.*")) {
                Log.w(TAG, "Found suspicious ID with whitespace: " + id);
            }
            Log.d(TAG, "User in DB: id=" + id + ", username=" + username);
        }
        cursor.close();
    }

    public void updateInvitationStatus(String invitationId, String status) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, status);
        values.put(COL_IS_SYNCED_INV, 0);
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        Set<String> groupNames = prefs.getStringSet("groupNames", new HashSet<>());
        Log.d(TAG, "Current groupNames after update: " + groupNames.toString());
        int rowsAffected = db.update(TABLE_INVITATIONS, values, COL_INVITATION_ID + " = ?", new String[]{invitationId});
        if (rowsAffected > 0) {
            Log.d(TAG, "Updated invitation ID: " + invitationId + " to status: " + status);
            syncInvitations(context, new SyncCallback() {
                @Override
                public void onSyncComplete(boolean success) {
                    if (success) {
                        String groupName = getGroupNameFromInvitation(invitationId);
                        if (groupName != null) {
                            SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            Set<String> groupNames = prefs.getStringSet("groupNames", new HashSet<>());
                            groupNames.add(groupName);
                            editor.putStringSet("groupNames", groupNames);
                            editor.apply();
                            Log.d(TAG, "Added group " + groupName + " to navigation menu");
                        }
                    }
                }
            });
            notifyInvitationStatusChanged();
        } else {
            Log.e(TAG, "Failed to update invitation ID: " + invitationId);
        }
    }

    private String getGroupNameFromInvitation(String invitationId) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS, new String[]{COL_GROUP_NAME},
                COL_INVITATION_ID + " = ?", new String[]{invitationId}, null, null, null);
        String groupName = null;
        if (cursor.moveToFirst()) {
            groupName = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
            Log.d(TAG, "Retrieved groupName: " + groupName + " for invitationId: " + invitationId);
        } else {
            Log.e(TAG, "No groupName found for invitationId: " + invitationId);
        }
        cursor.close();
        return groupName;
    }

    public class InvitationUpdateReceiver extends BroadcastReceiver {
        private static final String TAG = "InvitationUpdateReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.cameraproject_2.INVITATION_UPDATED".equals(intent.getAction())) {
                Log.d(TAG, "Received INVITATION_UPDATED broadcast");
                Intent updateIntent = new Intent(context, MainActivity.class);
                updateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                updateIntent.putExtra("UPDATE_MENU", true);
                context.startActivity(updateIntent);
            }
        }
    }

    public void notifyInvitationStatusChanged() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Intent intent = new Intent("com.example.cameraproject_2.INVITATION_UPDATED");
            if (context != null) {
                context.sendBroadcast(intent);
            } else {
                Log.e(TAG, "Context is null, cannot send broadcast");
            }
        });
    }
    public void deleteUserData(String userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("users", "userId = ?", new String[]{userId});
        db.close();
    }

    public interface SyncCallback {
        void onSyncComplete(boolean success);
    }

    public void syncInvitations(Context context, SyncCallback callback) {
        executorService.execute(() -> {
            try {
                // 檢查是否正在同步，避免重複執行
                if (isSyncing) {
                    Log.d(TAG, "Sync already in progress, skipping");
                    if (callback != null) callback.onSyncComplete(false);
                    return;
                }
                isSyncing = true;
                uploadUnsyncedInvitations();

                String userId = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        .getString("userId", null);
                if (userId == null) {
                    Log.e(TAG, "No userId found, skipping invitation sync");
                    if (callback != null) callback.onSyncComplete(false);
                    return;
                }
                String username = getUsernameFromUserId(userId);
                if (username == null) {
                    Log.e(TAG, "Cannot retrieve username for userId: " + userId + ", skipping sync");
                    if (callback != null) callback.onSyncComplete(false);
                    return;
                }
                String url = SERVER_URL + "/fetch_invitations.php?invited_user=" + Uri.encode(username);
                String response = fetchInvitationsFromServer(url);
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.getBoolean("success")) {
                    JSONArray data = jsonResponse.getJSONArray("data");
                    SQLiteDatabase db = getRegisterDatabase();
                    db.beginTransaction();
                    try {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject invitation = data.getJSONObject(i);
                            if (invitation.has("invitation_id") && !invitation.isNull("invitation_id")) {
                                String invitationId = invitation.getString("invitation_id");
                                String groupName = invitation.getString("group_name");
                                String invitedUser = invitation.getString("invited_user");
                                String status = invitation.getString("status");

                                Cursor checkCursor = db.query(TABLE_INVITATIONS,
                                        new String[]{COL_INVITATION_ID},
                                        COL_GROUP_NAME + " = ? AND " + COL_INVITED_USER + " = ?",
                                        new String[]{groupName, invitedUser},
                                        null, null, null);
                                boolean exists = checkCursor.moveToFirst();
                                checkCursor.close();

                                ContentValues values = new ContentValues();
                                values.put(COL_INVITATION_ID, invitationId);
                                values.put(COL_GROUP_NAME, groupName);
                                values.put(COL_INVITED_USER, invitedUser);
                                values.put(COL_STATUS, status);
                                values.put(COL_IS_SYNCED_INV, 1);

                                if (exists) {
                                    db.update(TABLE_INVITATIONS, values, COL_INVITATION_ID + " = ?", new String[]{invitationId});
                                    Log.d(TAG, "Updated existing invitation: " + invitationId);
                                } else {
                                    db.insertWithOnConflict(TABLE_INVITATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                                    Log.d(TAG, "Inserted new invitation: " + invitationId);
                                }
                            } else {
                                Log.e(TAG, "Invalid invitation data: invitationId is null, skipping");
                            }
                        }
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
                Log.d(TAG, "Invitation sync completed");
                if (callback != null) callback.onSyncComplete(true);
            } catch (Exception e) {
                Log.e(TAG, "Error syncing invitations: " + e.getMessage());
                if (callback != null) callback.onSyncComplete(false);
            } finally {
                isSyncing = false;
            }
        });
    }

    private boolean isSyncing = false; // 新增同步狀態標誌

    public static class RegistrationResult {
        public boolean success;
        public String id;

        public RegistrationResult(boolean success, String id) {
            this.success = success;
            this.id = id;
        }
    }

    public RegistrationResult registerUser(String username, String email, String password) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();

        String tempId;
        do {
            tempId = generateRandomId();
            Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID},
                    COL_ID + " = ?", new String[]{tempId}, null, null, null);
            if (cursor.getCount() == 0) {
                cursor.close();
                break;
            }
            cursor.close();
        } while (true);

        final String randomId = tempId;
        final String finalUsername = username;
        final String finalEmail = email;
        final String finalPassword = password;

        values.put(COL_ID, randomId);
        values.put(COL_USERNAME, finalUsername);
        values.put(COL_EMAIL, finalEmail);
        values.put(COL_PASSWORD, finalPassword);
        values.put(COL_LAST_MODIFIED, System.currentTimeMillis());
        values.put(COL_IS_SYNCED, 0);
        values.put(COL_SYNC_ACTION, "insert");

        long result = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) {
            Log.e(TAG, "Failed to register user: " + finalUsername);
            return new RegistrationResult(false, null);
        } else {
            Log.d(TAG, "User registered locally: " + finalUsername + " with ID: " + randomId);
            CountDownLatch latch = new CountDownLatch(1);
            executorService.execute(() -> {
                try {
                    uploadUserToServer(randomId, finalUsername, finalPassword, finalEmail);
                    Log.d(TAG, "User uploaded to server successfully");
                    updateSyncStatus(randomId, 1);
                    syncDatabaseInternal();
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Failed to upload user after registration: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() ->
                            showToast("Upload failed: " + e.getMessage()));
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for upload: " + e.getMessage());
            }
            return new RegistrationResult(true, randomId);
        }
    }

    private void uploadUserToServer(String id, String username, String password, String email) throws IOException, JSONException {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable, skipping upload");
            showToast("Network unavailable, upload will retry later");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("id", id);
        jsonBody.put("username", username);
        jsonBody.put("password", password);
        jsonBody.put("email", email != null ? email : JSONObject.NULL);
        jsonBody.put("last_modified", System.currentTimeMillis());
        jsonBody.put("is_synced", 0);
        jsonBody.put("sync_action", "insert");

        String fullUrl = SERVER_URL + "/register.php";
        Log.d(TAG, "Uploading user to: " + fullUrl);

        RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(fullUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body().string();
            Log.d(TAG, "Server response: " + responseData);
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to upload user: " + response.code() + " - " + response.message());
                throw new IOException("Upload failed: " + response.code() + " - " + response.message());
            }

            JSONObject jsonResponse = new JSONObject(responseData);
            if (!jsonResponse.getBoolean("success")) {
                Log.e(TAG, "Server registration failed: " + jsonResponse.getString("message"));
                throw new IOException(jsonResponse.getString("message"));
            }
        }
    }

    private void updateSyncStatus(String id, int isSynced) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_SYNCED, isSynced);
        db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{id});
        Log.d(TAG, "Updated sync status for ID: " + id + " to " + isSynced);
    }

    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME,
                new String[]{COL_ID, COL_PASSWORD},
                COL_USERNAME + "=? AND " + COL_PASSWORD + "=?",
                new String[]{username, password},
                null, null, null);

        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public String getUserId(String username, String password) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME,
                new String[]{COL_ID},
                COL_USERNAME + "=? AND " + COL_PASSWORD + "=?",
                new String[]{username, password},
                null, null, null);

        String userId = null;
        if (cursor.moveToFirst()) {
            userId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
        }
        cursor.close();
        return userId;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    public void syncDatabase(RegisterDatabaseHelper.SyncCallback callback) {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, skipping sync");
            showToast("無網絡連接，跳過同步");
            if (callback != null) callback.onSyncComplete(false);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        long currentTime = System.currentTimeMillis();

        executorService.execute(() -> {
            boolean success = false;
            try {
                syncDatabaseInternal(lastSyncTime);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(KEY_LAST_SYNC_TIME, currentTime);
                editor.apply();
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "Sync failed: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() ->
                        showToast("同步失敗: " + e.getMessage()));
            } finally {
                if (callback != null) {
                    boolean finalSuccess = success;
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onSyncComplete(finalSuccess));
                }
            }
        });
    }

    private void syncDatabaseInternal() throws IOException, JSONException {
        syncDatabaseInternal(System.currentTimeMillis());
    }

    private void syncDatabaseInternal(long lastSyncTime) throws IOException, JSONException {
        int retryCount = 0;
        final int maxRetries = 3;
        long backoffDelay = 2000;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                Log.d(TAG, "Starting database synchronization (attempt " + (retryCount + 1) + ")...");
                syncDatabase(null);
                fetchAndMergeUsersFromServer(lastSyncTime);
                Log.d(TAG, "Synchronization completed");
                return;
            } catch (Exception e) {
                retryCount++;
                lastException = e;
                Log.e(TAG, "Error syncing database (attempt " + retryCount + "): " + e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(backoffDelay);
                        backoffDelay *= 2;
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "Retry interrupted: " + ie.getMessage());
                    }
                }
            }
        }
        throw lastException != null ? new IOException("Sync failed after " + maxRetries + " attempts: " + lastException.getMessage(), lastException) : new IOException("Sync failed after " + maxRetries + " attempts");
    }

    private void fetchAndMergeUsersFromServer(long lastSyncTime) throws IOException, JSONException {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable, skipping download");
            showToast("Network unavailable, download will retry later");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String fullUrl = SERVER_URL + "/fetch_user.php"; // 修正為您提供的 fetch_user.php
        Log.d(TAG, "Fetching users from: " + fullUrl);

        Request request = new Request.Builder()
                .url(fullUrl)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body().string();
            Log.d(TAG, "Full fetch users response: " + responseData);
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to fetch users: " + response.code() + " - " + response.message());
                throw new IOException("Fetch failed: " + response.code() + " - " + response.message());
            }

            JSONObject jsonResponse = new JSONObject(responseData);
            if (jsonResponse.getBoolean("success")) {
                JSONArray users = jsonResponse.getJSONArray("data");
                SQLiteDatabase db = getRegisterDatabase();
                db.beginTransaction();
                try {
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject user = users.getJSONObject(i);
                        String id = user.getString(COL_ID);
                        String username = user.getString(COL_USERNAME);
                        String email = user.isNull(COL_EMAIL) ? null : user.getString(COL_EMAIL);
                        String password = user.getString(COL_PASSWORD);
                        long lastModified = user.getLong(COL_LAST_MODIFIED);
                        int isSynced = user.getInt(COL_IS_SYNCED);
                        String syncAction = user.isNull(COL_SYNC_ACTION) ? null : user.getString(COL_SYNC_ACTION);
                        String profileImageUrl = user.isNull(COL_PROFILE_IMAGE_URL) ? null : user.getString(COL_PROFILE_IMAGE_URL);

                        ContentValues values = new ContentValues();
                        values.put(COL_ID, id);
                        values.put(COL_USERNAME, username);
                        values.put(COL_EMAIL, email);
                        values.put(COL_PASSWORD, password);
                        values.put(COL_LAST_MODIFIED, lastModified);
                        values.put(COL_IS_SYNCED, isSynced);
                        values.put(COL_SYNC_ACTION, syncAction);
                        values.put(COL_PROFILE_IMAGE_URL, profileImageUrl);

                        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                        Log.d(TAG, "Merged user with ID: " + id + " (overwritten if duplicate)");
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } else {
                Log.e(TAG, "Server returned failure: " + jsonResponse.getString("message"));
                throw new IOException(jsonResponse.getString("message"));
            }
        }
    }



    public void addGroupInvitation(String groupName, String invitedUserId, SyncCallback callback) {
        SQLiteDatabase db = getRegisterDatabase();
        if (invitedUserId == null) {
            Log.e(TAG, "Invited user ID is null");
            if (callback != null) callback.onSyncComplete(false);
            return;
        }

        Cursor checkCursor = db.query(TABLE_INVITATIONS, new String[]{COL_STATUS},
                COL_GROUP_NAME + " = ? AND " + COL_INVITED_USER + " = ?",
                new String[]{groupName, invitedUserId}, null, null, null);
        if (checkCursor.moveToFirst() && "pending".equals(checkCursor.getString(checkCursor.getColumnIndexOrThrow(COL_STATUS)))) {
            checkCursor.close();
            Log.d(TAG, "Pending invitation already exists for user: " + invitedUserId + ", group: " + groupName);
            if (callback != null) callback.onSyncComplete(true);
            return;
        }
        checkCursor.close();

        String invitationId = generateRandomId();
        ContentValues values = new ContentValues();
        values.put(COL_INVITATION_ID, invitationId);
        values.put(COL_GROUP_NAME, groupName);
        values.put(COL_INVITED_USER, invitedUserId);
        values.put(COL_STATUS, "pending");
        values.put(COL_IS_SYNCED_INV, 0);

        long result = db.insert(TABLE_INVITATIONS, null, values);
        if (result != -1) {
            Log.d(TAG, "Group invitation added for user: " + invitedUserId + ", group: " + groupName + ", invitationId: " + invitationId);
            syncInvitations(context, callback);
        } else {
            Log.e(TAG, "Failed to add group invitation for user: " + invitedUserId);
            if (callback != null) callback.onSyncComplete(false);
        }
    }

    public List<Invitation> getPendingInvitations(String userId) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS,
                new String[]{COL_INVITATION_ID, COL_GROUP_NAME, COL_STATUS},
                COL_INVITED_USER + " = ? AND " + COL_STATUS + " = ?",
                new String[]{userId, "pending"},
                null, null, null);

        List<Invitation> invitations = new ArrayList<>();
        while (cursor.moveToNext()) {
            String invitationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITATION_ID));
            String groupName = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
            String status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));
            Log.d(TAG, "Found pending invitation: id=" + invitationId + ", group=" + groupName + ", user=" + userId + ", status=" + status);
            invitations.add(new Invitation(invitationId, groupName, status));
        }
        cursor.close();
        Log.d(TAG, "Pending invitations for userId " + userId + ": " + invitations.size());
        return invitations;
    }

    public interface OnMessageInsertedListener {
        void onMessageInserted(String groupName);
    }

    private OnMessageInsertedListener messageListener;

    public void setOnMessageInsertedListener(OnMessageInsertedListener listener) {
        this.messageListener = listener;
    }

    public void checkInvitationStatus(String userId) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_INVITATIONS +
                        " WHERE " + COL_INVITED_USER + " = ? AND " + COL_STATUS + " = ?",
                new String[]{userId, "pending"});
        if (cursor.getCount() > 0) {
            Log.d("Invitation", "New invitation found for " + userId);
            if (cursor.moveToFirst()) {
                do {
                    String groupName = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
                    // 通知發送由調用者負責，這裡僅記錄邏輯
                    Log.d(TAG, "Invitation found for userId: " + userId + ", group: " + groupName);
                } while (cursor.moveToNext());
            }
        }
        cursor.close();
    }

    public void showNotification(Context context, String userId, String groupName, boolean hasNotificationPermission) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "invitation_channel";
            CharSequence channelName = "Invitation Notifications";
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("UPDATE_MENU", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "invitation_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("新群組邀請")
                .setContentText("您收到來自群組 " + groupName + " 的邀請")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (hasNotificationPermission) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            Log.d(TAG, "Notification sent for userId: " + userId + ", group: " + groupName);
        } else {
            Log.w(TAG, "Notification permission not granted for userId: " + userId);
        }
    }

    public void insertMessage(String messageId, String groupName, String sender, String message, long timestamp) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_ID, messageId);
        values.put(COL_GROUP_NAME, groupName);
        values.put(COL_SENDER, sender);
        values.put(COL_MESSAGE, message);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_IS_SYNCED_MSG, 0);

        long result = db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (result != -1) {
            Log.d(TAG, "Message inserted into local database: " + messageId);
            if (messageListener != null && groupName != null) {
                messageListener.onMessageInserted(groupName); // 通知監聽器
            }
        } else {
            Log.e(TAG, "Failed to insert message into local database: " + messageId);
        }
    }

    private String fetchInvitationsFromServer(String url) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch invitations: " + response.code() + " - " + response.message());
            }
            return response.body().string();
        }
    }

    public void updateMessageSyncStatus(String messageId, int syncStatus) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_SYNCED_MSG, syncStatus);

        int rowsUpdated = db.update(TABLE_MESSAGES, values, COL_MESSAGE_ID + " = ?", new String[]{messageId});
        if (rowsUpdated > 0) {
            Log.d(TAG, "Updated sync status for messageId: " + messageId + " to " + syncStatus);
        } else {
            Log.e(TAG, "Failed to update sync status for messageId: " + messageId);
        }
    }

    public String getPendingInvitationId(String userId, String groupName) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS,
                new String[]{COL_INVITATION_ID},
                COL_INVITED_USER + " = ? AND " + COL_GROUP_NAME + " = ? AND " + COL_STATUS + " = ?",
                new String[]{userId, groupName, "pending"},
                null, null, null);

        String invitationId = null;
        if (cursor.moveToFirst()) {
            invitationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITATION_ID));
            Log.d(TAG, "Found pending invitation ID: " + invitationId + " for user: " + userId + ", group: " + groupName);
        } else {
            Log.d(TAG, "No pending invitation found for user: " + userId + ", group: " + groupName);
        }
        cursor.close();
        return invitationId;
    }

    private void uploadUnsyncedInvitations() throws IOException, JSONException {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS, null, COL_IS_SYNCED_INV + " = 0", null, null, null, null);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        db.beginTransaction();
        try {
            while (cursor.moveToNext()) {
                String invitationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITATION_ID));
                String groupName = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
                String invitedUser = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITED_USER));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));

                if (invitationId == null || groupName == null || invitedUser == null || status == null) {
                    Log.e(TAG, "Invalid invitation data: invitationId=" + invitationId + ", groupName=" + groupName + ", invitedUser=" + invitedUser + ", status=" + status);
                    continue;
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put(COL_INVITATION_ID, invitationId);
                jsonBody.put(COL_GROUP_NAME, groupName);
                jsonBody.put(COL_INVITED_USER, invitedUser);
                jsonBody.put(COL_STATUS, status);

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(SERVER_URL + "/sync_invitations.php")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.getBoolean("success")) {
                            Log.d(TAG, "Uploaded invitation with ID: " + invitationId);
                            ContentValues values = new ContentValues();
                            values.put(COL_IS_SYNCED_INV, 1);
                            db.update(TABLE_INVITATIONS, values, COL_INVITATION_ID + " = ?", new String[]{invitationId});
                        } else {
                            Log.e(TAG, "Server rejected invitation with ID: " + invitationId + ", message: " + jsonResponse.getString("message"));
                            throw new IOException(jsonResponse.getString("message"));
                        }
                    } else {
                        Log.e(TAG, "Failed to upload invitation with ID: " + invitationId + ": " + response.code() + " - " + response.message());
                        throw new IOException("Upload failed: " + response.code() + " - " + response.message());
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            cursor.close();
        }
    }

    private void fetchAndMergeInvitationsFromServer() throws IOException, JSONException {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable, skipping invitation download");
            showToast("Network unavailable, invitation download will retry later");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String fullUrl = SERVER_URL + "/fetch_invitations.php";
        Log.d(TAG, "Fetching invitations from: " + fullUrl);

        Request request = new Request.Builder()
                .url(fullUrl)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body().string();
            Log.d(TAG, "Full fetch invitations response: " + responseData);
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to fetch invitations: " + response.code() + " - " + response.message());
                throw new IOException("Fetch failed: " + response.code() + " - " + response.message());
            }

            JSONObject jsonResponse = new JSONObject(responseData);
            if (jsonResponse.getBoolean("success")) {
                JSONArray invitations = jsonResponse.getJSONArray("data");
                SQLiteDatabase db = getRegisterDatabase();
                db.beginTransaction();
                try {
                    for (int i = 0; i < invitations.length(); i++) {
                        JSONObject invitation = invitations.getJSONObject(i);
                        String invitationId = invitation.getString(COL_INVITATION_ID);
                        String groupName = invitation.getString(COL_GROUP_NAME);
                        String invitedUser = invitation.getString(COL_INVITED_USER);
                        String status = invitation.getString(COL_STATUS);

                        Cursor cursor = db.query(TABLE_INVITATIONS,
                                new String[]{COL_INVITED_USER},
                                COL_INVITATION_ID + "=?",
                                new String[]{invitationId},
                                null, null, null);
                        boolean hasLocalRecord = cursor.moveToFirst();
                        cursor.close();

                        if (hasLocalRecord) {
                            Log.d(TAG, "Skipping merge for invitation with ID: " + invitationId + " (local record exists)");
                            continue;
                        }

                        ContentValues values = new ContentValues();
                        values.put(COL_INVITATION_ID, invitationId);
                        values.put(COL_GROUP_NAME, groupName);
                        values.put(COL_INVITED_USER, invitedUser);
                        values.put(COL_STATUS, status);
                        values.put(COL_IS_SYNCED_INV, 1);

                        db.insertWithOnConflict(TABLE_INVITATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                        Log.d(TAG, "Merged invitation with ID: " + invitationId);
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } else {
                Log.e(TAG, "Server returned failure: " + jsonResponse.getString("message"));
                throw new IOException(jsonResponse.getString("message"));
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        closeDatabase();
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    public static String getServerUrl() {
        return SERVER_URL;
    }
}