package com.example.cameraproject_2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

public class WhereLocation extends AppCompatActivity {

    private LinearLayout container;
    private static final int REQUEST_LOCATION_CONFIRM = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_where_location);
        Button buttonBack = findViewById(R.id.buttonBack);

        container = findViewById(R.id.location_container);

        // 獲取傳遞的 topMatches
        Intent intent = getIntent();
        ArrayList<MatchResult> topMatches = intent.getParcelableArrayListExtra("topMatches");
        if (topMatches == null || topMatches.isEmpty()) {
            Toast.makeText(this, "無匹配結果", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 顯示所有 topMatches 中的資料
        for (int i = 0; i < topMatches.size(); i++) {
            MatchResult match = topMatches.get(i);
            addLocationItem(match.getUri(), match.getLocation(), i + 1);
        }

        buttonBack.setOnClickListener(v -> {
            finish(); // 返回 MainActivity
        });
    }

    private void addLocationItem(String uriString, String location, int index) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 16);
        itemLayout.setLayoutParams(layoutParams);

        ImageView imageView = new ImageView(this);

        // 將 uriString 轉換為 DatabaseHelper 儲存的圖片路徑
        String imagePath = getImagePathFromUri(uriString);
        File imageFile = new File(imagePath);
        Bitmap bitmap = null;
        if (imageFile.exists()) {
            bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            Log.e("WhereLocationActivity", "Failed to load image from path: " + imagePath);
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300
        ));

        // 添加單擊事件（顯示/隱藏位置資訊）
        imageView.setOnClickListener(v -> toggleLocationInfo(itemLayout, location));

        // 添加雙擊事件（使用 GestureDetector）
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showLocationDialog(location);
                return true;
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false; // 允許其他事件（如單擊）繼續處理
        });

        itemLayout.addView(imageView);

        TextView locationText = new TextView(this);
        locationText.setText(location);
        locationText.setPadding(16, 8, 16, 8);
        locationText.setVisibility(View.GONE);
        itemLayout.addView(locationText);

        container.addView(itemLayout);
    }

    // 根據 uriString 獲取圖片路徑，與 DatabaseHelper 一致
    private String getImagePathFromUri(String uriString) {
        // 假設 uriString 是圖片檔案名稱（例如從 MatchResult 傳來的）
        // 或者需要解析 URI 得到檔案名稱
        String fileName = uriString; // 預設為檔案名稱
        if (uriString.startsWith("file://")) {
            fileName = new File(Uri.parse(uriString).getPath()).getName();
        } else if (uriString.contains("/")) {
            fileName = new File(uriString).getName();
        }

        // 構建與 DatabaseHelper 相同的圖片路徑
        File imagesDir = new File(getFilesDir(), "images");
        return new File(imagesDir, fileName).getAbsolutePath();
    }

    private void toggleLocationInfo(LinearLayout itemLayout, String location) {
        TextView locationText = (TextView) itemLayout.getChildAt(1);
        if (locationText.getVisibility() == View.GONE) {
            locationText.setVisibility(View.VISIBLE);
            locationText.setText(location);
        } else {
            locationText.setVisibility(View.GONE);
        }
    }

    private void showLocationDialog(String location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("確認位置");
        builder.setMessage("您現在的位置是：" + location + "\n是否確認？");
        builder.setPositiveButton("確認", (dialog, which) -> {
            // 將選中的位置傳回 MainActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedLocation", location);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        builder.setNegativeButton("我再看看", (dialog, which) -> {
            // 關閉對話框，不做任何操作
            dialog.dismiss();
        });
        builder.setCancelable(false); // 禁止點擊外部關閉對話框
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}