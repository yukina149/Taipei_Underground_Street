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
    private TextView textViewFareResult;
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
        textViewFareResult = findViewById(R.id.textViewFareResult);
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

        viewModel.fareResult.observe(this, fare -> {
            if (fare != null && !fare.isEmpty()) {
                textViewFareResult.setText(fare);
                textViewFareResult.setVisibility(View.VISIBLE);
            } else {
                textViewFareResult.setText(""); // Clear previous
                textViewFareResult.setVisibility(View.GONE);
            }
        });

        viewModel.distanceResult.observe(this, distance -> {
            if (distance != null && !distance.isEmpty()) {
                textViewDistanceResult.setText(distance);
                textViewDistanceResult.setVisibility(View.VISIBLE);
            } else {
                textViewDistanceResult.setText(""); // Clear previous
                textViewDistanceResult.setVisibility(View.GONE);
            }
        });

        viewModel.isLoading.observe(this, isLoading -> {
            if (isLoading != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                buttonQueryFare.setEnabled(!isLoading);
            }
        });

        viewModel.errorMessage.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                // Optionally clear results on error
                textViewFareResult.setText("");
                textViewFareResult.setVisibility(View.GONE);
                textViewDistanceResult.setText("");
                textViewDistanceResult.setVisibility(View.GONE);
            }
        });
    }
}
