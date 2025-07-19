package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
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
            Log.d(TAG, "Do not show guide again, skipping to MainActivity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_app_guide);

        // 初始化 ViewPager 和其他元件
        viewPager = findViewById(R.id.viewPager);
        checkBoxDoNotShowAgain = findViewById(R.id.checkBoxDoNotShowAgain);
        tabLayout = findViewById(R.id.tabLayout);

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
        guideImages.add(R.drawable.person);
        guideImages.add(R.drawable.white_app_name);
        guideImages.add(R.drawable.trainupload);
        Log.d(TAG, "Guide images loaded: " + guideImages.size());

        // 設置 ViewPager 適配器
        GuideImageAdapter adapter = new GuideImageAdapter(guideImages);
        viewPager.setAdapter(adapter);

        // 設置 TabLayout 與 ViewPager2 關聯，顯示底線指示器
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            Log.d(TAG, "TabLayoutMediator: tab position " + position);
            // 不設置標籤文字，使用預設底線
        }).attach();

        // 監聽 ViewPager 頁面變化
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "ViewPager page selected: " + position);
                if (position == guideImages.size() - 1) {
                    // 最後一頁，延遲 1 秒後跳轉到 MainActivity
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        saveDoNotShowPreference();
                        Log.d(TAG, "Navigating to MainActivity from last page");
                        startActivity(new Intent(AppGuideActivity.this, MainActivity.class));
                        finish();
                    }, 1000);
                }
            }
        });

        // 設置「不再顯示」勾選框
        checkBoxDoNotShowAgain.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDoNotShowPreference();
            if (isChecked) {
                Log.d(TAG, "Do not show again checked, navigating to MainActivity");
                startActivity(new Intent(AppGuideActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private void saveDoNotShowPreference() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_DO_NOT_SHOW_GUIDE, checkBoxDoNotShowAgain.isChecked());
        editor.apply();
        Log.d(TAG, "Saved doNotShowGuide preference: " + checkBoxDoNotShowAgain.isChecked());
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
            if (getArguments() != null) {
                imageView.setImageResource(getArguments().getInt(ARG_IMAGE_RES));
            }
            return view;
        }
    }
}