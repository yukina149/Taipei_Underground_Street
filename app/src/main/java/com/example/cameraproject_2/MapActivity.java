package com.example.cameraproject_2;
import android.os.Bundle;
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

        // 設定點擊事件
        cardAR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // AR 卡片被點擊後執行的操作
                Toast.makeText(MapActivity.this, "AR 選項被點擊！", Toast.LENGTH_SHORT).show();
                // 這裡可以加入跳轉到 AR 功能的 Intent
            }
        });

        cardMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Media 卡片被點擊後執行的操作
                Toast.makeText(MapActivity.this, "Media 選項被點擊！", Toast.LENGTH_SHORT).show();
                // 這裡可以加入跳轉到 Media 功能的 Intent
            }
        });
    }
}

