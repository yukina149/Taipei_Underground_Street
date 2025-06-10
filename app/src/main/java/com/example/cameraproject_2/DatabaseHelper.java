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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    public static final String PICTURE_DB_NAME = "picture.db";
    public static final String REGISTER_DB_NAME = "register.db";
    private static final int DATABASE_VERSION = 2;
    private static final String DB_PATH = "/data/data/com.example.cameraproject_2/databases";

    public static final String TABLE_NAME = "Users";
    public static final String COL_USERNAME = "username";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_LAST_MODIFIED = "last_modified";
    public static final String COL_IS_SYNCED = "is_synced";
    public static final String COL_SYNC_ACTION = "sync_action";

    private final Context context;
    private SQLiteDatabase pictureDatabase;
    private SQLiteDatabase registerDatabase;

    private String dbPath;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time"; // 追蹤最後同步時間

    // 使用 ExecutorService 管理背景線程
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public DatabaseHelper(Context context) {
        super(context, REGISTER_DB_NAME, null, DATABASE_VERSION);
        this.context = context;
        this.dbPath = context.getDatabasePath(REGISTER_DB_NAME).getPath();
        //setServerUrl("http://192.168.10.15:8080"); // 強制設置正確 URL，192.168.10.15
        setServerUrl("http://192.168.234.200/android_studio/register.php"); // 模擬器
        loadServerUrl();
    }

    /*
    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultUrl = "http://192.168.10.15:8080";
        SERVER_URL = prefs.getString(KEY_SERVER_URL, defaultUrl);
        Log.d(TAG, "Loaded server URL from prefs: " + SERVER_URL);
    }

     */


    private void loadServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 如果你在模擬器上運行，使用 10.0.2.2；如果在真機上運行，使用你電腦的 IP
        String defaultUrl = "http://192.168.234.200/android_studio/register.php"; // 模擬器
        // String defaultUrl = "http://192.168.1.100/android_studio"; // 真機
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
                "id TEXT PRIMARY KEY, " +
                COL_USERNAME + " TEXT NOT NULL UNIQUE, " +
                COL_EMAIL + " TEXT NOT NULL, " +
                COL_PASSWORD + " TEXT NOT NULL, " +
                COL_LAST_MODIFIED + " INTEGER, " +
                COL_IS_SYNCED + " INTEGER DEFAULT 0, " +
                COL_SYNC_ACTION + " TEXT)";
        db.execSQL(createTable);
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
    }

    public void createDataBase(String dbName) throws IOException {
        boolean dbExists = checkDataBase(dbName);
        Log.d(TAG, "Checking if database " + dbName + " exists: " + dbExists);

        if (!dbExists) {
            try {
                copyDataBase(dbName);
                if (dbName.equals(PICTURE_DB_NAME)) {
                    copyImages();
                    verifyDatabase(dbName); // Verify the database after copying
                }
                Log.d(TAG, "Database " + dbName + " created and copied successfully");
            } catch (IOException e) {
                Log.e(TAG, "Error copying database " + dbName + ": " + e.getMessage());
                throw new Error("Error copying database " + dbName + ": " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Database " + dbName + " already exists, skipping copy");
            if (dbName.equals(PICTURE_DB_NAME)) {
                verifyDatabase(dbName); // Verify the database if it already exists
            }
        }
    }

    private boolean checkDataBase(String dbName) {
        File dbFile;
        if (dbName.equals(PICTURE_DB_NAME)) {
            dbFile = new File(DB_PATH + "/" + PICTURE_DB_NAME);
        } else {
            dbFile = new File(context.getDatabasePath(dbName).getPath());
        }
        return dbFile.exists();
    }

    private void copyDataBase(String dbName) throws IOException {
        InputStream input = context.getAssets().open(dbName);
        File outputFile;
        if (dbName.equals(PICTURE_DB_NAME)) {
            File dir = new File(DB_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
                Log.d(TAG, "Created directory: " + DB_PATH);
            }
            outputFile = new File(DB_PATH + "/" + PICTURE_DB_NAME);
        } else {
            outputFile = new File(context.getDatabasePath(dbName).getPath());
        }
        OutputStream output = new FileOutputStream(outputFile);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = input.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }

        output.flush();
        output.close();
        input.close();
    }

    private void verifyDatabase(String dbName) {
        SQLiteDatabase db = null;
        try {
            String path = dbName.equals(PICTURE_DB_NAME) ? (DB_PATH + "/" + PICTURE_DB_NAME) : context.getDatabasePath(dbName).getPath();
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='picture_data'", null);
            boolean tableExists = cursor.moveToFirst();
            cursor.close();
            if (!tableExists) {
                Log.e(TAG, "Table picture_data does not exist in " + dbName);
            } else {
                Log.d(TAG, "Table picture_data verified in " + dbName);
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error verifying database " + dbName + ": " + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    public SQLiteDatabase openDataBase(String dbName) {
        if (dbName.equals(PICTURE_DB_NAME)) {
            if (pictureDatabase == null || !pictureDatabase.isOpen()) {
                pictureDatabase = SQLiteDatabase.openDatabase(
                        DB_PATH + "/" + PICTURE_DB_NAME, null, SQLiteDatabase.OPEN_READWRITE);
                Log.d(TAG, "Opened pictureDatabase at: " + (DB_PATH + "/" + PICTURE_DB_NAME));
            }
            return pictureDatabase;
        } else if (dbName.equals(REGISTER_DB_NAME)) {
            if (registerDatabase == null || !registerDatabase.isOpen()) {
                registerDatabase = SQLiteDatabase.openDatabase(
                        context.getDatabasePath(REGISTER_DB_NAME).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
                Log.d(TAG, "Opened registerDatabase at: " + context.getDatabasePath(REGISTER_DB_NAME).getPath());
            }
            return registerDatabase;
        }
        throw new IllegalArgumentException("Unknown database name: " + dbName);
    }

    public void closeDatabase() {
        if (pictureDatabase != null && pictureDatabase.isOpen()) {
            pictureDatabase.close();
        }
        if (registerDatabase != null && registerDatabase.isOpen()) {
            registerDatabase.close();
        }
        // 關閉 ExecutorService
        executorService.shutdown();
    }

    public void copyImages() {
        File dir = new File(context.getFilesDir(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            String[] files = context.getAssets().list("images");
            if (files != null) {
                for (String file : files) {
                    File targetFile = new File(dir, file);
                    if (targetFile.exists()) {
                        continue;
                    }
                    InputStream input = context.getAssets().open("images/" + file);
                    OutputStream output = new FileOutputStream(targetFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                    output.flush();
                    output.close();
                    input.close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying images: " + e.getMessage());
        }
    }

    public SQLiteDatabase getRegisterDatabase() {
        if (registerDatabase == null || !registerDatabase.isOpen()) {
            registerDatabase = SQLiteDatabase.openDatabase(
                    context.getDatabasePath(REGISTER_DB_NAME).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        }
        return registerDatabase;
    }

    public SQLiteDatabase getPictureDatabase() {
        if (pictureDatabase == null || !pictureDatabase.isOpen()) {
            pictureDatabase = SQLiteDatabase.openDatabase(
                    DB_PATH + "/" + PICTURE_DB_NAME, null, SQLiteDatabase.OPEN_READWRITE);
            Log.d(TAG, "getPictureDatabase opened at: " + (DB_PATH + "/" + PICTURE_DB_NAME));
        }
        return pictureDatabase;
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

        String randomId;
        do {
            randomId = generateRandomId();
            Cursor cursor = db.query(TABLE_NAME, new String[]{"id"},
                    "id = ?", new String[]{randomId}, null, null, null);
            if (cursor.getCount() == 0) {
                cursor.close();
                break;
            }
            cursor.close();
        } while (true);

        values.put("id", randomId);
        values.put(COL_USERNAME, username);
        values.put(COL_EMAIL, email);
        values.put(COL_PASSWORD, password);
        values.put(COL_LAST_MODIFIED, System.currentTimeMillis());
        values.put(COL_IS_SYNCED, 0);
        values.put(COL_SYNC_ACTION, "insert");

        long result = db.insert(TABLE_NAME, null, values);
        if (result == -1) {
            Log.e(TAG, "Failed to register user: " + username);
            return new RegistrationResult(false, null);
        } else {
            Log.d(TAG, "User registered: " + username + " with ID: " + randomId);
            // 在背景線程中執行上傳
            executorService.execute(() -> {
                try {
                    uploadDatabase();
                    Log.d(TAG, "Database uploaded successfully in background");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to upload database after registration: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() ->
                            showToast("Upload failed: " + e.getMessage()));
                }
            });
            return new RegistrationResult(true, randomId);
        }
    }

    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME,
                new String[]{COL_USERNAME, COL_PASSWORD},
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
                new String[]{"id"},
                COL_USERNAME + "=? AND " + COL_PASSWORD + "=?",
                new String[]{username, password},
                null, null, null);

        String userId = null;
        if (cursor.moveToFirst()) {
            userId = cursor.getString(cursor.getColumnIndexOrThrow("id"));
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

        new Thread(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            boolean success = false;

            while (retryCount < maxRetries && !success) {
                try {
                    Log.d(TAG, "Starting database synchronization (attempt " + (retryCount + 1) + ")...");

                    // 檢查伺服器是否有更新
                    downloadAndMergeDatabaseIfNeeded(lastSyncTime);
                    Log.d(TAG, "Checked and merged central database if needed");

                    // 上傳本地資料庫
                    uploadDatabase();
                    Log.d(TAG, "Database uploaded successfully");

                    // 更新最後同步時間
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(KEY_LAST_SYNC_TIME, currentTime);
                    editor.apply();

                    success = true;
                } catch (Exception e) {
                    retryCount++;
                    Log.e(TAG, "Error syncing database (attempt " + retryCount + "): " + e.getMessage());
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(2000); // 等待 2 秒後重試
                        } catch (InterruptedException ie) {
                            Log.e(TAG, "Retry interrupted: " + ie.getMessage());
                        }
                    } else {
                        showToast("Sync failed after " + maxRetries + " attempts: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void downloadAndMergeDatabaseIfNeeded(long lastSyncTime) throws IOException {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable, skipping download");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(SERVER_URL + "/download")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to download central database: " + response.code() + " - " + response.message());
                throw new IOException("Download failed: " + response.code() + " - " + response.message());
            }
            Log.d(TAG, "Downloaded central database successfully");
            File tempFile = new File(context.getFilesDir(), "temp_register.db");
            try (InputStream inputStream = response.body().byteStream();
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // 檢查本地資料庫是否有更新
            SQLiteDatabase tempDb = SQLiteDatabase.openDatabase(tempFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = tempDb.query(TABLE_NAME, new String[]{COL_LAST_MODIFIED}, null, null, null, null, "last_modified DESC LIMIT 1");
            long serverLastModified = 0;
            if (cursor.moveToFirst()) {
                serverLastModified = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_MODIFIED));
            }
            cursor.close();
            tempDb.close();

            SQLiteDatabase localDb = getRegisterDatabase();
            Cursor localCursor = localDb.query(TABLE_NAME, new String[]{COL_LAST_MODIFIED}, null, null, null, null, "last_modified DESC LIMIT 1");
            long localLastModified = 0;
            if (localCursor.moveToFirst()) {
                localLastModified = localCursor.getLong(localCursor.getColumnIndexOrThrow(COL_LAST_MODIFIED));
            }
            localCursor.close();

            if (serverLastModified > localLastModified || serverLastModified > lastSyncTime) {
                mergeDatabases(tempFile.getPath());
                Log.d(TAG, "Merged updated database from server");
            } else {
                Log.d(TAG, "No updates needed from server");
            }

            if (!tempFile.delete()) {
                Log.w(TAG, "Failed to delete temp file");
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException during download: " + e.getMessage());
            throw e;
        }
    }

    private void uploadDatabase() throws IOException {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable, skipping upload");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        File dbFile = new File(dbPath);
        Log.d(TAG, "Uploading database from: " + dbFile.getAbsolutePath());

        if (!dbFile.exists()) {
            Log.e(TAG, "Database file not found: " + dbFile.getAbsolutePath());
            throw new IOException("Database file not found");
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("database", dbFile.getName(),
                        RequestBody.create(dbFile, MediaType.parse("application/octet-stream")))
                .build();

        Request request = new Request.Builder()
                .url(SERVER_URL + "/upload")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "Database uploaded successfully, response: " + response.body().string());
            } else {
                Log.e(TAG, "Failed to upload database: " + response.code() + " - " + response.message());
                throw new IOException("Upload failed: " + response.code() + " - " + response.message());
            }
        }
    }

    public interface UploadCallback {
        void onUploadComplete(boolean success, String message);
    }

    public void registerUser(String username, String email, String password, UploadCallback callback) {
        SQLiteDatabase db = getRegisterDatabase();
        ContentValues values = new ContentValues();

        String randomId;
        do {
            randomId = generateRandomId();
            Cursor cursor = db.query(TABLE_NAME, new String[]{"id"},
                    "id = ?", new String[]{randomId}, null, null, null);
            if (cursor.getCount() == 0) {
                cursor.close();
                break;
            }
            cursor.close();
        } while (true);

        values.put("id", randomId);
        values.put(COL_USERNAME, username);
        values.put(COL_EMAIL, email);
        values.put(COL_PASSWORD, password);
        values.put(COL_LAST_MODIFIED, System.currentTimeMillis());
        values.put(COL_IS_SYNCED, 0);
        values.put(COL_SYNC_ACTION, "insert");

        long result = db.insert(TABLE_NAME, null, values);
        if (result == -1) {
            Log.e(TAG, "Failed to register user: " + username);
            callback.onUploadComplete(false, "Failed to register user");
        } else {
            Log.d(TAG, "User registered: " + username + " with ID: " + randomId);
            // 在背景線程中執行上傳
            executorService.execute(() -> {
                try {
                    uploadDatabase();
                    Log.d(TAG, "Database uploaded successfully in background");
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onUploadComplete(true, "Registration and upload successful"));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to upload database after registration: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onUploadComplete(false, "Upload failed: " + e.getMessage()));
                }
            });
        }
    }

    private void mergeDatabases(String centralDbPath) {
        SQLiteDatabase localDb = getRegisterDatabase();
        SQLiteDatabase centralDb = SQLiteDatabase.openDatabase(centralDbPath, null, SQLiteDatabase.OPEN_READONLY);

        Cursor cursor = null;
        try {
            cursor = centralDb.query(TABLE_NAME, null, null, null, null, null, null);
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
                String email = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL));
                String password = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD));
                long lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_MODIFIED));
                int isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SYNCED));
                String syncAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_ACTION));

                Cursor localCursor = localDb.query(TABLE_NAME, null, "id = ?",
                        new String[]{id}, null, null, null);

                if (localCursor.moveToFirst()) {
                    long localLastModified = localCursor.getLong(localCursor.getColumnIndexOrThrow(COL_LAST_MODIFIED));
                    if (lastModified > localLastModified) {
                        ContentValues values = new ContentValues();
                        values.put("id", id);
                        values.put(COL_USERNAME, username);
                        values.put(COL_EMAIL, email);
                        values.put(COL_PASSWORD, password);
                        values.put(COL_LAST_MODIFIED, lastModified);
                        values.put(COL_IS_SYNCED, isSynced);
                        values.put(COL_SYNC_ACTION, syncAction);
                        localDb.update(TABLE_NAME, values, "id = ?", new String[]{id});
                        Log.d(TAG, "Updated record with ID: " + id);
                    }
                } else {
                    ContentValues values = new ContentValues();
                    values.put("id", id);
                    values.put(COL_USERNAME, username);
                    values.put(COL_EMAIL, email);
                    values.put(COL_PASSWORD, password);
                    values.put(COL_LAST_MODIFIED, lastModified);
                    values.put(COL_IS_SYNCED, isSynced);
                    values.put(COL_SYNC_ACTION, syncAction);
                    localDb.insert(TABLE_NAME, null, values);
                    Log.d(TAG, "Inserted new record with ID: " + id);
                }
                localCursor.close();
            }

            ContentValues values = new ContentValues();
            values.put(COL_IS_SYNCED, 1);
            localDb.update(TABLE_NAME, values, null, null);
            Log.d(TAG, "Marked all records as synced");
        } catch (Exception e) {
            Log.e(TAG, "Error merging databases: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            centralDb.close();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        closeDatabase();
    }

    private String SERVER_URL;

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    // New method to get image data from picture_data table
    public String getImageFileName(int imageId) {
        SQLiteDatabase db = getPictureDatabase();
        Cursor cursor = null;
        String fileName = null;
        try {
            cursor = db.query("picture_data",
                    new String[]{"image", "file_extension"},
                    "image = ?",
                    new String[]{String.valueOf(imageId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("image"));
                String extension = cursor.getString(cursor.getColumnIndexOrThrow("file_extension"));
                fileName = "images/" + id + extension; // Assuming numeric filename (e.g., 123.png)
                Log.d(TAG, "Found filename: " + fileName + " for imageId: " + imageId);
            } else {
                Log.w(TAG, "No record found for imageId: " + imageId);
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error querying image file name: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fileName;
    }

    // New method to get additional metadata from picture_data table
    public PictureData getPictureData(int imageId) {
        SQLiteDatabase db = getPictureDatabase();
        Cursor cursor = null;
        PictureData data = null;
        try {
            cursor = db.query("picture_data",
                    new String[]{"name", "description", "location_data", "latitude", "longitude"},
                    "image = ?",
                    new String[]{String.valueOf(imageId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                String locationData = cursor.getString(cursor.getColumnIndexOrThrow("location_data"));
                String latitude = cursor.getString(cursor.getColumnIndexOrThrow("latitude"));
                String longitude = cursor.getString(cursor.getColumnIndexOrThrow("longitude"));
                data = new PictureData(name, description, locationData, latitude, longitude);
                Log.d(TAG, "Found location data: " + locationData + " for imageId: " + imageId);
            } else {
                Log.w(TAG, "No metadata found for imageId: " + imageId);
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Error querying picture data: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return data;
    }

    // New class to hold picture data
    public static class PictureData {
        public final String name;
        public final String description;
        public final String locationData;
        public final String latitude;
        public final String longitude;

        public PictureData(String name, String description, String locationData, String latitude, String longitude) {
            this.name = name;
            this.description = description;
            this.locationData = locationData;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}