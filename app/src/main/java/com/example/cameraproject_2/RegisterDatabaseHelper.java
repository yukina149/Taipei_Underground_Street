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
import java.util.Random;
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
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_NAME = "Users";
    public static final String COL_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_LAST_MODIFIED = "last_modified";
    public static final String COL_IS_SYNCED = "is_synced";
    public static final String COL_SYNC_ACTION = "sync_action";

    private final Context context;
    private SQLiteDatabase registerDatabase;

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static String SERVER_URL = "https://549b-61-71-118-80.ngrok-free.app/android_studio"; // Replace with your active Ngrok URL

    public RegisterDatabaseHelper(Context context) {
        super(context, REGISTER_DB_NAME, null, DATABASE_VERSION);
        this.context = context;
        loadServerUrl();
    }

    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultUrl = "https://549b-61-71-118-80.ngrok-free.app/android_studio"; // Replace with your active Ngrok URL
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
    }

    // Modified to use getWritableDatabase() instead of manually opening the database
    public SQLiteDatabase getRegisterDatabase() {
        if (registerDatabase == null || !registerDatabase.isOpen()) {
            registerDatabase = getWritableDatabase(); // This ensures the database is created
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
            executorService.execute(() -> {
                try {
                    uploadUserToServer(randomId, finalUsername, finalPassword, finalEmail);
                    Log.d(TAG, "User uploaded to server successfully");
                    updateSyncStatus(randomId, 1);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Failed to upload user after registration: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() ->
                            showToast("Upload failed: " + e.getMessage()));
                }
            });
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

    public void syncDatabase() {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, skipping sync");
            showToast("No network available, sync skipped");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        long currentTime = System.currentTimeMillis();

        executorService.execute(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            boolean success = false;
            long backoffDelay = 2000;

            while (retryCount < maxRetries && !success) {
                try {
                    Log.d(TAG, "Starting database synchronization (attempt " + (retryCount + 1) + ")...");
                    uploadUnsyncedUsers();
                    fetchAndMergeUsersFromServer(lastSyncTime);
                    Log.d(TAG, "Synchronization completed");

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(KEY_LAST_SYNC_TIME, currentTime);
                    editor.apply();

                    success = true;
                } catch (Exception e) {
                    retryCount++;
                    Log.e(TAG, "Error syncing database (attempt " + retryCount + "): " + e.getMessage());
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(backoffDelay);
                            backoffDelay *= 2;
                        } catch (InterruptedException ie) {
                            Log.e(TAG, "Retry interrupted: " + ie.getMessage());
                        }
                    } else {
                        showToast("Sync failed after " + maxRetries + " attempts: " + e.getMessage());
                    }
                }
            }
        });
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
            Log.d(TAG, "Fetch users response: " + responseData);
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
                        String syncAction = user.getString(COL_SYNC_ACTION);

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

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        closeDatabase();
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}