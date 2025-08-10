package com.example.cameraproject_2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class ExitMapActivity extends AppCompatActivity {

    private static final String TAG = "ExitMapActivity";
    private WebView webView;
    // private LocationManager locationManager; // 暫時註解，因為 HTML 使用 navigator.geolocation
    // private LocationListener locationListener;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101; // Android 原生位置權限請求碼
    // GEOLOCATION_PERMISSION_REQUEST_CODE 用於 WebChromeClient 回調，但我們直接在 onGeolocationPermissionsShowPrompt 中處理

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        setContentView(R.layout.activity_exit_map);

        webView = findViewById(R.id.map_webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true); // 有時定位提示需要
        webSettings.setGeolocationEnabled(true);    // 啟用 WebView 內建的地理位置 API 支持

        // *** 關鍵修改：解決 file:/// CORS 問題 ***
        webSettings.setAllowFileAccess(true);       // 基礎的文件訪問權限 (你已經有了)
        // 為了允許 XHR/fetch 從 file:/// 載入的 HTML 訪問其他 file:/// 資源 (如本地 JSON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) { // API 16+
            webSettings.setAllowFileAccessFromFileURLs(true);     // 允許 JS 透過 file:// URL 訪問其他 file:// URL
            webSettings.setAllowUniversalAccessFromFileURLs(true); // 允許 JS 透過 file:// URL 訪問任何源的內容 (包括 file://)
        }
        // *****************************************

        // (可選) 添加 JavaScript 橋接，如果你的 HTML 需要從 JS 呼叫 Java 方法
        // webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
            }

            // 如果你需要攔截請求或處理錯誤，可以在這裡添加更多方法
            // 例如 onReceivedError
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                Log.d(TAG, "WebView requesting geolocation permission for origin: " + origin);
                if (ContextCompat.checkSelfPermission(ExitMapActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(ExitMapActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "App has location permission. Granting to WebView.");
                    callback.invoke(origin, true, false);
                } else {
                    Log.d(TAG, "App does NOT have location permission. Requesting from user.");
                    ActivityCompat.requestPermissions(ExitMapActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                    // 樂觀授予，依賴 Android 權限對話框。
                    // 如果用戶拒絕 Android 權限，HTML 中的 navigator.geolocation 最終還是會失敗。
                    // 這裡的邏輯是，先讓 WebView 的請求通過，Android 的權限請求會彈出。
                    callback.invoke(origin, true, false);
                }
            }

            // 如果你的 HTML 使用了 alert(), confirm(), prompt()，你可能需要覆寫這些方法
            // 例如: @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) { ... }
        });

        // 載入你 assets 資料夾下的 HTML 檔案
        webView.loadUrl("file:///android_asset/leaflet/exit_map.html");

        checkAndRequestLocationPermission();
    }


    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Location permission already granted for the App.");
            // 如果權限已授予，可以做一些初始化操作，或者讓 WebView 自行處理
        }
    }


    // (可選) JavaScript 橋接類
    // public class WebAppInterface {
    //     Context mContext;
    //     WebAppInterface(Context c) {
    //         mContext = c;
    //     }
    //     @JavascriptInterface
    //     public void someJavaMethod(String data) {
    //         Toast.makeText(mContext, "Called from JS: " + data, Toast.LENGTH_SHORT).show();
    //     }
    // }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_GRANTED || (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED))) {
                Log.d(TAG, "Android Location permission granted by user.");
                // 權限已授予，WebView 中的 navigator.geolocation 應該可以工作了
                // 如果之前 onGeolocationPermissionsShowPrompt 中保存了 callback，可以在這裡調用它
                // 或者，你可以讓 WebView 重新載入或讓 JS 重新嘗試定位
                Toast.makeText(this, "位置權限已獲取", Toast.LENGTH_SHORT).show();
                // 讓JS重新嘗試定位
                if (webView != null) {
                    webView.evaluateJavascript("javascript:if(typeof goToCurrentLocation === 'function'){goToCurrentLocation(true);} else { console.error('goToCurrentLocation function not found'); }", null);
                }

            } else {
                Log.w(TAG, "Android Location permission denied by user.");
                Toast.makeText(this, "位置權限被拒絕，定位功能可能無法使用。", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 如果你依賴原生的 LocationManager，則保留 onPause 和 onResume
    // @Override
    // protected void onPause() {
    //     super.onPause();
    //     // if (locationManager != null && locationListener != null) {
    //     //     locationManager.removeUpdates(locationListener);
    //     //     Log.d(TAG, "Native location updates paused");
    //     // }
    // }

    // @Override
    // protected void onResume() {
    //     super.onResume();
    //     // if (checkLocationPermission() && locationManager != null && locationListener != null) {
    //     // try {
    //     // locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
    //     // } catch (SecurityException e) {Log.e(TAG, "Sec ex onResume", e);}
    //     // }
    // }

    // 返回鍵處理，使 WebView 可以回退網頁歷史
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
