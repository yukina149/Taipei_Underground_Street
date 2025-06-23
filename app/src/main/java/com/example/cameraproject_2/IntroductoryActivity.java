package com.example.cameraproject_2;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class IntroductoryActivity extends AppCompatActivity {

    ImageView logo, appName;
    private int animationCompletedCount = 0; // 跟踪完成動畫的數量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_introductory);

        MyApplication app = (MyApplication) getApplication();
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String langCode = sharedPreferences.getString("language", Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en");
        app.setLocale();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        logo = findViewById(R.id.logo);
        appName = findViewById(R.id.app_name);

        // 檢查 logo 資源
        logo.setImageResource(R.drawable.icon_logo);
        if (logo.getDrawable() == null) {
            Log.e("IntroductoryActivity", "Failed to load icon_logo image");
        }

        appName.setImageResource(R.drawable.logoin_unlost);
        if (appName.getDrawable() == null) {
            Log.e("IntroductoryActivity", "Failed to load logoin_unlost image");
        }

        logo.setAlpha(0f); // 一開始隱藏
        appName.setAlpha(0f); // 一開始隱藏

        // 同時啟動 logo 和 appName 的進入動畫
        animateEntrance(logo, -2000f, 0f, 500, 1000); // 從上方滑入
        animateEntrance(appName, 2000f, 0f, 500, 1000); // 從下方滑入，與 logo 同步
    }

    private void animateEntrance(View view, float fromY, float toY, long startDelay, long duration) {
        ObjectAnimator entranceAnimator = ObjectAnimator.ofFloat(view, "translationY", fromY, toY);
        entranceAnimator.setDuration(duration);
        entranceAnimator.setStartDelay(startDelay);
        entranceAnimator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                view.setAlpha(1f); // 動畫開始時顯示
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // 進入動畫結束後延遲幾秒，然後啟動離開動畫
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    animateExit(view, toY, view.getId() == R.id.logo ? -2000f : 2000f, 0);
                    // logo 滑出上方，appName 滑出下方
                }, 1000); // 停留1秒
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {}

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {}
        });
        entranceAnimator.start();
    }

    private void animateExit(View view, float fromY, float toY, long startDelay) {
        ObjectAnimator exitAnimator = ObjectAnimator.ofFloat(view, "translationY", fromY, toY);
        exitAnimator.setDuration(1000); // 離開動畫持續1秒
        exitAnimator.setStartDelay(startDelay);
        exitAnimator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {}

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animationCompletedCount++; // 動畫結束時增加計數
                if (animationCompletedCount == 2) { // 兩個離開動畫都結束
                    // 無需觸發 lottieAnimationView，僅完成跳轉邏輯
                }
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {}

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {}
        });
        exitAnimator.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 約3.5秒後跳轉到 MainActivity（500ms 延遲 + 1000ms 進入 + 1000ms 停留 + 1000ms 離開）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(IntroductoryActivity.this, MainActivity.class));
            finish();
        }, 3500);
    }
}