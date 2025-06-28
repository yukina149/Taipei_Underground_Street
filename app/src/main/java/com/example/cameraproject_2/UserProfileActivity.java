package com.example.cameraproject_2;

import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_ID;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_IS_SYNCED;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_SYNC_ACTION;
import static com.example.cameraproject_2.RegisterDatabaseHelper.COL_USERNAME;
import static com.example.cameraproject_2.RegisterDatabaseHelper.TABLE_NAME;
import static org.opencv.android.NativeCameraView.TAG;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
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
    private Button buttonConfirmUsername;
    private Button buttonChangePassword;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private String originalUsername;
    private String userId;

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
        buttonConfirmUsername = findViewById(R.id.button_confirm_username);
        buttonChangePassword = findViewById(R.id.button_change_password);

        // 設置圖片選擇啟動器
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
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
        userId = intent.getStringExtra("userId");
        if (loggedInUser == null) loggedInUser = sharedPreferences.getString("loggedInUser", "未知用戶");
        if (userId == null) userId = sharedPreferences.getString("userId", "未知 ID");

        // 根據狀態顯示內容
        if (isLoggedIn && !userId.equals("訪客")) {
            originalUsername = loggedInUser;
            editTextUsername.setText(loggedInUser);
            editTextUsername.setEnabled(true);
            textViewUserId.setText("帳號: " + userId);

            // 載入頭像
            String profileImageUrl = intent.getStringExtra("profileImageUrl");
            if (profileImageUrl == null) {
                profileImageUrl = dbHelper.getProfileImageUrl(userId);
            }
            Log.d(TAG, "onCreate: userId=" + userId + ", profileImageUrl=" + profileImageUrl);

            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                Picasso.get().load(profileImageUrl)
                        .error(R.drawable.user)
                        .placeholder(R.drawable.user)
                        .into(profileImage, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                //Log.d(TAG, "Profile image loaded successfully: " + profileImageUrl);
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

            // 監聽 EditText 變化
            editTextUsername.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String newUsername = s.toString().trim();
                    buttonConfirmUsername.setVisibility(
                            !newUsername.isEmpty() && !newUsername.equals(originalUsername)
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }
            });

            buttonConfirmUsername.setOnClickListener(v -> {
                String newUsername = editTextUsername.getText().toString().trim();
                if (!newUsername.isEmpty()) {
                    updateUsername(newUsername, userId);
                    buttonConfirmUsername.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, "用戶名不能為空", Toast.LENGTH_SHORT).show();
                }
            });

            buttonChangeProfile.setOnClickListener(v -> changeProfileImage());

            buttonChangePassword.setOnClickListener(v -> showChangePasswordDialog());

            buttonLogout.setOnClickListener(v -> logout());
        } else {
            editTextUsername.setText("");
            textViewUserId.setText("帳號: 未登入");
            buttonChangeProfile.setVisibility(View.GONE);
            buttonLogout.setVisibility(View.GONE);
            buttonConfirmUsername.setVisibility(View.GONE);
            buttonChangePassword.setVisibility(View.GONE);
            profileImage.setImageResource(R.drawable.user);
            Toast.makeText(this, "請先登入", Toast.LENGTH_SHORT).show();
            Intent personalIntent = new Intent(this, PersonalAccount.class);
            startActivity(personalIntent);
            finish();
        }
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        EditText editTextCurrentPassword = dialogView.findViewById(R.id.edit_text_current_password);
        EditText editTextNewPassword = dialogView.findViewById(R.id.edit_text_new_password);
        EditText editTextConfirmNewPassword = dialogView.findViewById(R.id.edit_text_confirm_new_password);
        Button buttonCancel = dialogView.findViewById(R.id.button_cancel);
        Button buttonConfirm = dialogView.findViewById(R.id.button_confirm);

        AlertDialog dialog = builder.create();

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonConfirm.setOnClickListener(v -> {
            String currentPassword = editTextCurrentPassword.getText().toString().trim();
            String newPassword = editTextNewPassword.getText().toString().trim();
            String confirmNewPassword = editTextConfirmNewPassword.getText().toString().trim();

            if (currentPassword.isEmpty()) {
                editTextCurrentPassword.setError("請輸入當前密碼");
                editTextCurrentPassword.requestFocus();
                return;
            }

            if (newPassword.isEmpty()) {
                editTextNewPassword.setError("請輸入新密碼");
                editTextNewPassword.requestFocus();
                return;
            }

            if (newPassword.length() < 6) {
                editTextNewPassword.setError("新密碼長度至少需要 6 個字符");
                editTextNewPassword.requestFocus();
                return;
            }

            if (confirmNewPassword.isEmpty()) {
                editTextConfirmNewPassword.setError("請再次輸入新密碼");
                editTextConfirmNewPassword.requestFocus();
                return;
            }

            if (!newPassword.equals(confirmNewPassword)) {
                editTextConfirmNewPassword.setError("兩次輸入的密碼不一致");
                editTextConfirmNewPassword.requestFocus();
                return;
            }

            // 驗證當前密碼
            String username = sharedPreferences.getString("loggedInUser", null);
            if (username == null || !dbHelper.checkUser(username, currentPassword)) {
                editTextCurrentPassword.setError("當前密碼不正確");
                editTextCurrentPassword.requestFocus();
                return;
            }

            // 更新密碼
            updatePassword(userId, newPassword, dialog);
        });

        dialog.show();
    }

    private void updatePassword(String userId, String newPassword, AlertDialog dialog) {
        String originalPassword = dbHelper.getCurrentPassword(userId);
        boolean success = dbHelper.updatePassword(userId, newPassword);
        if (success) {
            Toast.makeText(this, "密碼已更新", Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            new Thread(() -> {
                try {
                    dbHelper.uploadUnsyncedUsers(userId);
                    runOnUiThread(() -> Toast.makeText(this, "密碼同步成功", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Password sync failed: " + e.getMessage());
                    dbHelper.updatePassword(userId, originalPassword); // 回滾
                    runOnUiThread(() -> Toast.makeText(this, "密碼同步失敗，已回滾: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } else {
            Toast.makeText(this, "更新密碼失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadProfileImage(Uri imageUri, final String userId) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://13.239.232.58/android_studio/upload_profile_image.php";

        File file = new File(getRealPathFromURI(imageUri));
        Log.d(TAG, "Uploading image for userId: " + userId + ", file path: " + file.getAbsolutePath() + ", file exists: " + file.exists() + ", file size: " + file.length());

        if (!file.exists() || file.length() == 0) {
            runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "無效的圖片文件", Toast.LENGTH_SHORT).show());
            Log.e(TAG, "Invalid image file: exists=" + file.exists() + ", size=" + file.length());
            return;
        }

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
                runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "上傳失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                Log.e(TAG, "Upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body().string();
                Log.d(TAG, "Upload response: " + responseData);

                if (!responseData.trim().startsWith("{")) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "伺服器錯誤: 無效的回應格式", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "Invalid JSON response: " + responseData);
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    if (jsonResponse.getBoolean("success")) {
                        String imageUrl = jsonResponse.getString("profile_image_url");
                        Log.d(TAG, "Image uploaded, URL: " + imageUrl);
                        dbHelper.updateProfileImageUrl(userId, imageUrl);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("profileImageUrl", imageUrl);
                        editor.apply();
                        runOnUiThread(() -> {
                            Picasso.get().load(imageUrl)
                                    .error(R.drawable.user)
                                    .placeholder(R.drawable.user)
                                    .into(profileImage, new com.squareup.picasso.Callback() {
                                        @Override
                                        public void onSuccess() {
                                            Log.d(TAG, "Profile image loaded successfully: " + imageUrl);
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.e(TAG, "Failed to load profile image: " + e.getMessage());
                                            profileImage.setImageResource(R.drawable.user);
                                        }
                                    });
                            Toast.makeText(UserProfileActivity.this, "圖片上傳成功", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        String message = jsonResponse.optString("message", "未知錯誤");
                        runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "上傳失敗: " + message, Toast.LENGTH_SHORT).show());
                        Log.e(TAG, "Upload failed: " + message);
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "JSON 錯誤: 無效的伺服器回應", Toast.LENGTH_SHORT).show());
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

    private void updateUsername(String newUsername, String userId) {
        String tempOriginalUsername = originalUsername;
        boolean success = dbHelper.updateUsername(userId, newUsername);
        if (success) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("loggedInUser", newUsername);
            editor.apply();
            originalUsername = newUsername;
            runOnUiThread(() -> Toast.makeText(this, "姓名已更新為: " + newUsername, Toast.LENGTH_SHORT).show());

            new Thread(() -> {
                try {
                    dbHelper.clearUnsyncedUsersExcept(userId);
                    dbHelper.logUnsyncedUsers();
                    dbHelper.uploadUnsyncedUsers(userId);
                    runOnUiThread(() -> Toast.makeText(this, "用戶數據同步成功", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Sync failed: " + e.getMessage());
                    dbHelper.updateUsername(userId, tempOriginalUsername);
                    runOnUiThread(() -> Toast.makeText(this, "同步失敗，已回滾用戶名: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } else {
            runOnUiThread(() -> Toast.makeText(this, "更新姓名失敗", Toast.LENGTH_SHORT).show());
        }
    }

    public void logAllUsers() {
        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
            String syncAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_ACTION));
            int isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SYNCED));
            Log.d(TAG, "User: id=" + id + ", username=" + username + ", syncAction=" + syncAction + ", isSynced=" + isSynced);
        }
        cursor.close();
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