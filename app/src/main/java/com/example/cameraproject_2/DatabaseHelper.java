package com.example.cameraproject_2;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {


    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "picture.db";
    private static final int DATABASE_VERSION = 1;
    private static final String DB_PATH = "/data/data/com.example.cameraproject_2/databases/";

    private final Context context;
    private SQLiteDatabase database;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    public void createDataBase() throws IOException {
        boolean dbExists = checkDataBase();

        if (!dbExists) {
            this.getReadableDatabase();

            try {
                copyDataBase();
                copyImages(); // 複製圖片檔案
                Log.d(TAG, "Database created successfully");
            } catch (IOException e) {
                throw new Error("Error copying database: " + e.getMessage());
            }
        }
    }

    private boolean checkDataBase() {
        SQLiteDatabase checkDB = null;

        try {
            String myPath = DB_PATH + DATABASE_NAME;
            File dbFile = new File(myPath);
            return dbFile.exists();
        } catch (SQLiteException e) {
            Log.e(TAG, "Database doesn't exist: " + e.getMessage());
        }

        if (checkDB != null) {
            checkDB.close();
        }

        return false;
    }

    private void copyDataBase() throws IOException {
        InputStream myInput = context.getAssets().open(DATABASE_NAME);
        String outFileName = DB_PATH + DATABASE_NAME;

        File directory = new File(DB_PATH);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        OutputStream myOutput = new FileOutputStream(outFileName);
        byte[] buffer = new byte[1024];
        int length;

        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }

        myOutput.flush();
        myOutput.close();
        myInput.close();
    }

    // 複製圖片檔案
    public void copyImages() {
        File imagesDir = new File(context.getFilesDir(), "images");
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
        }

        try {
            String[] imageFiles = context.getAssets().list("images");
            if (imageFiles != null) {
                for (String imageFile : imageFiles) {
                    File targetFile = new File(imagesDir, imageFile);
                    if (!targetFile.exists()) {
                        InputStream in = null;
                        OutputStream out = null;
                        try {
                            in = context.getAssets().open("images/" + imageFile);
                            out = new FileOutputStream(targetFile);
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            out.flush();
                        } catch (IOException e) {
                            Log.e("DatabaseHelper", "Error copying image: " + imageFile, e);
                        } finally {
                            if (in != null) {
                                try {
                                    in.close();
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }
                            if (out != null) {
                                try {
                                    out.close();
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e("DatabaseHelper", "Error listing assets", e);
        }
    }

    public SQLiteDatabase openDataBase() throws SQLiteException {
        String myPath = DB_PATH + DATABASE_NAME;
        database = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
        return database;
    }

    public void closeDatabase() {
        if (database != null) {
            database.close();
        }
        super.close();
    }


    public List<LocationData> getAllLocations() {
        List<LocationData> locationList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        try {
            // 查詢所有位置數據
            Cursor cursor = db.rawQuery("SELECT location_name, image_data, image_file_name FROM picture_data", null);

            if (cursor.moveToFirst()) {
                do {
                    String locationName = cursor.getString(0);
                    String imageData = cursor.getString(1);
                    String imageFileName = cursor.getString(2);

                    // 將位置數據添加到列表中
                    locationList.add(new LocationData(locationName, imageData, imageFileName));

                } while (cursor.moveToNext());
            }
            cursor.close();

        } catch (Exception e) {
            Log.e(TAG, "Error getting locations: " + e.getMessage());
        }

        return locationList;
    }

    public LocationData getLocationById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        LocationData location = null;

        try {
            Cursor cursor = db.rawQuery("SELECT location_name, image_data, image_file_name FROM picture_data WHERE id = ?",
                    new String[] { String.valueOf(id) });

            if (cursor.moveToFirst()) {
                String locationName = cursor.getString(0);
                String imageData = cursor.getString(1);
                String imageFileName = cursor.getString(2);
                location = new LocationData(locationName, imageData, imageFileName);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
        }

        return location;
    }
    //添加 getNavigationPath 方法
    public List<NavigationPoint> getNavigationPath(String startLocation, String endLocation) {
        List<NavigationPoint> path = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        try {
            // 假設有一個navigation_paths表存儲路徑點
            Cursor cursor = db.rawQuery(
                    "SELECT point_order, x_coordinate, y_coordinate, z_coordinate " +
                            "FROM navigation_paths " +
                            "WHERE start_location = ? AND end_location = ? " +
                            "ORDER BY point_order ASC",
                    new String[] { startLocation, endLocation });

            if (cursor.moveToFirst()) {
                do {
                    int order = cursor.getInt(0);
                    float x = cursor.getFloat(1);
                    float y = cursor.getFloat(2);
                    float z = cursor.getFloat(3);

                    path.add(new NavigationPoint(order, x, y, z));

                } while (cursor.moveToNext());
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "Error getting navigation path: " + e.getMessage());
        }

        return path;
    }


    @Override
    public synchronized void close() {
        if (database != null) {
            database.close();
        }
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 當第一次創建資料庫時會執行
        // 因為我們使用預先填充的資料庫，這裡不需要實現
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 當數據庫版本升級時會執行
    }
}

