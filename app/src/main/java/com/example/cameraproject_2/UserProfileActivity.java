package com.example.cameraproject_2;

import static org.opencv.android.NativeCameraView.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserProfileActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private RegisterDatabaseHelper dbHelper;
    private ImageView profileImage;
    private EditText editTextUsername;
    private TextView textViewUserId;
    private Button buttonLogout;
    private Button buttonChangeProfile;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        dbHelper = new RegisterDatabaseHelper(this);

        // 綁定 UI 元素
        profileImage = findViewById(R.id.profile_image);
        editTextUsername = findViewById(R.id.edit_text_username);
        textViewUserId = findViewById(R.id.text_view_user_id);
        buttonLogout = findViewById(R.id.button_logout);
        buttonChangeProfile = findViewById(R.id.button_change_profile);

        // 設置圖片選擇啟動器
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            String userId = sharedPreferences.getString("userId", null);
                            if (userId != null && !userId.equals("訪客")) {
                                uploadProfileImage(selectedImageUri, userId);
                            } else {
                                Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        // 從 Intent 或 SharedPreferences 獲取狀態
        Intent intent = getIntent();
        boolean isLoggedIn = intent.getBooleanExtra("isLoggedIn", sharedPreferences.getBoolean("isLoggedIn", false));
        String loggedInUser = intent.getStringExtra("username");
        String userId = intent.getStringExtra("userId");
        final String profileImageUrl; // 聲明為 final
        if (loggedInUser == null) loggedInUser = sharedPreferences.getString("loggedInUser", "未知用戶");
        if (userId == null) userId = sharedPreferences.getString("userId", "未知 ID");
        if (intent.getStringExtra("profileImageUrl") != null) {
            profileImageUrl = intent.getStringExtra("profileImageUrl");
        } else {
            profileImageUrl = dbHelper.getProfileImageUrl(userId); // 從資料庫獲取
        }

        Log.d(TAG, "onCreate: userId=" + userId + ", profileImageUrl=" + profileImageUrl);

        // 根據狀態顯示內容
        if (isLoggedIn && !userId.equals("訪客")) {
            editTextUsername.setText(loggedInUser);
            editTextUsername.setEnabled(true);
            textViewUserId.setText("帳號: " + userId);

            // 載入頭像
            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                Picasso.get().load(profileImageUrl)
                        .error(R.drawable.user)
                        .placeholder(R.drawable.user)
                        .into(profileImage, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Profile image loaded successfully: " + profileImageUrl);
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "Failed to load profile image: " + e.getMessage());
                                profileImage.setImageResource(R.drawable.user);
                            }
                        });
            } else {
                profileImage.setImageResource(R.drawable.user);
                Log.d(TAG, "No profile image URL, using default");
            }

            buttonChangeProfile.setOnClickListener(v -> changeProfileImage());
            buttonLogout.setOnClickListener(v -> logout());
            editTextUsername.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !editTextUsername.getText().toString().isEmpty()) {
                    updateUsername(editTextUsername.getText().toString());
                }
            });
        } else {
            editTextUsername.setText("");
            textViewUserId.setText("帳號: 未登入");
            buttonChangeProfile.setVisibility(View.GONE);
            buttonLogout.setVisibility(View.GONE);
            profileImage.setImageResource(R.drawable.user);
            Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show();
            Intent personalIntent = new Intent(this, PersonalAccount.class);
            startActivity(personalIntent);
            finish();
        }
    }

    private void uploadProfileImage(Uri imageUri, String userId) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://13.239.232.58/android_studio/upload_profile_image.php";

        File file = new File(getRealPathFromURI(imageUri));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("userId", userId)
                .addFormDataPart("profile_image", file.getName(),
                        RequestBody.create(file, MediaType.parse("image/*")))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                Log.e(TAG, "Upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body().string();
                Log.d(TAG, "Upload response: " + responseData);

                // 檢查響應是否包含 JSON
                if (!responseData.trim().startsWith("{")) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "Server error: Invalid response format", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "Invalid JSON response: " + responseData);
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    if (jsonResponse.getBoolean("success")) {
                        String imageUrl = jsonResponse.getString("profile_image_url");
                        dbHelper.updateProfileImageUrl(userId, imageUrl);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("profileImageUrl", imageUrl);
                        editor.apply();
                        runOnUiThread(() -> {
                            Picasso.get().load(imageUrl).error(R.drawable.user).into(profileImage);
                            Toast.makeText(UserProfileActivity.this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        String message = jsonResponse.optString("message", "Unknown error");
                        runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "Upload failed: " + message, Toast.LENGTH_SHORT).show());
                        Log.e(TAG, "Upload failed: " + message);
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "JSON error: Invalid server response", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "JSON error: " + e.getMessage() + ", response: " + responseData);
                }
            }
        });
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void updateUsername(String newUsername) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("loggedInUser", newUsername);
        editor.apply();
        Toast.makeText(this, "姓名已更新為: " + newUsername, Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", false);
        editor.putString("loggedInUser", "訪客");
        editor.putString("userId", "訪客");
        editor.putString("profileImageUrl", null);
        editor.apply();
        Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, PersonalAccount.class);
        startActivity(intent);
        finish();
    }
}