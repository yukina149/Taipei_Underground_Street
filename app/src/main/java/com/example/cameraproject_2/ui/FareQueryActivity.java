package com.example.cameraproject_2.ui;


import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList; // For empty adapter

// Import your ViewBinding class if you are using it
// import YOUR_PACKAGE_NAME.databinding.ActivityFareQueryBinding;
import com.example.cameraproject_2.R; // R file for layout and IDs

public class FareQueryActivity extends AppCompatActivity {

    // For ViewBinding:
    // private ActivityFareQueryBinding binding;

    // For findViewById:
    private AutoCompleteTextView autoCompleteStartStation;
    private AutoCompleteTextView autoCompleteEndStation;
    private Button buttonQueryFare;
    // --- 8/29修改：更新 TextView 變數 ---
    private TextView textViewFullFareResult;
    private TextView textViewConcessionFareResult;
    private TextView textViewTaipeiChildFareResult;
    private TextView textViewDistanceResult;
    private ProgressBar progressBar;

    private FareQueryViewModel viewModel;
    private String selectedStartStation = null;
    private String selectedEndStation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- Option 1: Using ViewBinding (Recommended) ---
        // binding = ActivityFareQueryBinding.inflate(getLayoutInflater());
        // setContentView(binding.getRoot());
        //
        // // Initialize views using binding
        // autoCompleteStartStation = binding.autoCompleteStartStation;
        // autoCompleteEndStation = binding.autoCompleteEndStation;
        // buttonQueryFare = binding.buttonQueryFare;
        // textViewFareResult = binding.textViewFareResult;
        // textViewDistanceResult = binding.textViewDistanceResult;
        // progressBar = binding.progressBar;

        // --- Option 2: Using findViewById ---
        setContentView(R.layout.activity_fare_query);
        autoCompleteStartStation = findViewById(R.id.autoCompleteStartStation);
        autoCompleteEndStation = findViewById(R.id.autoCompleteEndStation);
        buttonQueryFare = findViewById(R.id.buttonQueryFare);
        // --- 修改：獲取新的 TextView 引用 ---
        textViewFullFareResult = findViewById(R.id.textViewFullFareResult);
        textViewConcessionFareResult = findViewById(R.id.textViewConcessionFareResult);
        textViewTaipeiChildFareResult = findViewById(R.id.textViewTaipeiChildFareResult);
        textViewDistanceResult = findViewById(R.id.textViewDistanceResult);
        progressBar = findViewById(R.id.progressBar);
        // --- End of findViewById option ---

        viewModel = new ViewModelProvider(this).get(FareQueryViewModel.class);

        setupStationDropdowns();
        setupButton();
        observeViewModel();
    }

    private void setupStationDropdowns() {
        // Initialize with an empty adapter first to prevent null pointer if data loads later
        ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        autoCompleteStartStation.setAdapter(emptyAdapter);
        autoCompleteEndStation.setAdapter(emptyAdapter);

        autoCompleteStartStation.setOnItemClickListener((parent, view, position, id) ->
                selectedStartStation = (String) parent.getItemAtPosition(position)
        );
        autoCompleteEndStation.setOnItemClickListener((parent, view, position, id) ->
                selectedEndStation = (String) parent.getItemAtPosition(position)
        );
    }

    private void setupButton() {
        buttonQueryFare.setOnClickListener(v -> {
            if (selectedStartStation != null && !selectedStartStation.isEmpty() &&
                    selectedEndStation != null && !selectedEndStation.isEmpty()) {
                viewModel.queryFare(selectedStartStation, selectedEndStation);
                // *** 在開始新查詢前，清除所有舊的結果 ***
                clearResultTextViews();
                viewModel.queryFare(selectedStartStation, selectedEndStation);
            } else {
                Toast.makeText(this, "請選擇起點和終點站", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void observeViewModel() {
        viewModel.stationList.observe(this, stations -> {
            if (stations != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, stations);
                autoCompleteStartStation.setAdapter(adapter);
                autoCompleteEndStation.setAdapter(adapter);
            }
        });

        // --- 修改：觀察新的票價 LiveData ---
        viewModel.fullFareResult.observe(this, fare -> {
            updateTextView(textViewFullFareResult, fare);
        });

        viewModel.concessionFareResult.observe(this, fare -> {
            updateTextView(textViewConcessionFareResult, fare);
        });

        viewModel.taipeiChildFareResult.observe(this, fare -> {
            updateTextView(textViewTaipeiChildFareResult, fare);
        });
        // --- 修改結束 ---
        // *** 確保 distanceResult 也使用 updateTextView 輔助方法 ***
        viewModel.distanceResult.observe(this, distance -> {
            updateTextView(textViewDistanceResult, distance);
        });

        viewModel.isLoading.observe(this, isLoading -> {
            if (isLoading != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                buttonQueryFare.setEnabled(!isLoading); // 查詢時禁用按鈕
            }
        });

        viewModel.errorMessage.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                // *** 發生錯誤時，清除所有結果 ***
                clearResultTextViews();
            }
        });
    }

    // 輔助方法來更新 TextView 的文本和可見性
    private void updateTextView(TextView textView, String text) {
        if (text != null && !text.isEmpty()) {
            textView.setText(text);
            textView.setVisibility(View.VISIBLE);
        } else {
            // 如果文本為空或null，則清除文本並隱藏 TextView
            textView.setText("");
            textView.setVisibility(View.GONE);
        }
    }

    // 輔助方法來清除所有結果 TextView 的內容並隱藏它們
    private void clearResultTextViews() {
        updateTextView(textViewFullFareResult, null);
        updateTextView(textViewConcessionFareResult, null);
        updateTextView(textViewTaipeiChildFareResult, null);
        updateTextView(textViewDistanceResult, null);
    }
}
