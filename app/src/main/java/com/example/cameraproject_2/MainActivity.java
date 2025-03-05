package com.example.cameraproject_2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    static {
        System.loadLibrary("opencv_java4");
    }


    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;// 相機權限請求碼
    private static final int REQUEST_IMAGE_CAPTURE = 2; // 拍照請求碼
    private static final int REQUEST_IMAGE_PICK = 3; // 圖片選擇請求碼

    private ImageView imageView;
    private ImageView bigmap; // 新增
    private Uri photoUri;
    private Uri photoUri_1;

    CardView cardPicture;
    CardView cardCamera;
    CardView cardMap;
    CardView cardGo;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private File photoFile;

    private List<Mat> images = new ArrayList<>();
    //private List<LocationData> locationDataList = new ArrayList<>();
    private ActivityResultLauncher<Intent> startOrbActivityLauncher;
    //private DatabaseHelper dbHelper;
    private SQLiteDatabase database;

    private String currentLocation = "Unknown";
    private String selectedDestination = "";
    private Spinner destinationSpinner;
    private String bestMatchLocation = "Unknown";
    private static final int REQUEST_ORB_ACTIVITY = 1001;
    private TextView currentLocationTextView;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 顯示圖片
        bigmap = findViewById(R.id.bigmap);


        // 初始化 OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
            return;
        }
        Log.d("OpenCV", "OpenCV loaded successfully");

        setupClickListeners();


        //MENU的HEADER
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);  // 獲取第一個 header
        if (headerView == null) {
            headerView = navigationView.inflateHeaderView(R.layout.activity_menu_header);
        }


        // 初始化 UI 元件
        cardPicture = findViewById(R.id.cardPicture);
        cardCamera = findViewById(R.id.cardCamera);
        cardMap = findViewById(R.id.cardMap);
        cardGo = findViewById(R.id.cardGo);
        imageView = findViewById(R.id.menuIcon);

        // 初始化 Navigation Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ImageView menuIcon = findViewById(R.id.menuIcon);

        // 設置點擊事件，開啟側欄
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // 設定 Navigation Drawer 的監聽事件
        navigationView.bringToFront();
        navigationView.setNavigationItemSelectedListener(this);

        // 設置 ActionBarDrawerToggle 以控制 drawer 開關
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 設置 Map 按鈕點擊事件
        cardMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.cameraproject_2.MapActivity.class);
            startActivity(intent);
        });
    }

    //點擊事件
    private void setupClickListeners() {
        findViewById(R.id.cardCamera).setOnClickListener(v -> captureImage());
        findViewById(R.id.cardPicture).setOnClickListener(v -> openGallery());
    }

    //啟動相機應用程式來拍照
    private void captureImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }

        try {
            // 建立檔案來儲存圖片
            photoFile = createImageFile();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            return; // 如果檔案建立失敗，退出方法
        }

        Uri photoUri = FileProvider.getUriForFile(
                this,
                "com.example.cameraproject_2.fileprovider",
                photoFile
        );

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }



    //將拍照的照片顯示在ImageView

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                // 從檔案中讀取完整的圖片
                Bitmap imageBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

                // 將圖片顯示在 ImageView 中
                bigmap.setImageBitmap(imageBitmap);

                // 將圖片儲存到相冊（如果需要）
                saveImageToGallery(imageBitmap);
            } catch (Exception e) {
                Log.e("Camera", "Error loading image: " + e.getMessage());
            }
        }
        else if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            processSelectedImage(selectedImageUri);
        }
    }


    //拍照後儲存照片在使用者的媒體庫----不會顯示在使用者的媒體庫中

    private File createImageFile() throws IOException {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            Log.e("Camera", "Error creating image file: " + e.getMessage());
            return null;
        }
    }

    //相機拍攝的照片儲存到媒體庫----會顯示在使用者的媒體庫
    private void saveImageToGallery(Bitmap imageBitmap) {
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";
        File imageFile = new File(storageDir, fileName);

        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(imageFile));
            sendBroadcast(mediaScanIntent);

            Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Storage", "Error saving image: " + e.getMessage());
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    //打開媒體庫
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK); // 使用新的 API 替代
    }




    //處理使用者從媒體庫中選擇的圖片
    private void processSelectedImage(Uri selectedImageUri) {
        try {
            // 從 URI 獲取 Bitmap
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);

            // 將 Bitmap 顯示在 ImageView 中
            bigmap.setImageBitmap(bitmap);

            // 將 Bitmap 轉換為 OpenCV Mat
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);

            // 將 Mat 加入到 images 列表中以便後續處理
            images.add(mat);

            // 如果需要，您可以在這裡調用保存圖像到相簿的方法
            //saveImageToGallery(bitmap);
        } catch (IOException e) {
            Log.e("Gallery", "Error processing selected image: " + e.getMessage());
            Toast.makeText(this, "Error processing selected image", Toast.LENGTH_SHORT).show();
        }
    }


    //處理相機拍攝的圖片
    private void processCapturedImage(Uri photoUri) {
        try {
            // 從 URI 獲取 Bitmap
            InputStream input = getContentResolver().openInputStream(photoUri);
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            // 將 Bitmap 顯示在 ImageView 中
            bigmap.setImageBitmap(bitmap);

            // 將 Bitmap 轉換為 OpenCV Mat
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);

            // 將 Mat 加入到 images 列表中以便後續處理
            images.add(mat);

            // 如果需要，您可以在這裡調用保存圖像到相簿的方法
            saveImageToGallery(bitmap);

            input.close(); // 關閉輸入流
        } catch (IOException e) {
            Log.e("Camera", "Error processing captured image: " + e.getMessage());
            Toast.makeText(this, "Error processing captured image", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // 側欄選單的點擊事件
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            Toast.makeText(this, "Home Selected", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_store) {
            Toast.makeText(this, "Gallery Selected", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_restaurant) {
            Toast.makeText(this, "Settings Selected", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_game) {
            Toast.makeText(this, "Logout Selected", Toast.LENGTH_SHORT).show();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    //權限的請求
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureImage();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
