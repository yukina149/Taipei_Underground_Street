package com.example.cameraproject_2;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

//public class ORB extends AppCompatActivity
public class ORBActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 100; //用於從圖庫選擇圖片
    private ImageView uploadedImageView; //顯示上傳的圖片的 ImageView
    private TextView locationTextView; // 顯示匹配位置的 TextView
    private Button uploadButton;//按鈕
    private Mat uploadedImageMat;// 存儲上傳圖片的 Mat 物件
    private DatabaseHelper dbHelper;//使用資料庫
    private SQLiteDatabase database;//SQLite 資料庫物件
    private List<LocationData> locationDataList;//存儲位置資料的清單

    private ImageView databaseImageView; // 顯示最佳匹配圖像的 ImageView


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orbactivity);

        uploadedImageView = findViewById(R.id.uploadedImageView);
        locationTextView = findViewById(R.id.locationTextView);
        uploadButton = findViewById(R.id.uploadButton);
        databaseImageView = findViewById(R.id.databaseImageView);


        dbHelper = new DatabaseHelper(this);

        // 建立資料庫
        try {
            dbHelper.createDataBase();// 調用方法創建資料庫
        } catch (IOException e) {
            Log.e("ORBActivity", "Error creating database: " + e.getMessage());
        }

        database = dbHelper.openDataBase();// 打開資料庫連接
        locationDataList = new ArrayList<>();// 初始化位置資料清單
        loadLocationDataFromDatabase(); // 從資料庫載入位置資料

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
    }


    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);// 啟動圖庫選擇活動，並等待結果
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                uploadedImageView.setImageBitmap(bitmap);

                // Convert Bitmap to Mat
                // 將 Bitmap 轉換為 Mat 對象以便進行影像處理
                uploadedImageMat = new Mat();
                Utils.bitmapToMat(bitmap, uploadedImageMat);

                // Convert to grayscale
                // 將圖像轉換為灰度圖以便於處理和比較
                Imgproc.cvtColor(uploadedImageMat, uploadedImageMat, Imgproc.COLOR_BGR2GRAY);


                // Compare with database images
                // 與資料庫中的圖像進行比較，獲取匹配的位置名稱

                String location = compareImageWithDatabase(uploadedImageMat);
                locationTextView.setText("Location: " + location);// 顯示匹配位置名稱

                // 返回位置信息给 MainActivity
                Intent intent = new Intent();
                intent.putExtra("location", location);
                Log.e("location", location);
                setResult(RESULT_OK, intent);
                //finish(); // 關閉頁面


            } catch (IOException e) {
                Log.e("ORBActivity", "Error processing image: " + e.getMessage());
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String compareImageWithDatabase(Mat uploadedImage) {
        ORB orb = ORB.create();// 創建 ORB 特徵檢測器實例
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();// 存儲上傳圖像的關鍵點集合
        Mat descriptors1 = new Mat();// 存儲上傳圖像的描述符集合
        orb.detectAndCompute(uploadedImage, new Mat(), keypoints1, descriptors1);// 檢測關鍵點並計算描述符

        String bestMatchLocation = "Unknown";// 初始化最佳匹配位置名稱為未知
        int maxMatches = 0;// 初始化最大匹配數量為0
        Mat bestMatchImage = null; // 用於儲存最佳匹配的影像
        MatOfKeyPoint bestMatchKeyPoints = null; // 用於儲存最佳匹配的關鍵點
        String bestMatchImageFileName = null; // 用於儲存最佳匹配的圖片檔案名稱

        for (LocationData locationData : locationDataList) {
            String imageFileName = locationData.getImageFileName();
            Bitmap bitmap = getBitmapFromAsset(imageFileName);

            // 如果無法加載圖像，則跳過
            if (bitmap == null) {
                Log.e("ORBActivity", "無法從assets加載圖像：" + imageFileName);
                continue;
            }

            // Convert Bitmap to Mat
            Mat databaseImage = new Mat();
            Utils.bitmapToMat(bitmap, databaseImage);

            // Convert to grayscale
            Imgproc.cvtColor(databaseImage, databaseImage, Imgproc.COLOR_BGR2GRAY);

            MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
            Mat descriptors2 = new Mat();
            orb.detectAndCompute(databaseImage, new Mat(), keypoints2, descriptors2);

            // Matching descriptors
            MatOfDMatch matches = new MatOfDMatch();
            DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
            matcher.match(descriptors1, descriptors2, matches);

            List<DMatch> listOfMatches = matches.toList();
            List<DMatch> good_matches = new ArrayList<>();

            // 調整距離閾值
            double distanceThreshold = 50.0; // 調整此值

            // Filter matches based on distance
            double max_dist = 0;

            double min_dist = 100;

            for (int i = 0; i < descriptors1.rows(); i++) {
                double dist = listOfMatches.get(i).distance;
                if (dist <= Math.max(2 * min_dist, 0.02) && dist < distanceThreshold) { // 加入距離閾值判斷
                    good_matches.add(listOfMatches.get(i));
                }
            }

            int numMatches = good_matches.size();
            int numberOfKeyPoints = keypoints1.toArray().length;
            Log.d("ORBActivity", "Number of Key Points: " + numberOfKeyPoints);//列印抓取的特徵向量數量


            if (numMatches > maxMatches) {
                maxMatches = numMatches;
                bestMatchLocation = locationData.getLocationName();
                bestMatchImage = databaseImage.clone(); // 儲存最佳匹配的影像
                bestMatchKeyPoints = keypoints2; // 儲存最佳匹配的關鍵點
                bestMatchImageFileName = imageFileName; // 儲存最佳匹配的圖片檔案名稱
            }
        }

        // 載入最佳匹配圖片並顯示
        if (bestMatchImageFileName != null) {
            Bitmap bestMatchBitmap = getBitmapFromAsset(bestMatchImageFileName);
            if (bestMatchBitmap != null) {
                databaseImageView.setImageBitmap(bestMatchBitmap);
            } else {
                Log.e("ORBActivity", "無法從assets加載最佳匹配圖像：" + bestMatchImageFileName);
            }
        }
        else {
            // 如果找不到最佳匹配的圖片，則設定預設圖片或清除 ImageView
            databaseImageView.setImageResource(android.R.drawable.ic_menu_gallery); // 使用預設圖片
        }

        // 在上傳的圖片上繪製關鍵點
        Mat outputImage = new Mat();
        Imgproc.cvtColor(uploadedImage, outputImage, Imgproc.COLOR_GRAY2BGR);
        org.opencv.core.KeyPoint[] keyPoints = keypoints1.toArray();
        for (org.opencv.core.KeyPoint keyPoint : keyPoints) {
            Point pt = new Point(keyPoint.pt.x, keyPoint.pt.y);
            Scalar color = new Scalar(0, 255, 0); // 綠色
            Imgproc.circle(outputImage, pt, 5, color, 2);// 在关键点位置绘制圆圈标记
        }

        // 如果有最佳匹配，則在最佳匹配圖像上繪製關鍵點
        if (bestMatchImage != null) {
            Mat bestMatchOutputImage = new Mat();
            Imgproc.cvtColor(bestMatchImage, bestMatchOutputImage, Imgproc.COLOR_GRAY2BGR);
            if (bestMatchKeyPoints != null) {
                org.opencv.core.KeyPoint[] bestMatchKeyPointsArray = bestMatchKeyPoints.toArray();
                for (org.opencv.core.KeyPoint keyPoint : bestMatchKeyPointsArray) {
                    Point pt = new Point(keyPoint.pt.x, keyPoint.pt.y);
                    Scalar color = new Scalar(255, 0, 0); // 藍色
                    Imgproc.circle(bestMatchOutputImage, pt, 5, color, 2);
                }
            }

            // 將最佳匹配圖像顯示在另一個 ImageView 中 (如果有的話)
            Bitmap bestMatchBitmap = Bitmap.createBitmap(bestMatchOutputImage.cols(), bestMatchOutputImage.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(bestMatchOutputImage, bestMatchBitmap);
            //bestMatchImageView.setImageBitmap(bestMatchBitmap);
        }


        // 將結果圖像顯示在 uploadedImageView 中
        Bitmap outputBitmap = Bitmap.createBitmap(outputImage.cols(), outputImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImage, outputBitmap);
        uploadedImageView.setImageBitmap(outputBitmap);

        return bestMatchLocation;
    }

    //讀取圖片
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
                int imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image"));

                // 從 assets 資料夾讀取圖片
                String imageFileName = getImageFileName(imageId);
                Bitmap bitmap = getBitmapFromAsset(imageFileName);

                // 轉換 Bitmap 為 Base64 字串
                String imageData = convertBitmapToBase64(bitmap);
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
        private String imageFileName; // 新增

        public LocationData(String locationName, String imageData, String imageFileName) {
            this.locationName = locationName;
            this.imageData = imageData;
            this.imageFileName = imageFileName; // 新增
        }

        public String getLocationName() {
            return locationName;
        }

        public String getImageData() {
            return imageData;
        }

        public String getImageFileName() { // 新增
            return imageFileName;
        }
    }

}
