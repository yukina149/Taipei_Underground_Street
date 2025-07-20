package com.example.cameraproject_2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.unity3d.player.UnityPlayerActivity;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UploadImage extends AppCompatActivity {

    static {
        System.loadLibrary("opencv_java4");
    }

    private static final String KEY_PHOTO_URI = "photoUri";
    private static final String KEY_CURRENT_BITMAP_PATH = "currentBitmapPath";

    private BottomNavigationView bottomNavigationView;
    private ImageView bigmap;
    private ImageView smallmap;
    private TextView currentLocationTextView;
    private Button buttonCorrectLocation;
    private Button buttonIncorrectLocation;
    private Button buttonUpload;
    private Button sendToChatButton; // 新增對應的按鈕變量
    private Uri photoUri;
    private Bitmap currentBitmap;
    private String currentBitmapPath;
    private ArrayList<MatchResult> topMatches = new ArrayList<>();
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_upload_image);

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "無法載入 OpenCV");
            Toast.makeText(this, "OpenCV 初始化失敗，請檢查應用配置", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Log.d("OpenCV", "OpenCV 載入成功");

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // Initialize UI components
        bigmap = findViewById(R.id.bigmap);
        smallmap = findViewById(R.id.smallmap);
        currentLocationTextView = findViewById(R.id.currentLocationTextView);
        buttonCorrectLocation = findViewById(R.id.buttonCorrectLocation);
        buttonIncorrectLocation = findViewById(R.id.buttonIncorrectLocation);
        buttonUpload = findViewById(R.id.buttonupload);
        sendToChatButton = findViewById(R.id.sendtochat); // 初始化 "分享位置" 按鈕
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        if (bigmap == null || smallmap == null || currentLocationTextView == null ||
                buttonCorrectLocation == null || buttonIncorrectLocation == null ||
                buttonUpload == null || sendToChatButton == null || bottomNavigationView == null) {
            Log.e("UploadImage", "UI components not found in layout, check activity_upload_image.xml");
            Toast.makeText(this, "應用程式初始化失敗，請檢查佈局文件", Toast.LENGTH_LONG).show();
            finish();
            return;
        }



        // Setup BottomNavigationView
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.homefill) {
                Toast.makeText(this, R.string.home_page, Toast.LENGTH_SHORT).show();
            } else if (id == R.id.chat) {
                boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
                Log.d("MainActivity", "Navigating to Chatroom, isLoggedIn: " + isLoggedIn);
                if (!isLoggedIn) {
                    Intent intent = new Intent(UploadImage.this, PersonalAccount.class);
                    startActivity(intent);
                } else {
                    Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
                    if (groupNames == null || groupNames.isEmpty()) {
                        Intent intent = new Intent(UploadImage.this, chatroom_main.class);
                        startActivity(intent);
                    } else {
                        String defaultGroup = groupNames.iterator().next(); // 取第一個群組
                        String membersString = sharedPreferences.getString(defaultGroup + "_members", "");
                        List<String> members = new ArrayList<>();
                        if (!membersString.isEmpty()) {
                            String[] membersArray = membersString.split(",");
                            for (String member : membersArray) {
                                members.add(member.trim());
                            }
                        }
                        Intent intent = new Intent(UploadImage.this, chatroom_main.class);
                        intent.putExtra("groupName", defaultGroup);
                        intent.putStringArrayListExtra("members", new ArrayList<>(members));
                        startActivity(intent);
                    }
                }
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            } else if (id == R.id.nav_member) {
                // 保持原有邏輯不變
                boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
                Intent intent = new Intent(UploadImage.this, isLoggedIn ? UserProfileActivity.class : PersonalAccount.class);
                intent.putExtra("isLoggedIn", isLoggedIn);
                intent.putExtra("userId", sharedPreferences.getString("userId", "訪客"));
                intent.putExtra("loggedInUser", sharedPreferences.getString("loggedInUser", "訪客"));
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            } else if (id == R.id.nav_info) {
                Toast.makeText(this, R.string.taipei_info, Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(UploadImage.this, SettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            }
            return true;
        });
        bottomNavigationView.setSelectedItemId(R.id.homefill);

        bigmap.setScaleType(ImageView.ScaleType.FIT_CENTER);
        smallmap.setScaleType(ImageView.ScaleType.FIT_CENTER);
        buttonCorrectLocation.setEnabled(false);
        buttonIncorrectLocation.setEnabled(false);
        buttonUpload.setEnabled(false);

        // Initialize ActivityResultLauncher for ORBActivity and WhereLocation
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent resultIntent = result.getData();
                        String locationFromORB = resultIntent.getStringExtra("location");
                        Log.d("UploadImage", "Received location from ORBActivity: " + locationFromORB);

                        if (locationFromORB != null && !locationFromORB.isEmpty()) {
                            currentLocationTextView.setText("Location: " + locationFromORB);
                            Log.d("UploadImage", "Updated currentLocationTextView to: " + locationFromORB);

                            ArrayList<MatchResult> matches = resultIntent.getParcelableArrayListExtra("topMatches");
                            if (matches != null && !matches.isEmpty()) {
                                topMatches.clear();
                                topMatches.addAll(matches);
                                Log.d("UploadImage", "Received topMatches size: " + topMatches.size());

                                if (!topMatches.isEmpty()) {
                                    MatchResult bestMatch = topMatches.get(0);
                                    String imageUriString = bestMatch.getUri();
                                    Log.d("UploadImage", "Best match URI: " + imageUriString);
                                    String fileName = imageUriString.replace("file://assets/", "");
                                    Log.d("UploadImage", "Attempting to load file: " + fileName);
                                    try {
                                        InputStream inputStream = getAssets().open(fileName);
                                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                        inputStream.close();
                                        smallmap.setImageBitmap(bitmap);
                                        Log.d("UploadImage", "Set smallmap image: " + fileName);
                                    } catch (IOException e) {
                                        Log.e("UploadImage", "Failed to load smallmap image: " + fileName + ", Error: " + e.getMessage());
                                        Toast.makeText(this, "無法加載匹配的地圖圖片", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }

                            if (!locationFromORB.equals("未知") && !locationFromORB.isEmpty()) {
                                buttonCorrectLocation.setEnabled(true);
                                buttonIncorrectLocation.setEnabled(true);
                            } else {
                                buttonCorrectLocation.setEnabled(false);
                                buttonIncorrectLocation.setEnabled(false);
                            }
                        } else {
                            Log.w("UploadImage", "locationFromORB is null or empty");
                            String selectedLocation = resultIntent.getStringExtra("selectedLocation");
                            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                                currentLocationTextView.setText("Location: " + selectedLocation);
                                Toast.makeText(this, "位置已更新為：" + selectedLocation, Toast.LENGTH_SHORT).show();
                                buttonCorrectLocation.setEnabled(true);
                                buttonIncorrectLocation.setEnabled(true);
                            } else {
                                Toast.makeText(this, "未選擇位置", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Log.w("UploadImage", "Result code is not OK or data is null");
                        Toast.makeText(this, "操作取消或失敗", Toast.LENGTH_SHORT).show();
                    }
                });

        // Button click listeners
        buttonCorrectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(UploadImage.this, UnityPlayerActivity.class);
            startActivity(intent);
        });

        buttonIncorrectLocation.setOnClickListener(v -> {
            if (topMatches.isEmpty()) {
                Toast.makeText(this, "請先進行圖片比對以獲取匹配結果", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(UploadImage.this, WhereLocation.class);
            intent.putParcelableArrayListExtra("topMatches", topMatches);
            activityResultLauncher.launch(intent);
        });

        buttonUpload.setOnClickListener(v -> {
            Intent intent = new Intent(UploadImage.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // "分享位置" 按鈕點擊事件
        sendToChatButton.setOnClickListener(v -> {
            // 獲取所有聊天室名稱
            Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
            if (groupNames == null || groupNames.isEmpty()) {
                Toast.makeText(this, "您尚未加入任何聊天室", Toast.LENGTH_SHORT).show();
                return;
            }

            // 創建多選對話框
            final boolean[] checkedItems = new boolean[groupNames.size()];
            final ArrayList<String> groupList = new ArrayList<>(groupNames);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("選擇要分享的聊天室")
                    .setMultiChoiceItems(groupList.toArray(new String[0]), checkedItems,
                            (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                    .setPositiveButton("確定", (dialog, which) -> {
                        String currentLocation = currentLocationTextView.getText().toString().replace("Location: ", "");
                        for (int i = 0; i < checkedItems.length; i++) {
                            if (checkedItems[i]) {
                                String selectedGroup = groupList.get(i);
                                sendLocationToChatroom(selectedGroup, currentLocation);
                            }
                        }
                        Toast.makeText(UploadImage.this, "位置已分享到選定的聊天室", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        // Load and process image from Intent
        Intent intent = getIntent();
        String photoUriString = intent.getStringExtra("photoUri");
        if (photoUriString != null) {
            photoUri = Uri.parse(photoUriString);
            processImage(photoUri);
        } else {
            Toast.makeText(this, "未接收到圖片數據", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Restore state if available
        if (savedInstanceState != null) {
            String savedPhotoUriString = savedInstanceState.getString(KEY_PHOTO_URI);
            if (savedPhotoUriString != null) {
                photoUri = Uri.parse(savedPhotoUriString);
                processImage(photoUri);
            }
            currentBitmapPath = savedInstanceState.getString(KEY_CURRENT_BITMAP_PATH);
            if (currentBitmapPath != null) {
                currentBitmap = BitmapFactory.decodeFile(currentBitmapPath);
                if (currentBitmap != null) {
                    bigmap.setImageBitmap(currentBitmap);
                    bigmap.setVisibility(View.VISIBLE);
                    buttonUpload.setEnabled(true);
                }
            }
        }
    }

    private void sendLocationToChatroom(String groupName, String location) {
        Intent intent = new Intent(this, Chatroom.class);
        intent.putExtra("groupName", groupName);
        intent.putExtra("locationMessage", "位置分享: " + location);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        Log.d("UploadImage", "Sending location '" + location + "' to chatroom: " + groupName);
    }

    private void processImage(Uri photoUri) {
        if (photoUri == null) {
            Log.e("UploadImage", "photoUri is null");
            Toast.makeText(this, "無法處理圖片：URI 為空", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("UploadImage", "處理圖片，URI: " + photoUri.toString());

// 創建並顯示進度提示框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在比對中，請稍後...");
        progressDialog.setCancelable(false); // 禁止用戶取消
        progressDialog.show();

// 使用 AsyncTask 處理圖片
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(photoUri);
                    if (inputStream == null) {
                        Log.e("UploadImage", "無法為 photoUri 打開 InputStream");
                        runOnUiThread(() -> Toast.makeText(UploadImage.this, "無法讀取圖片", Toast.LENGTH_SHORT).show());
                        return null;
                    }

// 解碼 Bitmap
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    if (bitmap == null) {
                        Log.e("UploadImage", "從 InputStream 解碼 Bitmap 失敗");
                        runOnUiThread(() -> Toast.makeText(UploadImage.this, "無法解碼圖片", Toast.LENGTH_SHORT).show());
                        return null;
                    }

// 檢查並校正 EXIF 方向
                    ExifInterface exif = null;
                    try {
                        if (photoUri.getPath() != null) {
                            exif = new ExifInterface(getContentResolver().openInputStream(photoUri));
                        }
                    } catch (IOException e) {
                        Log.e("UploadImage", "讀取 EXIF 資訊失敗: " + e.getMessage());
                    }

                    if (exif != null) {
                        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        Log.d("UploadImage", "EXIF 方向: " + orientation);
                        bitmap = rotateBitmapIfNeeded(bitmap, orientation);
                    }

                    return bitmap;
                } catch (IOException e) {
                    Log.e("UploadImage", "處理圖片錯誤: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(UploadImage.this, "處理圖片時出錯", Toast.LENGTH_SHORT).show());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    currentBitmap = bitmap;
                    currentBitmapPath = saveBitmapToTempFile(currentBitmap);
                    bigmap.setImageBitmap(currentBitmap);
                    bigmap.setVisibility(View.VISIBLE);
                    buttonUpload.setEnabled(true);

// Convert Bitmap to Mat for ORB processing in a separate thread
                    new Thread(() -> {
                        try {
                            Mat mat = new Mat();
                            Utils.bitmapToMat(currentBitmap, mat);

// Launch ORBActivity for image comparison
                            Intent intent = new Intent(UploadImage.this, ORBActivity.class);
                            intent.putExtra("imageUri", photoUri.toString());
                            activityResultLauncher.launch(intent);
                        } finally {
// 確保進度提示框在 ORB 比對啟動後關閉
                            runOnUiThread(() -> {
                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }
                            });
                        }
                    }).start();
                } else {
// 如果圖片處理失敗，關閉進度提示框
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                }
            }
        }.execute();
    }


    private Bitmap rotateBitmapIfNeeded(Bitmap bitmap, int orientation) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w("UploadImage", "Bitmap is null or recycled");
            return null;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bitmap;
        }

        try {
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotatedBitmap != bitmap) {
                bitmap.recycle(); // 回收原始 Bitmap
            }
            return rotatedBitmap;
        } catch (OutOfMemoryError e) {
            Log.e("UploadImage", "OutOfMemoryError during bitmap rotation: " + e.getMessage());
            return bitmap; // 返回原始 Bitmap
        }
    }

    private String saveBitmapToTempFile(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w("UploadImage", "Bitmap 為空或已回收，無法保存到臨時文件");
            return null;
        }

        try {
            File tempDir = new File(getCacheDir(), "temp_bitmaps");
            if (!tempDir.exists()) tempDir.mkdirs();
            File tempFile = File.createTempFile("bitmap_", ".png", tempDir);
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out); // 降低壓縮質量
                out.flush();
            }
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e("UploadImage", "保存 Bitmap 到臨時文件失敗: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoUri != null) {
            outState.putString(KEY_PHOTO_URI, photoUri.toString());
        }
        if (currentBitmapPath != null) {
            outState.putString(KEY_CURRENT_BITMAP_PATH, currentBitmapPath);
        }
    }
}