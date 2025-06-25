package com.example.cameraproject_2.ui;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException; // 需要引入
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale; // For String.format

import com.example.cameraproject_2.model.FareEntry; // 引入你的模型
import com.example.cameraproject_2.model.MetroApiResponse; // 引入你的模型
import com.example.cameraproject_2.model.MetroApiResult; // 需要引入
import com.example.cameraproject_2.network.TaipeiMetroApiService; // 引入API服務

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FareQueryViewModel extends ViewModel {

    private static final String TAG = "FareQueryVM";

    private final MutableLiveData<List<String>> _stationList = new MutableLiveData<>();
    public final LiveData<List<String>> stationList = _stationList;

    private final MutableLiveData<String> _fareResult = new MutableLiveData<>();
    public final LiveData<String> fareResult = _fareResult;

    private final MutableLiveData<String> _distanceResult = new MutableLiveData<>();
    public final LiveData<String> distanceResult = _distanceResult;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private final List<FareEntry> allFareEntries = new ArrayList<>(); // 累積所有票價條目
    private final TaipeiMetroApiService apiService;

    private static final int API_LIMIT_PER_REQUEST = 1000; // API 每次請求的上限
    private int currentOffset = 0;
    private boolean isLoadingAllPages = false; // 標記是否正在載入所有分頁

    public FareQueryViewModel() {
        // HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        // loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        // OkHttpClient client = new OkHttpClient.Builder()
        //         .addInterceptor(loggingInterceptor)
        //         .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://data.taipei/")
                // .client(client) // 如果使用 OkHttp Logging Interceptor
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(TaipeiMetroApiService.class);
        fetchAllFareDataPages(); // 更改呼叫的方法
    }

    private void fetchAllFareDataPages() {
        if (isLoadingAllPages) {
            return; // 如果已經在載入，則不重複執行
        }
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        allFareEntries.clear(); // 清空舊資料
        currentOffset = 0;    // 重置位移
        isLoadingAllPages = true; // 開始載入
        Log.d(TAG, "Starting to fetch all fare data pages...");
        fetchNextPage(); // 開始獲取第一頁
    }

    private void fetchNextPage() {
        Log.d(TAG, "Fetching page with offset: " + currentOffset + ", limit: " + API_LIMIT_PER_REQUEST);
        // 注意：你需要修改 TaipeiMetroApiService 介面以接受 offset 和 limit
        apiService.getMetroFares(API_LIMIT_PER_REQUEST, currentOffset).enqueue(new Callback<MetroApiResponse>() {
            @Override
            public void onResponse(Call<MetroApiResponse> call, Response<MetroApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    MetroApiResult result = response.body().getResult();
                    List<FareEntry> currentPageEntries = result.getFareEntries();

                    if (currentPageEntries != null && !currentPageEntries.isEmpty()) {
                        allFareEntries.addAll(currentPageEntries);
                        Log.d(TAG, "Fetched " + currentPageEntries.size() + " entries. Total entries now: " + allFareEntries.size());

                        // 檢查是否還有更多資料
                        // 假設 MetroApiResult 中有 count 欄位 (根據常見的 data.taipei API 格式)
                        // 你需要確認你的 MetroApiResult POJO 中是否有 @SerializedName("count") private int count;
                        // 並且為它添加 getter: public int getCount() { return count; }
                        int totalCount = result.getCount(); // <<<<< 假設你的 POJO 有這個欄位和方法
                        Log.d(TAG, "API reports total count: " + totalCount);


                        if (allFareEntries.size() < totalCount && currentPageEntries.size() == API_LIMIT_PER_REQUEST) {
                            // 如果當前獲取的總數小於 API 報告的總數，並且這次請求滿了 limit，則繼續獲取下一頁
                            currentOffset += API_LIMIT_PER_REQUEST;
                            fetchNextPage(); // 遞迴獲取下一頁
                        } else {
                            // 所有資料都已獲取完畢
                            Log.d(TAG, "All pages fetched. Total entries: " + allFareEntries.size());
                            processLoadedData();
                        }
                    } else {
                        // 當前頁沒有資料，或者 entries 為空，也視為結束
                        Log.d(TAG, "Current page has no entries or API returned empty list for this page. Assuming all fetched.");
                        processLoadedData();
                    }
                } else {
                    isLoadingAllPages = false;
                    _isLoading.setValue(false);
                    Log.e(TAG, "API Error Response. Code: " + response.code() + ", Message: " + response.message());
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "null";
                        Log.e(TAG, "Error Body: " + errorBody);
                        _errorMessage.setValue("無法載入部分票價資料 (錯誤碼：" + response.code() + ")");
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                        _errorMessage.setValue("無法載入部分票價資料 (錯誤碼：" + response.code() + ")");
                    }
                    // 即使部分失敗，也嘗試處理已載入的資料
                    if(!allFareEntries.isEmpty()){
                        Log.w(TAG, "Processing partially loaded data due to API error on subsequent page.");
                        processLoadedData();
                    } else {
                        _stationList.setValue(new ArrayList<>()); // 確保清空
                    }
                }
            }

            @Override
            public void onFailure(Call<MetroApiResponse> call, Throwable t) {
                isLoadingAllPages = false;
                _isLoading.setValue(false);
                Log.e(TAG, "Network Error during paged fetch", t);
                _errorMessage.setValue("網路連線失敗，請稍後再試。");
                if(!allFareEntries.isEmpty()){
                    Log.w(TAG, "Processing partially loaded data due to network error.");
                    processLoadedData();
                } else {
                    _stationList.setValue(new ArrayList<>()); // 確保清空
                }
            }
        });
    }

    private void processLoadedData() {
        isLoadingAllPages = false; // 所有分頁（或嘗試）載入完畢
        _isLoading.setValue(false);

        if (allFareEntries.isEmpty()) {
            Log.w(TAG, "No fare entries loaded after fetching all pages.");
            if (_errorMessage.getValue() == null) { // 如果還沒有其他錯誤訊息
                _errorMessage.setValue("未能載入任何票價資料。");
            }
            _stationList.setValue(new ArrayList<>());
            return;
        }

        Set<String> stationSet = new HashSet<>();
        for (FareEntry entry : allFareEntries) {
            if (entry.getFromStation() != null && !entry.getFromStation().isEmpty()) {
                stationSet.add(entry.getFromStation());
            }
            if (entry.getToStation() != null && !entry.getToStation().isEmpty()) {
                stationSet.add(entry.getToStation());
            }
        }
        List<String> sortedStations = new ArrayList<>(stationSet);
        Collections.sort(sortedStations);
        _stationList.setValue(sortedStations);
        Log.d(TAG, "Station list updated with " + sortedStations.size() + " unique stations.");
    }


    // queryFare 方法保持不變，它會使用已經填充好的 allFareEntries
    public void queryFare(String startStation, String endStation) {
        // ... (之前的 queryFare 邏輯)
        // 確保在 queryFare 的開頭，也檢查 isLoadingAllPages，如果還在載入所有頁面，可以提示使用者稍等
        if (isLoadingAllPages) {
            _errorMessage.setValue("票價資料仍在載入中，請稍候...");
            return;
        }

        _isLoading.setValue(true); // 查詢過程也顯示 loading
        _fareResult.setValue(null);
        _distanceResult.setValue(null);
        _errorMessage.setValue(null);


        if (startStation == null || startStation.isEmpty() || endStation == null || endStation.isEmpty()) {
            _errorMessage.setValue("請選擇起點和終點站。");
            _isLoading.setValue(false);
            return;
        }
        if (startStation.equals(endStation)) {
            // 這個情況應該由 API 資料處理，例如 "松山機場" 到 "松山機場" 票價 20
            // 但如果業務邏輯不允許同站查詢，可以在這裡攔截
            // _errorMessage.setValue("起點和終點站不能相同。");
            // _isLoading.setValue(false);
            // return;
        }

        FareEntry foundEntry = null;
        Log.d(TAG, "Querying for: Start='" + startStation + "', End='" + endStation + "'");
        Log.d(TAG, "Total fare entries available for query: " + allFareEntries.size());


        if (!allFareEntries.isEmpty()) {
            for (int i = 0; i < allFareEntries.size(); i++) {
                FareEntry entry = allFareEntries.get(i);
                String entryFrom = entry.getFromStation();
                String entryTo = entry.getToStation();

                // (可選) 移除之前非常詳細的比較日誌，或保留用於調試
                // Log.d(TAG, "Entry #" + i + ": FromAPI='" + entryFrom + "', ToAPI='" + entryTo + ...);

                boolean startMatches = (entryFrom != null && startStation.equals(entryFrom));
                boolean endMatches = (entryTo != null && endStation.equals(entryTo));

                if (startMatches && endMatches) {
                    foundEntry = entry;
                    Log.i(TAG, ">>>> Found matching entry (forward) at index " + i + ": " +
                            entryFrom + " -> " + entryTo);
                    break;
                }
            }
        }

        if (foundEntry != null) {
            _fareResult.setValue("全票票價：NT$ " + foundEntry.getFullFare());
            String distanceStr = foundEntry.getDistance();
            try {
                if (distanceStr != null && !distanceStr.trim().isEmpty()) {
                    double distValue = Double.parseDouble(distanceStr);
                    _distanceResult.setValue(String.format(Locale.US, "距離：%.1f km", distValue));
                } else {
                    _distanceResult.setValue("距離：無資料");
                }
            } catch (NumberFormatException e) {
                _distanceResult.setValue("距離：" + (distanceStr != null ? distanceStr : "無資料"));
                Log.w(TAG, "Could not parse distance: " + distanceStr, e);
            }
        } else {
            // 嘗試反向查詢
            FareEntry foundReverseEntry = null;
            if (!allFareEntries.isEmpty()) {
                for (int i = 0; i < allFareEntries.size(); i++) {
                    FareEntry entry = allFareEntries.get(i);
                    String entryFrom = entry.getFromStation();
                    String entryTo = entry.getToStation();
                    boolean reverseStartMatches = (entryFrom != null && endStation.equals(entryFrom));
                    boolean reverseEndMatches = (entryTo != null && startStation.equals(entryTo));
                    if (reverseStartMatches && reverseEndMatches) {
                        foundReverseEntry = entry;
                        Log.i(TAG, ">>>> Found matching entry (reverse) at index " + i + ": " +
                                entryFrom + " -> " + entryTo);
                        break;
                    }
                }
            }
            if (foundReverseEntry != null) {
                _fareResult.setValue("全票票價：NT$ " + foundReverseEntry.getFullFare());
                String distanceStr = foundReverseEntry.getDistance();
                try {
                    if (distanceStr != null && !distanceStr.trim().isEmpty()) {
                        double distValue = Double.parseDouble(distanceStr);
                        _distanceResult.setValue(String.format(Locale.US, "距離：%.1f km", distValue));
                    } else {
                        _distanceResult.setValue("距離：無資料");
                    }
                } catch (NumberFormatException e) {
                    _distanceResult.setValue("距離：" + (distanceStr != null ? distanceStr : "無資料"));
                    Log.w(TAG, "Could not parse reverse distance: " + distanceStr, e);
                }
            } else {
                _errorMessage.setValue("找不到從 " + startStation + " 到 " + endStation + " 的票價資訊。");
                Log.w(TAG, "No fare info found for: Start='" + startStation + "', End='" + endStation + "'");
            }
        }
        _isLoading.setValue(false);
    }
}