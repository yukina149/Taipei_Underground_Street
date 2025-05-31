package com.example.cameraproject_2;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;
import static com.example.cameraproject_2.PictureDatabaseHelper.PICTURE_DB_NAME;
import static com.example.cameraproject_2.RegisterDatabaseHelper.REGISTER_DB_NAME;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.unity3d.player.UnityPlayerActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    static {
        System.loadLibrary("opencv_java4");
    }

    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION_CODE = 1002;
    private static final int REQUEST_LOCATION_CONFIRM = 1001;
    private static final String KEY_PHOTO_URI = "photoUri";
    private static final String KEY_PHOTO_FILE_PATH = "photoFilePath";
    private static final String KEY_CURRENT_BITMAP_PATH = "currentBitmapPath";
    private static final String INVITATION_CHECK_URL = "http://192.168.10.15/android_studio/fetch_invitations.php";
    private static final String USER_ID_KEY = "userId";
    private static final long CHECK_INTERVAL = 30000; // 每 30 秒檢查一次

    private ImageView bigmap;
    private ImageView smallmap;
    private Uri photoUri;
    private File photoFile;
    private Bitmap currentBitmap;
    private String currentBitmapPath;

    CardView cardPicture;
    CardView cardCamera;

    private List<Mat> images = new ArrayList<>();
    private List<LocationData> locationDataList = new ArrayList<>();
    private ArrayList<MatchResult> topMatches = new ArrayList<>();
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    private RegisterDatabaseHelper registerDbHelper;
    private PictureDatabaseHelper pictureDbHelper;
    private SQLiteDatabase database;

    private String currentLocation = "Unknown";
    private String selectedDestination = "";
    private Spinner destinationSpinner;
    private TextView currentLocationTextView;
    private Button buttonCorrectLocation;
    private Button buttonIncorrectLocation;

    private Handler handler = new Handler();
    private Runnable invitationChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
        dbHelper.setServerUrl("http://192.168.10.15/android_studio");

        if (drawerLayout == null || navigationView == null) {
            Toast.makeText(this, "Navigation setup failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.bringToFront();
        navigationView.setNavigationItemSelectedListener(this);

        updateNavigationMenu();
        updateHeader();

        registerDbHelper = new RegisterDatabaseHelper(this);
        pictureDbHelper = new PictureDatabaseHelper(this);
        try {
            pictureDbHelper.createDataBase();
            registerDbHelper.getRegisterDatabase();
            database = pictureDbHelper.getPictureDatabase();
        } catch (IOException e) {
            Log.e("MainActivity", "Error creating databases: " + e.getMessage());
            Toast.makeText(this, "Database initialization failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bigmap = findViewById(R.id.bigmap);
        bigmap.setScaleType(ImageView.ScaleType.FIT_CENTER);
        smallmap = findViewById(R.id.smallmap);
        smallmap.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (savedInstanceState != null) {
            String photoUriString = savedInstanceState.getString(KEY_PHOTO_URI);
            if (photoUriString != null) photoUri = Uri.parse(photoUriString);

            String photoFilePath = savedInstanceState.getString(KEY_PHOTO_FILE_PATH);
            if (photoFilePath != null) photoFile = new File(photoFilePath);

            currentBitmapPath = savedInstanceState.getString(KEY_CURRENT_BITMAP_PATH);
            if (currentBitmapPath != null) {
                try {
                    currentBitmap = BitmapFactory.decodeFile(currentBitmapPath);
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to restore currentBitmap: " + e.getMessage());
                    currentBitmap = null;
                    currentBitmapPath = null;
                }
            }
        }

        if (currentBitmap != null) {
            bigmap.setImageBitmap(currentBitmap);
        }

        buttonCorrectLocation = findViewById(R.id.buttonCorrectLocation);
        buttonIncorrectLocation = findViewById(R.id.buttonIncorrectLocation);

        buttonCorrectLocation.setEnabled(false);
        buttonIncorrectLocation.setEnabled(false);

        buttonCorrectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UnityPlayerActivity.class);
            startActivity(intent);
        });

        buttonIncorrectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WhereLocation.class);
            if (topMatches.isEmpty()) {
                Toast.makeText(this, "請先拍攝或選擇圖片以獲取匹配結果", Toast.LENGTH_SHORT).show();
                return;
            }
            intent.putParcelableArrayListExtra("topMatches", topMatches);
            activityResultLauncher.launch(intent);
        });

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

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        if (intent != null) {
                            String locationFromORB = intent.getStringExtra("location");
                            if (locationFromORB != null && !locationFromORB.isEmpty()) {
                                currentLocation = locationFromORB;
                                currentLocationTextView.setText("Location: " + locationFromORB);

                                ArrayList<MatchResult> matches = intent.getParcelableArrayListExtra("topMatches");
                                if (matches != null && !matches.isEmpty()) {
                                    topMatches.clear();
                                    topMatches.addAll(matches);

                                    if (!topMatches.isEmpty()) {
                                        MatchResult bestMatch = topMatches.get(0);
                                        String imageUriString = bestMatch.getUri();
                                        Log.d("MainActivity", "Image URI from ORBActivity: " + imageUriString);
                                        String fileName = imageUriString.replace("file://assets/", "");
                                        Log.d("MainActivity", "Attempting to load file: " + fileName);
                                        try {
                                            InputStream inputStream = getAssets().open(fileName);
                                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                            inputStream.close();
                                            smallmap.setImageBitmap(bitmap);
                                            Log.d("MainActivity", "Set smallmap with image: " + fileName);
                                        } catch (IOException e) {
                                            Log.e("MainActivity", "Error loading image for smallmap: " + fileName + ", Error: " + e.getMessage());
                                            Toast.makeText(this, "無法加載匹配的地圖圖片", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }

                                if (!currentLocation.equals("Unknown") && !currentLocation.isEmpty()) {
                                    buttonCorrectLocation.setEnabled(true);
                                    buttonIncorrectLocation.setEnabled(true);
                                } else {
                                    buttonCorrectLocation.setEnabled(false);
                                    buttonIncorrectLocation.setEnabled(false);
                                }
                                return;
                            }

                            String selectedLocation = intent.getStringExtra("selectedLocation");
                            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                                currentLocation = selectedLocation;
                                currentLocationTextView.setText("Location: " + selectedLocation);
                                Toast.makeText(this, "位置已更新為：" + selectedLocation, Toast.LENGTH_SHORT).show();

                                if (!currentLocation.equals("Unknown") && !currentLocation.isEmpty()) {
                                    buttonCorrectLocation.setEnabled(true);
                                    buttonIncorrectLocation.setEnabled(false);
                                } else {
                                    buttonCorrectLocation.setEnabled(false);
                                    buttonIncorrectLocation.setEnabled(false);
                                }
                            } else {
                                Toast.makeText(this, "未選擇位置", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Toast.makeText(this, "操作取消或失敗", Toast.LENGTH_SHORT).show();
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Log.d("MainActivity", "Photo captured successfully, processing image...");
                        if (photoUri != null) {
                            processCapturedImage(photoUri);
                        } else {
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
                });

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
                });

        setupClickListeners();

        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            headerView = navigationView.inflateHeaderView(R.layout.activity_menu_header);
        }

        createNotificationChannel();
        startInvitationChecking();
    }

    @Override
    protected void updateNavigationMenu() {
        if (navigationView == null) {
            Log.e("MainActivity", "navigationView is null");
            return;
        }

        Menu navMenu = navigationView.getMenu();
        navMenu.clear();

        getMenuInflater().inflate(R.menu.menu_main, navMenu);

        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        int order = 100;
        for (String groupName : groupNames) {
            navMenu.add(Menu.NONE, Menu.NONE, order++, groupName)
                    .setIcon(R.drawable.store_icon);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.personal_account) {
            Intent intent = new Intent(this, PersonalAccount.class);
            startActivity(intent);
        } else if (id == R.id.Chat_room) {
            boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
            if (isLoggedIn) {
                Intent intent = new Intent(MainActivity.this, Chatroom.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(MainActivity.this, PersonalAccount.class);
                startActivity(intent);
            }
        } else if (id == R.id.Create_Group) {
            Intent intent = new Intent(MainActivity.this, CreateGroupActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("確認登出")
                    .setMessage("您確定要登出嗎？")
                    .setPositiveButton("確定", (dialog, which) -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("loggedInUser");
                        editor.putBoolean("isLoggedIn", false);
                        editor.putString("userId", "訪客");
                        editor.apply();
                        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(MainActivity.this, PersonalAccount.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            String groupName = item.getTitle().toString();
            Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
            if (groupNames.contains(groupName)) {
                String membersString = sharedPreferences.getString(groupName + "_members", "");
                List<String> members = new ArrayList<>();
                if (!membersString.isEmpty()) {
                    String[] membersArray = membersString.split(",");
                    for (String member : membersArray) {
                        members.add(member);
                    }
                }
                Intent intent = new Intent(MainActivity.this, Chatroom.class);
                intent.putExtra("groupName", groupName);
                intent.putStringArrayListExtra("members", new ArrayList<>(members));
                startActivity(intent);
            }
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoUri != null) outState.putString(KEY_PHOTO_URI, photoUri.toString());
        if (photoFile != null) outState.putString(KEY_PHOTO_FILE_PATH, photoFile.getAbsolutePath());
        if (currentBitmapPath != null) outState.putString(KEY_CURRENT_BITMAP_PATH, currentBitmapPath);
    }

    private String saveBitmapToTempFile(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w("MainActivity", "Bitmap is null or recycled, cannot save to temp file");
            return null;
        }

        try {
            File tempDir = new File(getCacheDir(), "temp_bitmaps");
            if (!tempDir.exists()) tempDir.mkdirs();
            File tempFile = File.createTempFile("bitmap_", ".png", tempDir);
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }
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
            if (position == 0) ((TextView) view).setText("請選擇目的地");
            return view;
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.cardCamera).setOnClickListener(v -> captureImage());
        findViewById(R.id.cardPicture).setOnClickListener(v -> openGallery());
    }

    private void captureImage() {
        Log.d("MainActivity", "Starting captureImage()");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }

        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            Log.e("MainActivity", "Error creating image file: " + e.getMessage());
            Toast.makeText(this, "無法創建圖片文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            photoUri = FileProvider.getUriForFile(this, "com.example.cameraproject_2.fileprovider", photoFile);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Error generating URI with FileProvider: " + e.getMessage());
            Toast.makeText(this, "無法生成圖片 URI，請檢查 FileProvider 配置", Toast.LENGTH_LONG).show();
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
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
        if (storageDir == null || !storageDir.exists()) storageDir.mkdirs();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void saveImageToGallery(Bitmap imageBitmap) {
        if (imageBitmap == null || imageBitmap.isRecycled()) {
            Log.w("MainActivity", "Bitmap is null or recycled, cannot save to gallery");
            return;
        }

        // Use app-specific external storage instead of public storage
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";
        File imageFile = new File(storageDir, fileName);

        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(imageFile));
            sendBroadcast(mediaScanIntent);

            Log.d("MainActivity", "Image saved to app storage: " + imageFile.getAbsolutePath());
            Toast.makeText(this, "圖片已儲存至應用儲存空間", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Storage", "Error saving image to app storage: " + e.getMessage());
            Toast.makeText(this, "無法儲存圖片", Toast.LENGTH_SHORT).show();
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
            }

            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
                originalBitmap.recycle();
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
                bigmap.setImageBitmap(currentBitmap);
                bigmap.setVisibility(View.VISIBLE);
            });

            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            images.add(mat);

            saveImageToGallery(bitmap);

            Intent intent = new Intent(MainActivity.this, ORBActivity.class);
            intent.putExtra("imageUri", photoUri.toString());
            activityResultLauncher.launch(intent);

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

            currentBitmap = bitmap;
            currentBitmapPath = saveBitmapToTempFile(currentBitmap);
            runOnUiThread(() -> {
                bigmap.setImageBitmap(currentBitmap);
                bigmap.setVisibility(View.VISIBLE);
            });

            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            images.add(mat);

            Intent intent = new Intent(MainActivity.this, ORBActivity.class);
            intent.putExtra("imageUri", selectedImageUri.toString());
            activityResultLauncher.launch(intent);

            input.close();
        } catch (IOException e) {
            Log.e("Gallery", "Error processing selected image: " + e.getMessage());
            Toast.makeText(this, "處理選擇的圖片時出錯", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                captureImage();
            } else {
                Log.w("MainActivity", "Camera or storage permissions denied");
                Toast.makeText(this, "需要相機和儲存權限才能使用拍照功能", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知權限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要通知權限以接收群組邀請通知", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkInvitations() {
        String currentUserId = sharedPreferences.getString(USER_ID_KEY, "訪客");
        if (currentUserId.equals("訪客")) {
            Log.d("MainActivity", "User not logged in, skipping invitation check");
            return;
        }

        RegisterDatabaseHelper dbHelper = new RegisterDatabaseHelper(this);
        List<Invitation> invitations = dbHelper.getPendingInvitations(currentUserId);
        Log.d("MainActivity", "Found " + invitations.size() + " pending invitations for userId: " + currentUserId);

        for (Invitation invitation : invitations) {
            String groupName = invitation.getGroupName();
            // 檢查是否已經處理過該邀請
            if (!isGroupInPreferences(groupName)) {
                Log.d("MainActivity", "New invitation for group: " + groupName);
                runOnUiThread(() -> showInvitationDialog(groupName, invitation.getInvitationId()));
                updateGroupInPreferences(groupName);
                updateNavigationMenu();
            }
        }
    }
    private void showInvitationDialog(String groupName, String invitationId) {
        new AlertDialog.Builder(this)
                .setTitle("群組邀請")
                .setMessage("您被邀請加入群組: " + groupName)
                .setPositiveButton("接受", (dialog, which) -> {
                    registerDbHelper.updateInvitationStatus(invitationId, "accepted");
                    Toast.makeText(this, "已接受群組邀請：" + groupName, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("拒絕", (dialog, which) -> {
                    registerDbHelper.updateInvitationStatus(invitationId, "rejected");
                    Toast.makeText(this, "已拒絕群組邀請：" + groupName, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private boolean isGroupInPreferences(String groupName) {
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        return groupNames.contains(groupName);
    }

    private void updateGroupInPreferences(String groupName) {
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        groupNames.add(groupName);
        sharedPreferences.edit().putStringSet("groupNames", groupNames).apply();
    }

    private void showInvitationNotification(String groupName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "invitation_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Use default system icon
                .setContentTitle("新群組邀請")
                .setContentText("您收到來自群組 " + groupName + " 的邀請")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION_CODE);
            return;
        }
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Invitation Channel";
            String description = "Channel for group invitation notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("invitation_channel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startInvitationChecking() {
        invitationChecker = new Runnable() {
            @Override
            public void run() {
                // 先同步資料庫
                registerDbHelper.syncInvitations();
                // 檢查是否有新邀請
                checkInvitations();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(invitationChecker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavigationMenu();
        updateHeader();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && invitationChecker != null) {
            handler.removeCallbacks(invitationChecker);
        }
        if (registerDbHelper != null) registerDbHelper.closeDatabase();
        if (pictureDbHelper != null) pictureDbHelper.closeDatabase();
    }
}