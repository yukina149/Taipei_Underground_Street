package com.example.cameraproject_2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
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
import android.widget.Button;
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
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.unity3d.player.UnityPlayerActivity;

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

    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
    private static final int REQUEST_IMAGE_CAPTURE = 2;
    private static final int REQUEST_IMAGE_PICK = 3;

    private static final String KEY_PHOTO_URI = "photoUri";
    private static final String KEY_PHOTO_FILE_PATH = "photoFilePath";
    private static final String KEY_CURRENT_BITMAP_PATH = "currentBitmapPath";

    private ImageView bigmap;
    private Uri photoUri;
    private File photoFile;
    private Bitmap currentBitmap;
    private String currentBitmapPath;

    CardView cardPicture;
    CardView cardCamera;
    CardView cardMap;
    CardView cardGo;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private List<Mat> images = new ArrayList<>();
    private List<LocationData> locationDataList = new ArrayList<>();
    private ActivityResultLauncher<Intent> startOrbActivityLauncher;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private DatabaseHelper dbHelper;
    private SQLiteDatabase database;

    private String currentLocation = "Unknown";
    private String selectedDestination = "";
    private Spinner destinationSpinner;
    private TextView currentLocationTextView;
    private Button buttonCorrectLocation;
    private Button buttonIncorrectLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            String photoUriString = savedInstanceState.getString(KEY_PHOTO_URI);
            if (photoUriString != null) {
                photoUri = Uri.parse(photoUriString);
                Log.d("MainActivity", "Restored photoUri: " + photoUri);
            }

            String photoFilePath = savedInstanceState.getString(KEY_PHOTO_FILE_PATH);
            if (photoFilePath != null) {
                photoFile = new File(photoFilePath);
                Log.d("MainActivity", "Restored photoFile: " + photoFilePath);
            }

            currentBitmapPath = savedInstanceState.getString(KEY_CURRENT_BITMAP_PATH);
            if (currentBitmapPath != null) {
                try {
                    currentBitmap = BitmapFactory.decodeFile(currentBitmapPath);
                    Log.d("MainActivity", "Restored currentBitmap from path: " + currentBitmapPath);
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to restore currentBitmap: " + e.getMessage());
                    currentBitmap = null;
                    currentBitmapPath = null;
                }
            }
        }

        bigmap = findViewById(R.id.bigmap);
        bigmap.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (currentBitmap != null) {
            bigmap.setImageBitmap(currentBitmap);
            Log.d("MainActivity", "Restored image to bigmap");
            updateButtonState(); // 恢復後檢查按鈕狀態
        }

        destinationSpinner = findViewById(R.id.destinationSpinner);

        buttonCorrectLocation = findViewById(R.id.buttonCorrectLocation);
        buttonIncorrectLocation = findViewById(R.id.buttonIncorrectLocation);

        // 設置按鈕點擊事件
        buttonCorrectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UnityPlayerActivity.class);
            startActivity(intent);
            Log.d("MainActivity", "Launching UnityPlayerActivity");
        });

        buttonIncorrectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WhereLocation.class); // 修正類名為 WhereLocationActivity
            startActivity(intent);
            Log.d("MainActivity", "Launching WhereLocationActivity");
        });

        // 初始禁用按鈕
        buttonCorrectLocation.setEnabled(false);
        buttonIncorrectLocation.setEnabled(false);

        setupDestinationSpinner();

        currentLocationTextView = findViewById(R.id.currentLocationTextView);

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
            Toast.makeText(this, "OpenCV 初始化失敗，請檢查應用配置", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Log.d("OpenCV", "OpenCV loaded successfully");

        cardPicture = findViewById(R.id.cardPicture);
        cardCamera = findViewById(R.id.cardCamera);
        cardMap = findViewById(R.id.cardMap);
        cardGo = findViewById(R.id.cardGo);
        ImageView menuIcon = findViewById(R.id.menuIcon);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.bringToFront();
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        cardMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.cameraproject_2.MapActivity.class);
            intent.putExtra("destination", selectedDestination);
            Log.d("MainActivity", "Sending destination to MapActivity: " + selectedDestination);
            startActivity(intent);
        });

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
                                    currentLocation = location;
                                    currentLocationTextView.setText("Location: " + location);
                                    Log.d("MainActivity", "Received location: " + location);
                                    updateButtonState(); // 收到 location 後更新按鈕狀態
                                } else {
                                    Toast.makeText(MainActivity.this, "位置信息為空", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Image processing cancelled or failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Log.d("MainActivity", "Photo captured successfully, processing image...");
                        if (photoUri != null) {
                            processCapturedImage(photoUri);
                        } else {
                            Log.e("MainActivity", "photoUri is null after capturing image");
                            Intent data = result.getData();
                            if (data != null && data.getData() != null) {
                                processCapturedImage(data.getData());
                            } else {
                                Toast.makeText(this, "無法獲取拍攝的照片，請重試", Toast.LENGTH_LONG).show();
                            }
                        }
                    } else {
                        Log.d("MainActivity", "Take picture canceled or failed, resultCode: " + result.getResultCode());
                        Toast.makeText(this, "拍照取消或失敗", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            Log.d("MainActivity", "Image selected successfully, processing image...");
                            processSelectedImage(selectedImageUri);
                        } else {
                            Log.e("MainActivity", "Selected image URI is null");
                            Toast.makeText(this, "無法獲取選擇的圖片", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d("MainActivity", "Image selection canceled or failed, resultCode: " + result.getResultCode());
                        Toast.makeText(this, "圖片選擇取消或失敗", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        setupClickListeners();

        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            headerView = navigationView.inflateHeaderView(R.layout.activity_menu_header);
        }
    }

    // 新增方法：檢查並更新按鈕狀態
    private void updateButtonState() {
        boolean shouldEnableButtons = currentBitmap != null && !currentLocation.equals("Unknown");
        buttonCorrectLocation.setEnabled(shouldEnableButtons);
        buttonIncorrectLocation.setEnabled(shouldEnableButtons);
        Log.d("MainActivity", "Button state updated: enabled=" + shouldEnableButtons);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoUri != null) {
            outState.putString(KEY_PHOTO_URI, photoUri.toString());
            Log.d("MainActivity", "Saved photoUri: " + photoUri);
        }
        if (photoFile != null) {
            outState.putString(KEY_PHOTO_FILE_PATH, photoFile.getAbsolutePath());
            Log.d("MainActivity", "Saved photoFile path: " + photoFile.getAbsolutePath());
        }
        if (currentBitmapPath != null) {
            outState.putString(KEY_CURRENT_BITMAP_PATH, currentBitmapPath);
            Log.d("MainActivity", "Saved currentBitmapPath: " + currentBitmapPath);
        }
    }

    private String saveBitmapToTempFile(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w("MainActivity", "Bitmap is null or recycled, cannot save to temp file");
            return null;
        }

        try {
            File tempDir = new File(getCacheDir(), "temp_bitmaps");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File tempFile = File.createTempFile("bitmap_", ".png", tempDir);
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }
            Log.d("MainActivity", "Bitmap saved to temp file: " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to save bitmap to temp file: " + e.getMessage());
            return null;
        }
    }

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
        destinationSpinner.setSelection(0);

        destinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLocation = locationNamesForDisplay.get(position);
                if (!selectedLocation.equals("請選擇目的地")) {
                    Toast.makeText(MainActivity.this, "您選擇了： " + selectedLocation, Toast.LENGTH_SHORT).show();
                    selectedDestination = selectedLocation;
                } else {
                    selectedDestination = "";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDestination = "";
            }
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.cardCamera).setOnClickListener(v -> {
            Log.d("MainActivity", "Camera button clicked");
            captureImage();
        });
        findViewById(R.id.cardPicture).setOnClickListener(v -> {
            Log.d("MainActivity", "Gallery button clicked");
            openGallery();
        });

        ImageView imageViewDatabase = findViewById(R.id.image_database);
        if (imageViewDatabase != null) {
            imageViewDatabase.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, database.class);
                startActivity(intent);
            });
        } else {
            Log.e("MainActivity", "image_view_database not found in layout");
        }
    }

    private void captureImage() {
        Log.d("MainActivity", "Starting captureImage()");

        // 修正權限檢查邏輯---直接預設授予存取+拍照的權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Permissions not granted, requesting permissions...");
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }

        try {
            photoFile = createImageFile();
            Log.d("MainActivity", "Image file created: " + photoFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("MainActivity", "Error creating image file: " + e.getMessage());
            Toast.makeText(this, "無法創建圖片文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            photoUri = FileProvider.getUriForFile(
                    this,
                    "com.example.cameraproject_2.fileprovider",
                    photoFile
            );
            Log.d("MainActivity", "Photo URI created: " + photoUri.toString());
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Error generating URI with FileProvider: " + e.getMessage());
            Toast.makeText(this, "無法生成圖片 URI，請檢查 FileProvider 配置", Toast.LENGTH_LONG).show();
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            Log.d("MainActivity", "Launching camera intent...");
            takePictureLauncher.launch(takePictureIntent);
        } else {
            Log.e("MainActivity", "No camera app available to handle intent");
            Toast.makeText(this, "找不到相機應用程式，請確保設備已安裝相機應用", Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null || !storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            Log.d("MainActivity", "Storage directory created: " + created);
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void saveImageToGallery(Bitmap imageBitmap) {
        if (imageBitmap == null || imageBitmap.isRecycled()) {
            Log.w("MainActivity", "Bitmap is null or recycled, cannot save to gallery");
            return;
        }

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

            Log.d("MainActivity", "Image saved to gallery: " + imageFile.getAbsolutePath());
            Toast.makeText(this, "圖片已儲存至相簿", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Storage", "Error saving image to gallery: " + e.getMessage());
            Toast.makeText(this, "無法儲存圖片至相簿", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Log.d("MainActivity", "Opening gallery...");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private Bitmap getCorrectedBitmap(Uri photoUri, Bitmap originalBitmap) {
        if (photoFile == null) {
            Log.w("MainActivity", "photoFile is null, cannot read EXIF data. Returning original bitmap.");
            return originalBitmap;
        }

        try {
            String path = photoFile.getAbsolutePath();
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationAngle = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
                default:
                    rotationAngle = 0;
                    break;
            }

            Log.d("MainActivity", "EXIF orientation: " + orientation + ", rotation angle: " + rotationAngle);

            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
                originalBitmap.recycle();
                Log.d("MainActivity", "Bitmap rotated by " + rotationAngle + " degrees");
                return rotatedBitmap;
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error reading EXIF data: " + e.getMessage());
        }
        return originalBitmap;
    }

    private void processCapturedImage(Uri photoUri) {
        if (photoUri == null) {
            Log.e("MainActivity", "photoUri is null in processCapturedImage");
            Toast.makeText(this, "無法處理拍攝的照片：URI 為空", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("MainActivity", "Processing captured image with URI: " + photoUri.toString());
        try {
            InputStream input = getContentResolver().openInputStream(photoUri);
            if (input == null) {
                Log.e("MainActivity", "Failed to open InputStream for photoUri");
                Toast.makeText(this, "無法讀取拍攝的圖片", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                Log.e("MainActivity", "Failed to decode bitmap from InputStream");
                Toast.makeText(this, "無法解碼拍攝的圖片", Toast.LENGTH_SHORT).show();
                input.close();
                return;
            }

            Log.d("MainActivity", "Bitmap decoded, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            bitmap = getCorrectedBitmap(photoUri, bitmap);
            if (bitmap == null) {
                Log.e("MainActivity", "Bitmap is null after getCorrectedBitmap");
                Toast.makeText(this, "無法處理圖片方向", Toast.LENGTH_SHORT).show();
                input.close();
                return;
            }

            currentBitmap = bitmap;
            currentBitmapPath = saveBitmapToTempFile(currentBitmap);
            runOnUiThread(() -> {
                Log.d("MainActivity", "Setting image to bigmap, bitmap size: " + currentBitmap.getWidth() + "x" + currentBitmap.getHeight());
                bigmap.setImageBitmap(currentBitmap);
                bigmap.setVisibility(View.VISIBLE);
                Log.d("MainActivity", "Image displayed on bigmap");
                updateButtonState(); // 圖片設置後更新按鈕狀態
            });

            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            images.add(mat);
            Log.d("MainActivity", "Image converted to Mat and added to images list");

            saveImageToGallery(bitmap);

            Intent intent = new Intent(MainActivity.this, ORBActivity.class);
            intent.putExtra("imageUri", photoUri.toString());
            Log.d("MainActivity", "Launching ORBActivity with image URI: " + photoUri.toString());
            startOrbActivityLauncher.launch(intent);

            input.close();
        } catch (IOException e) {
            Log.e("Camera", "Error processing captured image: " + e.getMessage());
            Toast.makeText(this, "處理拍攝的圖片時出錯", Toast.LENGTH_SHORT).show();
        }
    }

    private void processSelectedImage(Uri selectedImageUri) {
        Log.d("MainActivity", "Processing selected image with URI: " + selectedImageUri.toString());
        try {
            InputStream input = getContentResolver().openInputStream(selectedImageUri);
            if (input == null) {
                Log.e("MainActivity", "Failed to open InputStream for selectedImageUri");
                Toast.makeText(this, "無法讀取選擇的圖片", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                Log.e("MainActivity", "Failed to decode bitmap from InputStream");
                Toast.makeText(this, "無法解碼選擇的圖片", Toast.LENGTH_SHORT).show();
                input.close();
                return;
            }

            Log.d("MainActivity", "Bitmap decoded, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            currentBitmap = bitmap;
            currentBitmapPath = saveBitmapToTempFile(currentBitmap);
            runOnUiThread(() -> {
                Log.d("MainActivity", "Setting image to bigmap, bitmap size: " + currentBitmap.getWidth() + "x" + currentBitmap.getHeight());
                bigmap.setImageBitmap(currentBitmap);
                bigmap.setVisibility(View.VISIBLE);
                Log.d("MainActivity", "Image displayed on bigmap");
                updateButtonState(); // 圖片設置後更新按鈕狀態
            });

            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            images.add(mat);
            Log.d("MainActivity", "Image converted to Mat and added to images list");

            Intent intent = new Intent(MainActivity.this, ORBActivity.class);
            intent.putExtra("imageUri", selectedImageUri.toString());
            Log.d("MainActivity", "Launching ORBActivity with image URI: " + selectedImageUri.toString());
            startOrbActivityLauncher.launch(intent);

            input.close();
        } catch (IOException e) {
            Log.e("Gallery", "Error processing selected image: " + e.getMessage());
            Toast.makeText(this, "處理選擇的圖片時出錯", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "All permissions granted, proceeding with captureImage");
                captureImage();
            } else {
                Log.w("MainActivity", "Camera or storage permissions denied");
                Toast.makeText(this, "需要相機和儲存權限才能使用拍照功能", Toast.LENGTH_LONG).show();
            }
        }
    }
}