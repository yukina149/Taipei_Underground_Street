package com.example.cameraproject_2;

import android.Manifest;
import android.content.Context;
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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private List<LocationData> locationDataList = new ArrayList<>();
    private ActivityResultLauncher<Intent> startOrbActivityLauncher;
    private DatabaseHelper dbHelper;
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

        // 初始化 Spinner
        destinationSpinner = findViewById(R.id.destinationSpinner);

        // 設置 Spinner 的數據和行為
        setupDestinationSpinner();

        // 初始化 currentLocationTextView
        currentLocationTextView = findViewById(R.id.currentLocationTextView);


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


        //調用orb location
        // 初始化 ActivityResultLauncher
        startOrbActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent intent = result.getData();
                            if (intent != null) {
                                String location = intent.getStringExtra("location");
                                if (location != null && !location.isEmpty()) {
                                    // 更新目前顯示
                                    currentLocationTextView.setText("Location: " + location);
                                    Log.e("location", location);
                                } else {
                                    Toast.makeText(MainActivity.this, "位置信息为空", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Image capture/selection cancelled or failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    //將拍照的照片顯示在ImageView

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 檢查結果是否為成功
        if (resultCode == RESULT_OK) {
            // 處理拍照後的結果
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
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
            // 處理選擇圖片後的結果
            else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                Uri selectedImageUri = data.getData();
                processSelectedImage(selectedImageUri);
            }
            // 處理 ORB 活動後的結果
            else if (requestCode == REQUEST_ORB_ACTIVITY && data != null) {
                String location = data.getStringExtra("location");
                if (location != null && !location.isEmpty()) {
                    // 更新目前位置
                    currentLocationTextView.setText("Location: " + location);
                    Log.e("location", location);
                } else {
                    Toast.makeText(this, "位置信息为空", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            // 如果操作取消或失敗，顯示提示訊息
            Toast.makeText(this, "Image capture/selection cancelled or failed", Toast.LENGTH_SHORT).show();
        }
    }


    //下拉式選單
    public class CustomSpinnerAdapter extends ArrayAdapter<String> {

        public CustomSpinnerAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (position == 0) {
                ((TextView) view).setText("請選擇目的地");
            }
            return view;
        }

    }
    private void setupDestinationSpinner() {
        List<String> locationNamesForDisplay = new ArrayList<>();
        List<String> locationNamesForDropdown = new ArrayList<>();

        for (LocationData location : locationDataList) {
            locationNamesForDropdown.add(location.getLocationName());
        }

        if (locationNamesForDropdown.isEmpty()) {
            locationNamesForDropdown.add("A棟");
            locationNamesForDropdown.add("B棟");
            locationNamesForDropdown.add("C棟");
            locationNamesForDropdown.add("D棟");
            locationNamesForDropdown.add("E棟");
            locationNamesForDropdown.add("F棟");
            locationNamesForDropdown.add("G棟");
            locationNamesForDropdown.add("H棟");
            locationNamesForDropdown.add("I棟");
            locationNamesForDropdown.add("J棟");
            locationNamesForDropdown.add("K棟");
            locationNamesForDropdown.add("L棟");
            locationNamesForDropdown.add("M棟");
            locationNamesForDropdown.add("N棟");
            locationNamesForDropdown.add("NB棟");
            locationNamesForDropdown.add("排灣族");
            locationNamesForDropdown.add("資源教室");
        }

        locationNamesForDisplay.add("請選擇目的地");
        locationNamesForDisplay.addAll(locationNamesForDropdown);

        CustomSpinnerAdapter adapter = new CustomSpinnerAdapter(
                this, android.R.layout.simple_spinner_item, locationNamesForDisplay);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        destinationSpinner.setAdapter(adapter);
        destinationSpinner.setSelection(0); // 默認第一個元素

        destinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLocation = locationNamesForDisplay.get(position);
                if (!selectedLocation.equals("請選擇目的地")) {
                    // 顯示選擇的位置
                    Toast.makeText(MainActivity.this, "您選擇了： " + selectedLocation, Toast.LENGTH_SHORT).show();
                    selectedDestination = selectedLocation; // 更新 selectedDestination
                } else {
                    selectedDestination = ""; // 如果選則默認選項，則清空位置
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDestination = "";
            }
        });
    }

    //點擊事件
    private void setupClickListeners() {
        findViewById(R.id.cardCamera).setOnClickListener(v -> captureImage());
        findViewById(R.id.cardPicture).setOnClickListener(v -> openGallery());

        //  找到 database 的 ImageView 並設定點擊事件
        ImageView imageViewDatabase = findViewById(R.id.image_database);
        if (imageViewDatabase != null) {
            imageViewDatabase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // 啟動 DatabaseActivity
                    Intent intent = new Intent(MainActivity.this, database.class);
                    startActivity(intent);
                }
            });
        } else {
            Log.e("MainActivity", "image_view_database not found in layout");
        }


        //  找到 orb 的 ImageView 並設定點擊事件 (新增)
        ImageView imageViewOrb = findViewById(R.id.image_orb);
        if (imageViewOrb != null) {
            imageViewOrb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // 啟動 ORBActivity
                    Intent intent = new Intent(MainActivity.this, ORBActivity.class);
                    startOrbActivityLauncher.launch(intent);
                }
            });
        } else {
            Log.e("MainActivity", "image_view_orb not found in layout");
        }
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
