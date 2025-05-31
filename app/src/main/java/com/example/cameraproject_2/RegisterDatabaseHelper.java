package com.example.cameraproject_2;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "RegisterDatabaseHelper";
    public static final String REGISTER_DB_NAME = "register.db";
    private static final int DATABASE_VERSION = 5;

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

    private final Context context;
    private SQLiteDatabase registerDatabase;

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static String SERVER_URL = "http://192.168.10.15/android_studio";

    public RegisterDatabaseHelper(Context context) {
        super(context, REGISTER_DB_NAME, null, DATABASE_VERSION);
        this.context = context;
        loadServerUrl();
        checkDatabaseIntegrity();
    }

    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultUrl = "http://192.168.10.15/android_studio";
        SERVER_URL = prefs.getString(KEY_SERVER_URL, defaultUrl);
        Log.d(TAG, "Loaded server URL from prefs: " + SERVER_URL);
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
                COL_SYNC_ACTION + " TEXT)";
        db.execSQL(createTable);
        Log.d(TAG, "Database created with table: " + TABLE_NAME);

        String createInvitationsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_INVITATIONS + " (" +
                COL_INVITATION_ID + " TEXT PRIMARY KEY, " +
                COL_GROUP_NAME + " TEXT NOT NULL, " +
                COL_INVITED_USER + " TEXT NOT NULL, " +
                COL_STATUS + " TEXT NOT NULL, " +
                COL_IS_SYNCED_INV + " INTEGER DEFAULT 0, " +
                "UNIQUE(" + COL_GROUP_NAME + ", " + COL_INVITED_USER + "))";
        db.execSQL(createInvitationsTable);
        Log.d(TAG, "Database created with table: " + TABLE_INVITATIONS);

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_invited_user ON " + TABLE_INVITATIONS + " (" + COL_INVITED_USER + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
                    "UNIQUE(" + COL_GROUP_NAME + ", " + COL_INVITED_USER + "))";
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
    }

    public SQLiteDatabase getRegisterDatabase() {
        if (registerDatabase == null || !registerDatabase.isOpen()) {
            registerDatabase = getWritableDatabase();
        }
        return registerDatabase;
    }

    public void closeDatabase() {
        if (registerDatabase != null && registerDatabase.isOpen()) {
            registerDatabase.close();
            registerDatabase = null;
        }
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private String generateRandomId() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder randomId = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            randomId.append(characters.charAt(random.nextInt(characters.length())));
        }
        return randomId.toString();
    }

    private String getUserIdFromUsername(String username) {
        SQLiteDatabase db = getRegisterDatabase();
        Log.d(TAG, "Querying userId for username: " + username);
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID},
                COL_USERNAME + "=?", new String[]{username}, null, null, null);
        String userId = null;
        if (cursor.moveToFirst()) {
            userId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
            Log.d(TAG, "Found userId: " + userId + " for username: " + username);
        } else {
            Log.e(TAG, "No userId found for username: " + username);
        }
        cursor.close();
        return userId;
    }

    private String getUsernameFromUserId(String userId) {
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
        values.put(COL_IS_SYNCED_INV, 0); // 標記為未同步，觸發同步
        int rowsAffected = db.update(TABLE_INVITATIONS, values, COL_INVITATION_ID + " = ?", new String[]{invitationId});
        if (rowsAffected > 0) {
            Log.d(TAG, "Updated invitation ID: " + invitationId + " to status: " + status);
            syncInvitations(); // 同步到伺服器
            // 立即刷新本地邀請狀態，確保 UI 反映最新狀態
            notifyInvitationStatusChanged(); // 新增方法，通知 UI 更新
        } else {
            Log.e(TAG, "Failed to update invitation ID: " + invitationId);
        }
    }

    // 新增方法，用於通知 UI 更新邀請狀態
    private void notifyInvitationStatusChanged() {
        // 這裡可以觸發一個廣播或回調，通知 UI 重新加載邀請列表
        // 例如，使用 LocalBroadcastManager 或介面回調
        Log.d(TAG, "Notifying UI of invitation status change");
    }

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

    public boolean syncDatabase() {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, skipping sync");
            showToast("No network available, sync skipped");
            return false;
        }

        CountDownLatch latch = new CountDownLatch(1);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        long currentTime = System.currentTimeMillis();

        final boolean[] success = {false};
        executorService.execute(() -> {
            try {
                syncDatabaseInternal(lastSyncTime);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(KEY_LAST_SYNC_TIME, currentTime);
                editor.apply();
                success[0] = true;
            } catch (Exception e) {
                Log.e(TAG, "Sync failed: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() ->
                        showToast("Sync failed: " + e.getMessage()));
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for sync: " + e.getMessage());
            return false;
        }
        return success[0];
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
                uploadUnsyncedUsers();
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

        String fullUrl = SERVER_URL + "/fetch_users.php";
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

                        ContentValues values = new ContentValues();
                        values.put(COL_ID, id);
                        values.put(COL_USERNAME, username);
                        values.put(COL_EMAIL, email);
                        values.put(COL_PASSWORD, password);
                        values.put(COL_LAST_MODIFIED, lastModified);
                        values.put(COL_IS_SYNCED, isSynced);
                        values.put(COL_SYNC_ACTION, syncAction);

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

    private void uploadUnsyncedUsers() throws IOException, JSONException {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COL_IS_SYNCED + " = 0", null, null, null, null);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        db.beginTransaction();
        try {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
                String email = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL));
                String password = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD));
                long lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_MODIFIED));
                int isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SYNCED));
                String syncAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_ACTION));

                JSONObject jsonBody = new JSONObject();
                jsonBody.put(COL_ID, id);
                jsonBody.put(COL_USERNAME, username);
                jsonBody.put(COL_EMAIL, email != null ? email : JSONObject.NULL);
                jsonBody.put(COL_PASSWORD, password);
                jsonBody.put(COL_LAST_MODIFIED, lastModified);
                jsonBody.put(COL_IS_SYNCED, isSynced);
                jsonBody.put(COL_SYNC_ACTION, syncAction);

                RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(SERVER_URL + "/register.php")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.getBoolean("success")) {
                            updateSyncStatus(id, 1);
                            Log.d(TAG, "Uploaded unsynced user with ID: " + id);
                        } else {
                            Log.e(TAG, "Server rejected user with ID: " + id + ", message: " + jsonResponse.getString("message"));
                            throw new IOException(jsonResponse.getString("message"));
                        }
                    } else {
                        Log.e(TAG, "Failed to upload user with ID: " + id + ": " + response.code() + " - " + response.message());
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

    public void addGroupInvitation(String groupName, String invitedUser) {
        SQLiteDatabase db = getRegisterDatabase();

        // 檢查 invitedUser 是否為有效的使用者名稱
        String userId = getUserIdFromUsername(invitedUser);
        if (userId == null) {
            Log.e(TAG, "Invalid invitedUser: " + invitedUser + ", no userId found");
            return;
        }

        // 檢查是否已存在相同的邀請
        Cursor checkCursor = db.query(TABLE_INVITATIONS, new String[]{COL_STATUS},
                COL_GROUP_NAME + " = ? AND " + COL_INVITED_USER + " = ?",
                new String[]{groupName, invitedUser}, null, null, null);
        if (checkCursor.moveToFirst()) {
            String status = checkCursor.getString(checkCursor.getColumnIndexOrThrow(COL_STATUS));
            checkCursor.close();
            if ("pending".equals(status)) {
                Log.d(TAG, "Pending invitation already exists for user: " + invitedUser + " to group: " + groupName);
                return; // 已經有 pending 邀請，無需重複添加
            } else {
                // 更新現有記錄
                ContentValues updateValues = new ContentValues();
                updateValues.put(COL_STATUS, "pending");
                updateValues.put(COL_IS_SYNCED_INV, 0);
                db.update(TABLE_INVITATIONS, updateValues,
                        COL_GROUP_NAME + " = ? AND " + COL_INVITED_USER + " = ?",
                        new String[]{groupName, invitedUser});
                Log.d(TAG, "Updated existing invitation for user: " + invitedUser + " to group: " + groupName);
                syncInvitations();
                return;
            }
        }
        checkCursor.close();

        // 生成唯一的 invitationId
        String invitationId;
        do {
            invitationId = generateRandomId();
            Cursor cursor = db.query(TABLE_INVITATIONS, new String[]{COL_INVITATION_ID},
                    COL_INVITATION_ID + " = ?", new String[]{invitationId}, null, null, null);
            if (cursor.getCount() == 0) {
                cursor.close();
                break;
            }
            cursor.close();
        } while (true);

        ContentValues values = new ContentValues();
        values.put(COL_INVITATION_ID, invitationId);
        values.put(COL_GROUP_NAME, groupName);
        values.put(COL_INVITED_USER, invitedUser);
        values.put(COL_STATUS, "pending");
        values.put(COL_IS_SYNCED_INV, 0);

        long result = db.insert(TABLE_INVITATIONS, null, values);
        if (result == -1) {
            Log.e(TAG, "Failed to add group invitation for user: " + invitedUser);
        } else {
            Log.d(TAG, "Group invitation added for user: " + invitedUser + " to group: " + groupName + ", invitationId: " + invitationId);
            syncInvitations();
        }
    }

    public List<Invitation> getPendingInvitations(String userId) {
        syncInvitations(); // 確保最新狀態

        String username = getUsernameFromUserId(userId);
        if (username == null) {
            Log.e(TAG, "Cannot retrieve username for userId: " + userId + ", returning empty invitation list");
            return new ArrayList<>();
        }

        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_INVITATIONS,
                new String[]{COL_INVITATION_ID, COL_GROUP_NAME, COL_INVITED_USER},
                COL_INVITED_USER + "=? AND " + COL_STATUS + "=?",
                new String[]{username, "pending"}, // 只返回 pending 狀態
                null, null, null);

        List<Invitation> invitations = new ArrayList<>();
        while (cursor.moveToNext()) {
            String invitationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITATION_ID));
            String groupName = cursor.getString(cursor.getColumnIndexOrThrow(COL_GROUP_NAME));
            String invitedUser = cursor.getString(cursor.getColumnIndexOrThrow(COL_INVITED_USER));
            Log.d(TAG, "Found pending invitation: invitationId=" + invitationId + ", groupName=" + groupName + ", invitedUser=" + invitedUser);
            invitations.add(new Invitation(invitationId, groupName));
        }
        cursor.close();
        Log.d(TAG, "Pending invitations for userId " + userId + " (username: " + username + "): " + invitations.size());
        return invitations;
    }

    public void syncInvitations() {
        new Thread(() -> {
            try {
                // 先上傳本地未同步的邀請
                uploadUnsyncedInvitations();

                // 從伺服器拉取最新的邀請資料，並根據當前使用者過濾
                String username = getUsernameFromUserId("1"); // 假設 hilda111 的 userId 是 1
                if (username == null) {
                    Log.e(TAG, "Cannot retrieve username for userId: 1, skipping sync");
                    return;
                }
                String url = SERVER_URL + "/fetch_invitations.php?invited_user=" + username;
                String response = fetchInvitationsFromServer(url);
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.getBoolean("success")) {
                    JSONArray data = jsonResponse.getJSONArray("data");
                    SQLiteDatabase db = getRegisterDatabase();
                    db.beginTransaction();
                    try {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject invitation = data.getJSONObject(i);
                            String invitationId = invitation.getString("invitation_id");
                            String groupName = invitation.getString("group_name");
                            String invitedUser = invitation.getString("invited_user");
                            String status = invitation.getString("status");

                            // 檢查本地是否已有該邀請
                            Cursor cursor = db.query(TABLE_INVITATIONS,
                                    new String[]{COL_INVITATION_ID},
                                    COL_INVITATION_ID + " = ?",
                                    new String[]{invitationId},
                                    null, null, null);
                            boolean exists = cursor.moveToFirst();
                            cursor.close();

                            ContentValues values = new ContentValues();
                            values.put(COL_INVITATION_ID, invitationId);
                            values.put(COL_GROUP_NAME, groupName);
                            values.put(COL_INVITED_USER, invitedUser);
                            values.put(COL_STATUS, status);
                            values.put(COL_IS_SYNCED_INV, 1);

                            if (exists) {
                                db.update(TABLE_INVITATIONS, values, COL_INVITATION_ID + " = ?", new String[]{invitationId});
                                Log.d(TAG, "Updated invitation: " + invitationId);
                            } else {
                                db.insert(TABLE_INVITATIONS, null, values);
                                Log.d(TAG, "Inserted new invitation: " + invitationId);
                            }
                        }
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
                Log.d(TAG, "Invitation sync completed");
            } catch (Exception e) {
                Log.e(TAG, "Error syncing invitations: " + e.getMessage());
            }
        }).start();
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
}