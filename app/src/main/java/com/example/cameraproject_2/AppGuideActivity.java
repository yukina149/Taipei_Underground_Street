package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;

public class AppGuideActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private CheckBox checkBoxDoNotShowAgain;
    private TabLayout tabLayout;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_DO_NOT_SHOW_GUIDE = "doNotShowGuide";
    private List<Integer> guideImages;
    private static final String TAG = "AppGuideActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 檢查是否需要顯示使用說明
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean doNotShowGuide = sharedPreferences.getBoolean(KEY_DO_NOT_SHOW_GUIDE, false);
        if (doNotShowGuide) {
            Log.d(TAG, "不再顯示引導頁面，跳轉到 MainActivity");
            navigateToMainActivity();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_app_guide);

        // 初始化元件
        viewPager = findViewById(R.id.viewPager);
        checkBoxDoNotShowAgain = findViewById(R.id.checkBoxDoNotShowAgain);
        tabLayout = findViewById(R.id.tabLayout);

        if (viewPager == null || checkBoxDoNotShowAgain == null || tabLayout == null) {
            Log.e(TAG, "初始化元件失敗，無法找到 ViewPager、CheckBox 或 TabLayout");
            Toast.makeText(this, "初始化失敗，請重試", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 設置語言
        MyApplication app = (MyApplication) getApplication();
        app.setLocale();

        // 設置系統欄內距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化使用說明圖片清單
        guideImages = new ArrayList<>();
        try {
            guideImages.add(R.drawable.person);
            guideImages.add(R.drawable.white_app_name);
            guideImages.add(R.drawable.trainupload);
            Log.d(TAG, "成功載入引導圖片: " + guideImages.size());
        } catch (Exception e) {
            Log.e(TAG, "載入引導圖片失敗: " + e.getMessage());
            Toast.makeText(this, "載入圖片失敗，請重試", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 設置 ViewPager 適配器
        GuideImageAdapter adapter = new GuideImageAdapter(guideImages);
        viewPager.setAdapter(adapter);

        // 設置 TabLayout 與 ViewPager2 關聯
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            Log.d(TAG, "TabLayoutMediator: 標籤位置 " + position);
        }).attach();

        // 監聽 ViewPager 頁面變化
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "ViewPager 選擇頁面: " + position);
                if (position == guideImages.size() - 1) {
                    // 最後一頁，延遲 2 秒後跳轉到 MainActivity
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        saveDoNotShowPreference();
                        Log.d(TAG, "從最後一頁跳轉到 MainActivity");
                        navigateToMainActivity();
                    }, 2000); // 增加延遲到 2 秒
                }
            }
        });

        // 設置「不再顯示」勾選框
        checkBoxDoNotShowAgain.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDoNotShowPreference();
            if (isChecked) {
                Log.d(TAG, "勾選不再顯示，跳轉到 MainActivity");
                new Handler(Looper.getMainLooper()).postDelayed(() -> navigateToMainActivity(), 500); // 添加 0.5 秒延遲
            }
        });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(AppGuideActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void saveDoNotShowPreference() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_DO_NOT_SHOW_GUIDE, checkBoxDoNotShowAgain.isChecked());
        editor.apply();
        Log.d(TAG, "保存不再顯示偏好: " + checkBoxDoNotShowAgain.isChecked());
    }

    // ViewPager 適配器
    private class GuideImageAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final List<Integer> imageResources;

        public GuideImageAdapter(List<Integer> imageResources) {
            super(AppGuideActivity.this);
            this.imageResources = imageResources;
        }

        @Override
        public int getItemCount() {
            return imageResources.size();
        }

        @Override
        public androidx.fragment.app.Fragment createFragment(int position) {
            return GuideImageFragment.newInstance(imageResources.get(position));
        }
    }

    // 用於顯示單張圖片的 Fragment
    public static class GuideImageFragment extends androidx.fragment.app.Fragment {
        private static final String ARG_IMAGE_RES = "image_res";

        public static GuideImageFragment newInstance(int imageRes) {
            GuideImageFragment fragment = new GuideImageFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_IMAGE_RES, imageRes);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_guide_image, container, false);
            ImageView imageView = view.findViewById(R.id.guideImage);
            if (getArguments() != null && imageView != null) {
                try {
                    imageView.setImageResource(getArguments().getInt(ARG_IMAGE_RES));
                } catch (Exception e) {
                    Log.e(TAG, "設置圖片資源失敗: " + e.getMessage());
                    Toast.makeText(getContext(), "載入圖片失敗", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "無法設置圖片，參數或 ImageView 為空");
            }
            return view;
        }
    }
}