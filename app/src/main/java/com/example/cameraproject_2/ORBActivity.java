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
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ORBActivity extends AppCompatActivity {

    private ImageView uploadedImageView;
    private TextView locationTextView;
    private Mat uploadedImageMat;
    private DatabaseHelper dbHelper;
    private SQLiteDatabase database;
    private List<LocationData> locationDataList;
    private ImageView databaseImageView;

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

        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDataBase();
        } catch (IOException e) {
            Log.e("ORBActivity", "Error creating database: " + e.getMessage());
        }

        database = dbHelper.openDataBase();
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

            String location = compareImageWithDatabase(uploadedImageMat);
            locationTextView.setText("Location: " + location);

            Intent intent = new Intent();
            intent.putExtra("location", location);
            setResult(RESULT_OK, intent);
            finish();

            inputStream.close();
        } catch (IOException e) {
            Log.e("ORBActivity", "Error processing image: " + e.getMessage());
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private String compareImageWithDatabase(Mat uploadedImage) {
        ORB orb = ORB.create();
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        orb.detectAndCompute(uploadedImage, new Mat(), keypoints1, descriptors1);

        String bestMatchLocation = "Unknown";
        int maxMatches = 0;
        Mat bestMatchImage = null;
        MatOfKeyPoint bestMatchKeyPoints = null;
        String bestMatchImageFileName = null;
        Bitmap bestMatchBitmap = null;

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

            if (numMatches > maxMatches && numMatches >= 10) {
                maxMatches = numMatches;
                bestMatchLocation = locationData.getLocationName();
                bestMatchImage = databaseImage.clone();
                bestMatchKeyPoints = keypoints2;
                bestMatchImageFileName = imageFileName;
                Log.d("ORBActivity", "New best match: " + bestMatchLocation + ", matches: " + maxMatches);
            }

            databaseImage.release();
            matches.release();
        }

        Log.d("ORBActivity", "Final best match location: " + bestMatchLocation + ", matches: " + maxMatches);

        if (bestMatchImageFileName != null) {
            bestMatchBitmap = getBitmapFromAsset(bestMatchImageFileName);
            if (bestMatchBitmap != null) {
                databaseImageView.setImageBitmap(bestMatchBitmap);
            } else {
                Log.e("ORBActivity", "無法從assets加載最佳匹配圖像：" + bestMatchImageFileName);
            }
        } else {
            databaseImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        Mat outputImage = new Mat();
        Imgproc.cvtColor(uploadedImage, outputImage, Imgproc.COLOR_GRAY2BGR);
        org.opencv.core.KeyPoint[] keyPoints = keypoints1.toArray();
        for (org.opencv.core.KeyPoint keyPoint : keyPoints) {
            Point pt = new Point(keyPoint.pt.x, keyPoint.pt.y);
            Scalar color = new Scalar(0, 255, 0);
            Imgproc.circle(outputImage, pt, 5, color, 2);
        }

        if (bestMatchImage != null) {
            Mat bestMatchOutputImage = new Mat();
            Imgproc.cvtColor(bestMatchImage, bestMatchOutputImage, Imgproc.COLOR_GRAY2BGR);
            if (bestMatchKeyPoints != null) {
                org.opencv.core.KeyPoint[] bestMatchKeyPointsArray = bestMatchKeyPoints.toArray();
                for (org.opencv.core.KeyPoint keyPoint : bestMatchKeyPointsArray) {
                    Point pt = new Point(keyPoint.pt.x, keyPoint.pt.y);
                    Scalar color = new Scalar(255, 0, 0);
                    Imgproc.circle(bestMatchOutputImage, pt, 5, color, 2);
                }
            }
        }

        Bitmap outputBitmap = Bitmap.createBitmap(outputImage.cols(), outputImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImage, outputBitmap);
        uploadedImageView.setImageBitmap(outputBitmap);

        return bestMatchLocation;
    }

    private String getImageFileName(int imageId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        String fileName = null;

        try {
            cursor = db.query(
                    "picture_data",
                    new String[]{"image", "file_extension"},
                    "image = ?",
                    new String[]{String.valueOf(imageId)},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String imageName = cursor.getString(cursor.getColumnIndexOrThrow("image"));
                String fileExtension = cursor.getString(cursor.getColumnIndexOrThrow("file_extension"));
                fileName = imageName + fileExtension;
            }
        } catch (Exception e) {
            Log.e("ORBActivity", "Error getting image file name from database: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return fileName;
    }

    private void loadLocationDataFromDatabase() {
        SQLiteDatabase db = dbHelper.openDataBase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    "picture_data",
                    new String[]{"location_data", "image"},
                    null, null, null, null, null
            );

            while (cursor.moveToNext()) {
                String locationName = cursor.getString(cursor.getColumnIndexOrThrow("location_data"));
                if (locationName == null || locationName.trim().isEmpty()) {
                    locationName = "未知位置";
                }
                Log.d("ORBActivity", "Loaded location data: " + locationName + " for image ID: " + cursor.getInt(cursor.getColumnIndexOrThrow("image")));

                int imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image"));
                String imageFileName = getImageFileName(imageId);
                Bitmap bitmap = getBitmapFromAsset(imageFileName);
                String imageData = bitmap != null ? convertBitmapToBase64(bitmap) : null;
                locationDataList.add(new LocationData(locationName, imageData, imageFileName));
            }
        } catch (Exception e) {
            Log.e("ORBActivity", "Error loading data from database: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Bitmap getBitmapFromAsset(String fileName) {
        try {
            InputStream inputStream = getAssets().open("images/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            return bitmap;
        } catch (IOException e) {
            Log.e("ORBActivity", "Error loading image from asset: " + e.getMessage());
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
        if (database != null) {
            database.close();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private static class LocationData {
        private String locationName;
        private String imageData;
        private String imageFileName;

        public LocationData(String locationName, String imageData, String imageFileName) {
            this.locationName = locationName;
            this.imageData = imageData;
            this.imageFileName = imageFileName;
        }

        public String getLocationName() {
            return locationName;
        }

        public String getImageData() {
            return imageData;
        }

        public String getImageFileName() {
            return imageFileName;
        }
    }
}