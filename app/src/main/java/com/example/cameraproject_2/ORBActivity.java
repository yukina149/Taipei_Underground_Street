package com.example.cameraproject_2;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ORBActivity extends AppCompatActivity {

    private ImageView uploadedImageView;
    private TextView locationTextView;
    private Mat uploadedImageMat;
    private PictureDatabaseHelper dbHelper;
    private SQLiteDatabase database;
    private List<LocationData> locationDataList;
    private ImageView databaseImageView;
    private ArrayList<MatchResult> matchResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orbactivity);

        // 初始化 OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("ORBActivity", "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV 初始化失敗，請檢查應用配置", Toast.LENGTH_LONG).show();
            finish();
            return;
        } else {
            Log.d("ORBActivity", "OpenCV initialized successfully");
        }

        uploadedImageView = findViewById(R.id.uploadedImageView);
        locationTextView = findViewById(R.id.locationTextView);
        databaseImageView = findViewById(R.id.databaseImageView);

        // Initialize PictureDatabaseHelper
        dbHelper = new PictureDatabaseHelper(this); // Constructor handles database creation
        database = dbHelper.getPictureDatabase(); // Get the managed database instance
        locationDataList = new ArrayList<>();
        loadLocationDataFromDatabase();

        Intent intent = getIntent();
        String imageUriString = intent.getStringExtra("imageUri");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            processImage(imageUri);
        } else {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void processImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            Log.d("ORBActivity", "Processing image: " + imageUri.toString() + ", dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            uploadedImageView.setImageBitmap(bitmap);

            uploadedImageMat = new Mat();
            Utils.bitmapToMat(bitmap, uploadedImageMat);
            Imgproc.cvtColor(uploadedImageMat, uploadedImageMat, Imgproc.COLOR_BGR2GRAY);

            ArrayList<MatchResult> topMatches = compareImageWithDatabase(uploadedImageMat, imageUri);
            Intent resultIntent = new Intent();
            if (!topMatches.isEmpty()) {
                String bestLocation = topMatches.get(0).getLocation();
                locationTextView.setText("Location: " + bestLocation);
                resultIntent.putExtra("location", bestLocation); // 設置最佳位置
                resultIntent.putParcelableArrayListExtra("topMatches", new ArrayList<>(topMatches));
                setResult(RESULT_OK, resultIntent);
            } else {
                locationTextView.setText("Location: Unknown");
                resultIntent.putExtra("location", "Unknown");
                resultIntent.putParcelableArrayListExtra("topMatches", new ArrayList<>());
                setResult(RESULT_OK, resultIntent);
            }
            finish();

            inputStream.close();
        } catch (IOException e) {
            Log.e("ORBActivity", "Error processing image: " + e.getMessage());
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private ArrayList<MatchResult> compareImageWithDatabase(Mat uploadedImage, Uri imageUri) {
        ORB orb = ORB.create();
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        orb.detectAndCompute(uploadedImage, new Mat(), keypoints1, descriptors1);

        matchResults.clear(); // 清空之前的匹配結果
        Log.d("ORBActivity", "Uploaded image keypoints: " + keypoints1.toArray().length);

        for (LocationData locationData : locationDataList) {
            String imageFileName = locationData.getImageFileName();
            Bitmap bitmap = getBitmapFromAsset(imageFileName);

            if (bitmap == null) {
                Log.e("ORBActivity", "無法從assets加載圖像：" + imageFileName);
                continue;
            }

            Mat databaseImage = new Mat();
            Utils.bitmapToMat(bitmap, databaseImage);
            Imgproc.cvtColor(databaseImage, databaseImage, Imgproc.COLOR_BGR2GRAY);

            MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
            Mat descriptors2 = new Mat();
            orb.detectAndCompute(databaseImage, new Mat(), keypoints2, descriptors2);

            Log.d("ORBActivity", "Database image " + imageFileName + " keypoints: " + keypoints2.toArray().length);

            if (descriptors1.empty() || descriptors2.empty()) {
                Log.w("ORBActivity", "Descriptors empty for " + imageFileName);
                databaseImage.release();
                continue;
            }

            MatOfDMatch matches = new MatOfDMatch();
            DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
            matcher.match(descriptors1, descriptors2, matches);

            List<DMatch> listOfMatches = matches.toList();
            List<DMatch> goodMatches = new ArrayList<>();

            double minDist = 100;
            for (DMatch match : listOfMatches) {
                if (match.distance < minDist) minDist = match.distance;
            }

            double dynamicThreshold = Math.min(2 * minDist, 50.0);
            Log.d("ORBActivity", "Min distance: " + minDist + ", Dynamic threshold: " + dynamicThreshold);

            for (DMatch match : listOfMatches) {
                if (match.distance <= dynamicThreshold) {
                    goodMatches.add(match);
                }
            }

            int numMatches = goodMatches.size();
            Log.d("ORBActivity", "Matches for " + imageFileName + ": " + numMatches);

            // 降低門檻，允許更多匹配進入結果
            if (numMatches >= 5) {
                // 構建 assets 文件的 URI（移除多餘的 images/ 前綴）
                String correctedFileName = imageFileName.startsWith("images/") ? imageFileName : "images/" + imageFileName;
                String imageUriString = "file://assets/" + correctedFileName; // e.g., "file://assets/images/21.jpg"
                matchResults.add(new MatchResult(imageUriString, locationData.getLocationName(), numMatches));
                Log.d("ORBActivity", "Added match: " + locationData.getLocationName() + ", matches: " + numMatches + ", uri: " + imageUriString);
            }

            databaseImage.release();
            matches.release();
        }

        // 按匹配數降序排序
        Collections.sort(matchResults, (a, b) -> b.getMatches() - a.getMatches());

        // 動態計算顯示筆數：總數的 1/3（四捨五入）
        int totalMatches = matchResults.size();
        int displayCount = Math.max(1, (int) Math.round(totalMatches / 3.0)); // 至少顯示 1 筆
        displayCount = Math.min(displayCount, matchResults.size()); // 不超過總數
        Log.d("ORBActivity", "Total matches: " + totalMatches + ", Display count: " + displayCount);

        return new ArrayList<>(matchResults.subList(0, displayCount));
    }

    private String getImageFileName(int imageId) {
        // Use the method from PictureDatabaseHelper
        return dbHelper.getImageFileName(imageId);
    }

    private void loadLocationDataFromDatabase() {
        SQLiteDatabase db = dbHelper.getPictureDatabase(); // Use the managed database instance
        Cursor cursor = null;
        try {
            // Replace raw query with PictureDatabaseHelper.getPictureData()
            for (int imageId = 1; imageId <= 21; imageId++) { // Assuming IDs 1 to 21 based on logs
                PictureDatabaseHelper.PictureData data = dbHelper.getPictureData(imageId);
                if (data != null) {
                    String locationName = data.locationData != null && !data.locationData.trim().isEmpty() ?
                            data.locationData : "未知位置";
                    Log.d("ORBActivity", "Loaded location data: " + locationName + " for image ID: " + imageId);

                    String imageFileName = dbHelper.getImageFileName(imageId);
                    Bitmap bitmap = getBitmapFromAsset(imageFileName);
                    String imageData = bitmap != null ? convertBitmapToBase64(bitmap) : null;
                    locationDataList.add(new LocationData(locationName, imageData, imageFileName));
                } else {
                    Log.w("ORBActivity", "No metadata found for imageId: " + imageId);
                }
            }
        } catch (Exception e) {
            Log.e("ORBActivity", "Error loading data from database: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d("ORBActivity", "Total locations loaded: " + locationDataList.size());
    }

    private Bitmap getBitmapFromAsset(String fileName) {
        try {
            // Load from context.getFilesDir() instead of assets
            File imageFile = new File(getFilesDir(), fileName);
            Log.d("ORBActivity", "Loading image from: " + imageFile.getAbsolutePath());
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            if (bitmap == null) {
                Log.e("ORBActivity", "Failed to decode bitmap from file: " + imageFile.getAbsolutePath());
            }
            return bitmap;
        } catch (Exception e) {
            Log.e("ORBActivity", "Error loading image from file: " + fileName + ", Error: " + e.getMessage());
            return null;
        }
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null && database.isOpen()) {
            database.close();
        }
        if (dbHelper != null) {
            dbHelper.closeDatabase();
        }
    }
}