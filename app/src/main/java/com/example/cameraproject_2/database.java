package com.example.cameraproject_2;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.bumptech.glide.Glide; // 引入 Glide

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class database extends AppCompatActivity {

    private static final String DATABASE_NAME = "picture.db";

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_database);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new DatabaseHelper(this);

        // 每次都複製並更新資料庫
        copyDatabase();

        //  為了確保資料庫可以打開，嘗試打開它
        try {
            dbHelper.createDataBase();
            SQLiteDatabase db = dbHelper.openDataBase(); // 修正方法名稱
            // 如果可以到達這裡，表示資料庫已成功打開
            Toast.makeText(this, "Database opened successfully", Toast.LENGTH_SHORT).show();
            displayImagesFromDatabase(db); // 顯示圖片
            dbHelper.closeDatabase(); // 修正方法名稱
        } catch (Exception e) {
            Log.e("Database", "Error opening database: " + e.getMessage());
            Toast.makeText(this, "Error opening database", Toast.LENGTH_SHORT).show();
        }

        // 複製圖片到手機裡面
        dbHelper.copyImages();

        //建立圖片檔案
        File imageDir = new File(getApplicationContext().getFilesDir(), "images");
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }

    }

    // 檢查資料庫是不是存在
    private boolean isDatabaseExists(Context context, String dbName) {
        File dbFile = context.getDatabasePath(dbName);
        return dbFile.exists();
    }


    private void copyDatabase() {
        try {
            InputStream input = getAssets().open(DATABASE_NAME);
            File dbFile = getDatabasePath(DATABASE_NAME);

            // 如果資料庫檔案存在，則刪除它
            if (dbFile.exists()) {
                dbFile.delete();
            }

            // 確保父目錄存在
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            OutputStream output = new FileOutputStream(dbFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            output.flush();
            output.close();
            input.close();

            Toast.makeText(this, "Database copied successfully", Toast.LENGTH_SHORT).show();
            checkCopiedDatabase(); // 確認資料庫檔案是否存在
        } catch (IOException e) {
            Log.e("Database", "Error copying database: " + e.getMessage());
            Toast.makeText(this, "Error copying database", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkCopiedDatabase() {
        File dbFile = getDatabasePath(DATABASE_NAME);
        if (dbFile.exists()) {
            Log.d("Database", "Database copied successfully to: " + dbFile.getAbsolutePath());
        } else {
            Log.e("Database", "Database file not found at: " + dbFile.getAbsolutePath());
        }
    }

    private void displayImagesFromDatabase(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            ConstraintLayout imagesContainer = findViewById(R.id.imagesContainer);
            imagesContainer.removeAllViews(); // Clear existing views
            int lastViewId = View.NO_ID;

            String[] columns = {"name", "image", "file_extension", "description"};
            cursor = db.query("picture_data", columns, null, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String imageName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                    String imageFileName = cursor.getString(cursor.getColumnIndexOrThrow("image"));
                    String fileExtension = cursor.getString(cursor.getColumnIndexOrThrow("file_extension"));

                    String fullImageFileName = imageFileName  + fileExtension;

                    String imagePath = getImagePathFromName(fullImageFileName);
                    Log.d("ImageDisplay", "Image path: " + imagePath);

                    //圖片
                    ImageView imageView = new ImageView(this);
                    imageView.setId(View.generateViewId());
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ConstraintLayout.LayoutParams imageParams = new ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            ConstraintLayout.LayoutParams.WRAP_CONTENT
                    );
                    imageView.setLayoutParams(imageParams);

                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        Glide.with(this)
                                .load(imageFile)
                                .into(imageView);
                    } else {
                        Log.e("ImageDisplay", "Image file not found: " + imagePath);
                        imageView.setImageResource(R.drawable.ic_launcher_background);
                    }

                    TextView textView = new TextView(this);
                    textView.setId(View.generateViewId());
                    textView.setText(description);
                    textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    ConstraintLayout.LayoutParams textParams = new ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            ConstraintLayout.LayoutParams.WRAP_CONTENT
                    );
                    textView.setLayoutParams(textParams);

                    imagesContainer.addView(imageView);
                    imagesContainer.addView(textView);

                    ConstraintSet set = new ConstraintSet();
                    set.clone(imagesContainer);

                    set.connect(imageView.getId(), ConstraintSet.TOP,
                            (lastViewId == View.NO_ID) ? ConstraintSet.PARENT_ID : lastViewId,
                            (lastViewId == View.NO_ID) ? ConstraintSet.TOP : ConstraintSet.BOTTOM, 0);
                    set.connect(imageView.getId(), ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 0);
                    set.connect(imageView.getId(), ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 0);

                    set.connect(textView.getId(), ConstraintSet.TOP, imageView.getId(), ConstraintSet.BOTTOM, 0);
                    set.connect(textView.getId(), ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 0);
                    set.connect(textView.getId(), ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 0);

                    set.applyTo(imagesContainer);
                    lastViewId = textView.getId();

                } while (cursor.moveToNext());
            } else {
                Log.e("ImageDisplay", "Cursor is null or empty");
                Toast.makeText(this, "No images found in database", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("ImageDisplay", "Database Query Error: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getImagePathFromName(String imageName) {
        File imageDir = new File(getApplicationContext().getFilesDir(), "images");
        Log.d("ImageDisplay", "Image directory: " + imageDir.getAbsolutePath()); // 新增這一行
        return new File(imageDir, imageName).getAbsolutePath();
    }

}