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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

public class WhereLocation extends AppCompatActivity {

    private RecyclerView container;
    private TextView bottomText;
    private static final int REQUEST_LOCATION_CONFIRM = 1001;
    private static final String TAG = "WhereLocation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_where_location);

        container = findViewById(R.id.location_container);
        bottomText = findViewById(R.id.bottom_text);

        // 設置 RecyclerView
        container.setLayoutManager(new GridLayoutManager(this, 2)); // 兩欄
        LocationAdapter adapter = new LocationAdapter();
        container.setAdapter(adapter);
        container.setHasFixedSize(true); // 優化性能
        container.requestLayout(); // 強制重新計算佈局

        // 獲取傳遞的 topMatches
        Intent intent = getIntent();
        ArrayList<MatchResult> topMatches = intent.getParcelableArrayListExtra("topMatches");
        Log.d(TAG, "topMatches size: " + (topMatches != null ? topMatches.size() : "null"));
        if (topMatches == null || topMatches.isEmpty()) {
            Toast.makeText(this, "無匹配結果", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 更新底部文字
        bottomText.setText("共 " + topMatches.size() + " 個匹配結果");
        adapter.setData(topMatches);

        // 設置返回箭頭點擊事件，返回到 UploadImage
        findViewById(R.id.backArrow).setOnClickListener(v -> {
            onBackPressed();
        });
    }

    // 根據 uriString 獲取圖片路徑，與 DatabaseHelper 一致
    private String getImagePathFromUri(String uriString) {
        String fileName = uriString; // 預設為檔案名稱
        if (uriString.startsWith("file://")) {
            fileName = new File(Uri.parse(uriString).getPath()).getName();
        } else if (uriString.contains("/")) {
            fileName = new File(uriString).getName();
        }

        File imagesDir = new File(getFilesDir(), "images");
        String imagePath = new File(imagesDir, fileName).getAbsolutePath();
        Log.d(TAG, "Image path: " + imagePath);
        return imagePath;
    }

    private void showLocationDialog(String location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("確認位置");
        builder.setMessage("您現在的位置是：" + location + "\n是否確認？");
        builder.setPositiveButton("確認", (dialog, which) -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedLocation", location);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        builder.setNegativeButton("我再看看", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // RecyclerView Adapter
    private class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {
        private ArrayList<MatchResult> data = new ArrayList<>();

        public void setData(ArrayList<MatchResult> newData) {
            data.clear();
            if (newData != null) {
                data.addAll(newData);
                Log.d(TAG, "Adapter data size: " + data.size());
            }
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Log.d(TAG, "Binding item at position: " + position);
            MatchResult match = data.get(position);
            String uriString = match.getUri();
            String location = match.getLocation();

            // 載入圖片
            String imagePath = getImagePathFromUri(uriString);
            File imageFile = new File(imagePath);
            Bitmap bitmap = null;
            if (imageFile.exists()) {
                bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                Log.d(TAG, "Image loaded from: " + imagePath);
            }
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap);
            } else {
                Log.e(TAG, "Failed to load image from path: " + imagePath);
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // 設置位置文字
            holder.locationText.setText(location);
            holder.locationText.setVisibility(View.GONE);

            // 添加單擊事件
            holder.cardView.setOnClickListener(v -> toggleLocationInfo(holder.cardView, location));

            // 添加雙擊事件
            GestureDetector gestureDetector = new GestureDetector(WhereLocation.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    showLocationDialog(location);
                    return true;
                }
            });

            holder.cardView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardView;
            ImageView imageView;
            TextView locationText;

            public ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                imageView = itemView.findViewById(R.id.card_image);
                locationText = itemView.findViewById(R.id.card_location);
            }
        }
    }

    private void toggleLocationInfo(CardView cardView, String location) {
        TextView locationText = cardView.findViewById(R.id.card_location);
        if (locationText.getVisibility() == View.GONE) {
            locationText.setVisibility(View.VISIBLE);
            locationText.setText(location);
        } else {
            locationText.setVisibility(View.GONE);
        }
    }
}