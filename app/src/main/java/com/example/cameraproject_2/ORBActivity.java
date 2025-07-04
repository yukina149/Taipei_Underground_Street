package com.example.cameraproject_2;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
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
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
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

        dbHelper = new PictureDatabaseHelper(this);
        database = dbHelper.getPictureDatabase();
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

            int targetWidth = 800;
            int targetHeight = (int) (bitmap.getHeight() * ((float) targetWidth / bitmap.getWidth()));
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

            uploadedImageView.setImageBitmap(scaledBitmap);

            uploadedImageMat = new Mat();
            Utils.bitmapToMat(scaledBitmap, uploadedImageMat);
            Imgproc.cvtColor(uploadedImageMat, uploadedImageMat, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(uploadedImageMat, uploadedImageMat);

            new Thread(() -> {
                ArrayList<MatchResult> topMatches = compareImageWithDatabase(uploadedImageMat, imageUri);
                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();
                    Log.d("ORBActivity", "Top matches size: " + topMatches.size());
                    if (!topMatches.isEmpty()) {
                        String bestLocation = topMatches.get(0).getLocation();
                        locationTextView.setText("Location: " + bestLocation);
                        resultIntent.putExtra("location", bestLocation);
                        resultIntent.putParcelableArrayListExtra("topMatches", new ArrayList<>(topMatches));
                        Log.d("ORBActivity", "Returning location: " + bestLocation);
                    } else {
                        locationTextView.setText("Location: Unknown");
                        resultIntent.putExtra("location", "Unknown");
                        resultIntent.putParcelableArrayListExtra("topMatches", new ArrayList<>());
                        Log.d("ORBActivity", "Returning location: Unknown");
                    }
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
            }).start();

            inputStream.close();
        } catch (IOException e) {
            Log.e("ORBActivity", "Error processing image: " + e.getMessage());
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private ArrayList<MatchResult> compareImageWithDatabase(Mat uploadedImage, Uri imageUri) {
        ORB orb = ORB.create(1000);
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        orb.detectAndCompute(uploadedImage, new Mat(), keypoints1, descriptors1);

        matchResults.clear();
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

            double dynamicThreshold = Math.min(2 * minDist, 40.0);
            Log.d("ORBActivity", "Min distance: " + minDist + ", Dynamic threshold: " + dynamicThreshold);

            for (DMatch match : listOfMatches) {
                if (match.distance <= dynamicThreshold) {
                    goodMatches.add(match);
                }
            }

            // RANSAC 幾何驗證
            if (goodMatches.size() > 10) {
                MatOfDMatch goodMatchesMat = new MatOfDMatch();
                goodMatchesMat.fromList(goodMatches);

                List<KeyPoint> keypoints1List = keypoints1.toList();
                List<KeyPoint> keypoints2List = keypoints2.toList();
                MatOfPoint2f srcPts = new MatOfPoint2f();
                MatOfPoint2f dstPts = new MatOfPoint2f();
                List<org.opencv.core.Point> srcPoints = new ArrayList<>();
                List<org.opencv.core.Point> dstPoints = new ArrayList<>();

                for (DMatch match : goodMatches) {
                    srcPoints.add(keypoints1List.get(match.queryIdx).pt);
                    dstPoints.add(keypoints2List.get(match.trainIdx).pt);
                }
                srcPts.fromList(srcPoints);
                dstPts.fromList(dstPoints);

                Mat mask = new Mat();
                Mat homography = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 3, mask);
                if (homography.rows() > 0 && homography.cols() > 0) {
                    int inliers = Core.countNonZero(mask);
                    if (inliers > 3) { // 降低門檻以測試
                        int numMatchesAdjusted = inliers;
                        Log.d("ORBActivity", "Matches for " + imageFileName + " after RANSAC: " + numMatchesAdjusted);

                        if (numMatchesAdjusted >= 3) { // 降低門檻以測試
                            String correctedFileName = imageFileName.startsWith("images/") ? imageFileName : "images/" + imageFileName;
                            String imageUriString = "file://assets/" + correctedFileName;
                            matchResults.add(new MatchResult(imageUriString, locationData.getLocationName(), numMatchesAdjusted));
                            Log.d("ORBActivity", "MatchResult added: location=" + locationData.getLocationName() + ", matches=" + numMatchesAdjusted + ", size=" + matchResults.size());
                        }
                    }
                    mask.release();
                }
            }

            databaseImage.release();
            matches.release();
        }

        Collections.sort(matchResults, (a, b) -> b.getMatches() - a.getMatches());
        int totalMatches = matchResults.size();
        int displayCount = Math.max(1, (int) Math.round(totalMatches / 3.0));
        displayCount = Math.min(displayCount, matchResults.size());
        Log.d("ORBActivity", "Before subList: totalMatches=" + totalMatches + ", displayCount=" + displayCount);
        ArrayList<MatchResult> topMatches = new ArrayList<>(matchResults.subList(0, displayCount));
        Log.d("ORBActivity", "After subList: topMatches size=" + topMatches.size() + ", first match=" + (topMatches.isEmpty() ? "empty" : topMatches.get(0).getLocation()));
        return topMatches;
    }

    private String getImageFileName(int imageId) {
        // Use the method from PictureDatabaseHelper
        return dbHelper.getImageFileName(imageId);
    }

    private void loadLocationDataFromDatabase() {
        SQLiteDatabase db = dbHelper.getPictureDatabase();
        locationDataList.clear();
        Cursor cursor = null;
        try {
            cursor = db.query("picture_data",
                    new String[]{"image", "name", "description", "location_data", "latitude", "longitude", "file_extension"},
                    null, null, null, null, "image ASC");
            while (cursor.moveToNext()) {
                int imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image"));
                String locationData = cursor.getString(cursor.getColumnIndexOrThrow("location_data"));
                String locationName = (locationData != null && !locationData.trim().isEmpty()) ? locationData : "未知位置";
                String fileExtension = cursor.getString(cursor.getColumnIndexOrThrow("file_extension"));
                String imageFileName = "images/" + imageId + fileExtension;

                Bitmap bitmap = getBitmapFromAsset(imageFileName);
                String imageData = bitmap != null ? convertBitmapToBase64(bitmap) : null;
                locationDataList.add(new LocationData(locationName, imageData, imageFileName));
                Log.d("ORBActivity", "Loaded location data: " + locationName + " for image ID: " + imageId);
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
            File imageFile = new File(new File(getFilesDir(), "images"), fileName.replace("images/", ""));
            Log.d("ORBActivity", "Loading image from: " + imageFile.getAbsolutePath());
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            if (bitmap == null) {
                Log.e("ORBActivity", "Failed to decode bitmap from file: " + imageFile.getAbsolutePath());
            }
            return bitmap;
        } catch (Exception e) {
            Log.e("ORBActivity", "Error loading image from file: " + fileName + ", Error: " + e.getMessage());
            return null; // 返回 null，允許後續邏輯處理缺失情況
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