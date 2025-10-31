package com.example.cameraproject_2.ui;

import android.app.Application; // 需要 Application Context
import android.content.Context; // 需要 Context
import android.content.SharedPreferences; // 需要 SharedPreferences
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel; // 改為 AndroidViewModel 以獲取 Application Context
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
// import androidx.lifecycle.ViewModel; // 不再使用 ViewModel

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService; // 用於後台執行緒
import java.util.concurrent.Executors;   // 用於後台執行緒

import com.example.cameraproject_2.model.AppDatabase; // 引入資料庫
import com.example.cameraproject_2.model.FareEntryDao; // 引入 DAO
import com.example.cameraproject_2.model.FareEntry;
import com.example.cameraproject_2.model.MetroApiResponse;
import com.example.cameraproject_2.model.MetroApiResult;
import com.example.cameraproject_2.network.TaipeiMetroApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FareQueryViewModel extends AndroidViewModel { // *** 改為 AndroidViewModel ***

    private static final String TAG = "FareQueryVM";
    private static final String PREFS_NAME = "FarePrefs";
    private static final String KEY_LAST_UPDATE_TIMESTAMP = "lastUpdateTimestamp";
    private static final long CACHE_EXPIRY_DURATION_MS = 24 * 60 * 60 * 1000; // 24 小時

    // LiveData 保持不變
    private final MutableLiveData<List<String>> _stationList = new MutableLiveData<>();
    public final LiveData<List<String>> stationList = _stationList;
    private final MutableLiveData<String> _fullFareResult = new MutableLiveData<>();
    public final LiveData<String> fullFareResult = _fullFareResult;
    private final MutableLiveData<String> _concessionFareResult = new MutableLiveData<>();
    public final LiveData<String> concessionFareResult = _concessionFareResult;
    private final MutableLiveData<String> _taipeiChildFareResult = new MutableLiveData<>();
    public final LiveData<String> taipeiChildFareResult = _taipeiChildFareResult;
    private final MutableLiveData<String> _distanceResult = new MutableLiveData<>();
    public final LiveData<String> distanceResult = _distanceResult;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private List<FareEntry> allFareEntries = new ArrayList<>(); // 仍然用於內存中的當前資料
    private final TaipeiMetroApiService apiService;
    private final FareEntryDao fareEntryDao; // 資料庫 DAO
    private final SharedPreferences sharedPreferences; // 用於儲存時間戳
    private final ExecutorService databaseExecutor; // 用於在後台執行緒操作資料庫

    private static final int API_LIMIT_PER_REQUEST = 1000;
    private int currentOffset = 0;
    private boolean isLoadingAllPagesFromApi = false; // 與 isLoading 不同，這個專指 API 分頁載入

    // *** 構造函數修改 ***
    public FareQueryViewModel(Application application) {
        super(application); // 傳遞 Application 給 AndroidViewModel

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://data.taipei/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(TaipeiMetroApiService.class);

        // 初始化資料庫和 DAO
        AppDatabase database = AppDatabase.getDatabase(application);
        fareEntryDao = database.fareEntryDao();
        // 初始化 SharedPreferences
        sharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 初始化用於資料庫操作的執行緒池
        databaseExecutor = Executors.newSingleThreadExecutor();

        loadFareData(); // 啟動時載入資料 (會先檢查快取)
    }

    private void loadFareData() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        databaseExecutor.execute(() -> { // 在後台執行緒中操作資料庫
            long lastUpdateTime = sharedPreferences.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0);
            boolean isCacheExpired = (System.currentTimeMillis() - lastUpdateTime) > CACHE_EXPIRY_DURATION_MS;
            List<FareEntry> cachedEntries = null;

            if (!isCacheExpired) {
                Log.d(TAG, "Cache not expired. Trying to load from database...");
                cachedEntries = fareEntryDao.getAllFareEntries();
            } else {
                Log.d(TAG, "Cache expired or first load.");
            }

            if (cachedEntries != null && !cachedEntries.isEmpty()) {
                Log.d(TAG, "Loaded " + cachedEntries.size() + " entries from database cache.");
                allFareEntries.clear();
                allFareEntries.addAll(cachedEntries);
                ContextCompat.getMainExecutor(getApplication()).execute(this::processLoadedData);
            } else {
                Log.d(TAG, "No valid cache found or cache expired. Fetching from API.");
                // 需要在主執行緒中觸發 API 請求的 loading 狀態 (如果 fetchAllFareDataPages 內部處理了就不用)
                // getApplication().getMainExecutor().execute(() -> _isLoading.setValue(true));
                fetchAllFareDataPagesFromApi(); // 從 API 獲取
            }
        });
    }


    private void fetchAllFareDataPagesFromApi() {
        if (isLoadingAllPagesFromApi) {
            return;
        }
        // _isLoading.setValue(true); // 這個應該在 loadFareData 開始時設定，或者在這裡再次確認
        allFareEntries.clear();
        currentOffset = 0;
        isLoadingAllPagesFromApi = true;
        Log.d(TAG, "Starting to fetch all fare data pages from API...");
        // 確保 fetchNextPageFromApi 是在主執行緒發起 Retrofit 請求 (Retrofit callback 會切回主執行緒)
        ContextCompat.getMainExecutor(getApplication()).execute(this::fetchNextPageFromApi);
    }

    private void fetchNextPageFromApi() {
        // isLoading 狀態應由 loadFareData 或 fetchAllFareDataPagesFromApi 開始時設定
        // 在這裡主要是網路請求本身
        Log.d(TAG, "Fetching page from API with offset: " + currentOffset + ", limit: " + API_LIMIT_PER_REQUEST);
        apiService.getMetroFares(API_LIMIT_PER_REQUEST, currentOffset).enqueue(new Callback<MetroApiResponse>() {
            @Override
            public void onResponse(Call<MetroApiResponse> call, Response<MetroApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    MetroApiResult result = response.body().getResult();
                    List<FareEntry> currentPageEntries = result.getFareEntries();

                    if (currentPageEntries != null && !currentPageEntries.isEmpty()) {
                        allFareEntries.addAll(currentPageEntries); // 先累加到內存列表
                        int totalCount = result.getCount();
                        if (allFareEntries.size() < totalCount && currentPageEntries.size() == API_LIMIT_PER_REQUEST) {
                            currentOffset += API_LIMIT_PER_REQUEST;
                            fetchNextPageFromApi(); // 遞迴獲取下一頁
                        } else {
                            // 所有資料都已獲取完畢
                            Log.d(TAG, "All pages fetched from API. Total entries: " + allFareEntries.size());
                            // 將獲取的資料存入資料庫並更新時間戳
                            saveFareDataToDatabase(new ArrayList<>(allFareEntries)); // 傳遞副本以防修改
                            processLoadedData(); // 然後處理載入的資料 (更新 UI 等)
                        }
                    } else {
                        Log.d(TAG, "API: Current page has no entries or API returned empty list. Assuming all fetched.");
                        if (!allFareEntries.isEmpty()) { // 如果之前頁面有數據
                            saveFareDataToDatabase(new ArrayList<>(allFareEntries));
                        }
                        processLoadedData();
                    }
                } else {
                    // API 錯誤處理
                    isLoadingAllPagesFromApi = false;
                    // _isLoading.setValue(false); // 應該在 processLoadedData 或這裡統一處理
                    Log.e(TAG, "API Error Response. Code: " + response.code() + ", Message: " + response.message());
                    String errorMsg = "無法載入部分票價資料 (錯誤碼：" + response.code() + ")";
                    try {
                        if (response.errorBody() != null) errorMsg += " " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    _errorMessage.postValue(errorMsg); // 使用 postValue 因為可能在背景執行緒 (雖然 Retrofit callback 在主執行緒)

                    // 即使 API 失敗，如果之前從快取或其他頁面載入了資料，也嘗試處理
                    if (!allFareEntries.isEmpty()) {
                        Log.w(TAG, "Processing potentially partial data due to API error.");
                        processLoadedData(); // 這裡會把 isLoading 設為 false
                    } else {
                        // 確保 isLoading 被設為 false，並且 stationList 為空
                        _isLoading.postValue(false);
                        _stationList.postValue(new ArrayList<>());
                    }
                }
            }

            @Override
            public void onFailure(Call<MetroApiResponse> call, Throwable t) {
                // 網路錯誤處理
                isLoadingAllPagesFromApi = false;
                // _isLoading.setValue(false);
                Log.e(TAG, "Network Error during paged API fetch", t);
                _errorMessage.postValue("網路連線失敗，請稍後再試。");

                if (!allFareEntries.isEmpty()) {
                    Log.w(TAG, "Processing potentially partial data due to network error.");
                    processLoadedData();
                } else {
                    _isLoading.postValue(false);
                    _stationList.postValue(new ArrayList<>());
                }
            }
        });
    }

    private void saveFareDataToDatabase(final List<FareEntry> entriesToSave) {
        databaseExecutor.execute(() -> { // 在後台執行緒中操作資料庫
            try {
                fareEntryDao.deleteAll(); // 清空舊資料
                fareEntryDao.insertAll(entriesToSave); // 插入新資料
                // 更新時間戳
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis());
                editor.apply();
                Log.d(TAG, "Saved " + entriesToSave.size() + " entries to database and updated timestamp.");
            } catch (Exception e) {
                Log.e(TAG, "Error saving data to database", e);
                // 可以考慮在這裡設定一個錯誤訊息
            }
        });
    }


    private void processLoadedData() { // 這個方法應該在主執行緒被呼叫，因為它更新 LiveData
        isLoadingAllPagesFromApi = false; // API 分頁載入結束 (無論成功與否)
        _isLoading.setValue(false); // 整體載入過程結束

        if (allFareEntries.isEmpty()) {
            Log.w(TAG, "No fare entries to process.");
            if (_errorMessage.getValue() == null || _errorMessage.getValue().isEmpty()) {
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
        _stationList.setValue(sortedStations); // 更新 LiveData 會觸發 UI 更新
        Log.d(TAG, "Station list updated with " + sortedStations.size() + " unique stations from processed data.");
    }


    // queryFare 方法基本不變，但要確保它使用的是內存中的 allFareEntries
    public void queryFare(String startStation, String endStation) {
        // 檢查 isLoadingAllPagesFromApi 而不是 isLoading，因為 isLoading 可能因快取載入而很快變 false
        if (isLoadingAllPagesFromApi) {
            _errorMessage.setValue("票價資料仍在從網路載入中，請稍候...");
            return;
        }
        if (_isLoading.getValue() != null && _isLoading.getValue()) {
            // 如果 _isLoading 仍然是 true (例如，剛從快取載入完，正在 processLoadedData)
            _errorMessage.setValue("票價資料準備中，請稍候...");
            return;
        }


        _isLoading.setValue(true); // 查詢過程也顯示 loading
        _fullFareResult.setValue(null);
        _concessionFareResult.setValue(null);
        _taipeiChildFareResult.setValue(null);
        _distanceResult.setValue(null);
        _errorMessage.setValue(null);

        if (startStation == null || startStation.isEmpty() || endStation == null || endStation.isEmpty()) {
            _errorMessage.setValue("請選擇起點和終點站。");
            _isLoading.setValue(false);
            return;
        }

        // 由於資料庫操作在後台，這裡直接查詢內存中的 allFareEntries
        // allFareEntries 應該在 loadFareData 或 fetchAllFareDataPagesFromApi 後被填充
        FareEntry foundEntry = null;
        if (!allFareEntries.isEmpty()) {
            for (FareEntry entry : allFareEntries) {
                if (startStation.equals(entry.getFromStation()) && endStation.equals(entry.getToStation())) {
                    foundEntry = entry;
                    break;
                }
            }
            if (foundEntry == null) { // 嘗試反向
                for (FareEntry entry : allFareEntries) {
                    if (endStation.equals(entry.getFromStation()) && startStation.equals(entry.getToStation())) {
                        foundEntry = entry;
                        break;
                    }
                }
            }
        }

        if (foundEntry != null) {
            _fullFareResult.setValue("全票票價：NT$ " + foundEntry.getFullFare());
            _concessionFareResult.setValue("敬老愛心/兒童：NT$ " + foundEntry.getConcessionFare());
            //廢棄 _taipeiChildFareResult.setValue("兒童(北市)：NT$ " + foundEntry.getTaipeiChildFare());
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
            }
        } else {
            _errorMessage.setValue("找不到從 " + startStation + " 到 " + endStation + " 的票價資訊。");
        }
        _isLoading.setValue(false);
    }

    // 當 ViewModel 被銷毀時，可以關閉 ExecutorService
    @Override
    protected void onCleared() {
        super.onCleared();
        databaseExecutor.shutdown();
    }
}

