package com.example.cameraproject_2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);  // 連結 XML 佈局

        // 綁定 CardView
        CardView cardAR = findViewById(R.id.map_cardAR);
        CardView cardMedia = findViewById(R.id.map_cardMedia);
        CardView cardUnityAR = findViewById(R.id.map_UnityAR);

        // 從 Intent 中接收 destination
        String selectedDestination = getIntent().getStringExtra("destination");
        if (selectedDestination == null || selectedDestination.isEmpty()) {
            selectedDestination = "未選擇目的地";
        }
        Log.d("MapActivity", "Received destination from MainActivity: " + selectedDestination);

        // 修正：使用一個新的變數來避免重新賦值
        final String destinationToShow = selectedDestination;
        Log.d("MapActivity", "Destination to show: " + destinationToShow);

        // 設定點擊事件
        cardAR.setOnClickListener(v -> {
            Toast.makeText(MapActivity.this, "AR 選項被點擊！", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MapActivity.this, ARNavigationActivity.class);
            intent.putExtra("destination", destinationToShow); // 傳遞目的地
            Log.d("MapActivity", "Sending destination to ARNavigationActivity: " + destinationToShow);
            startActivity(intent);
        });

        cardMedia.setOnClickListener(v -> {
            Toast.makeText(MapActivity.this, "Media 選項被點擊！", Toast.LENGTH_SHORT).show();
        });

        cardUnityAR.setOnClickListener(v -> {
            Toast.makeText(MapActivity.this, "Unity AR 選項被點擊！", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MapActivity.this, UnityHanderActivity.class);
            startActivity(intent);
        });
    }
}
