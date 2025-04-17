package com.example.cameraproject_2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.ar.core.exceptions.DeadlineExceededException;

public class ARNavigationActivity extends AppCompatActivity{

    private ArSceneView arSceneView;
    private TextView descriptionTextView;
    private Button startArNavigationButton;
    private Button startOrbDetectionButton;
    private ImageView processedImageView;

    private Session arSession;
    private boolean installRequested;

    private List<LocationData> locationDataList = new ArrayList<>();
    private DatabaseHelper dbHelper;
    private SQLiteDatabase database;

    private TextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private Mat descriptors = new Mat();
    private MatOfKeyPoint keypoints;
    private boolean shouldCaptureFeatures = false;
    private boolean processingFrame = false;
    private String selectedDestination; // 新增變數儲存目的地


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arnavigation);

        arSceneView = findViewById(R.id.ar_scene_view);
        startArNavigationButton = findViewById(R.id.start_ar_navigation_button);
        startOrbDetectionButton = findViewById(R.id.start_orb_detection_button);
        descriptionTextView = findViewById(R.id.description_text_view);
        processedImageView = findViewById(R.id.processed_image_view);
        textureView = findViewById(R.id.texture_view);

        dbHelper = new DatabaseHelper(this);

        // 從 Intent 中接收目的地
        Intent intent = getIntent();
        if (intent != null) {
            Log.d("ARNavigationActivity", "Intent received: " + intent.toString());
            if (intent.hasExtra("destination")) {
                selectedDestination = intent.getStringExtra("destination");
                Log.d("ARNavigationActivity", "Received destination: " + selectedDestination);
            } else {
                selectedDestination = "未選擇目的地";
                Log.d("ARNavigationActivity", "No 'destination' extra found in Intent, using default: " + selectedDestination);
            }
        } else {
            selectedDestination = "未選擇目的地";
            Log.d("ARNavigationActivity", "Intent is null, using default: " + selectedDestination);
        }

        // 後續初始化邏輯保持不變
        if (!hasCameraPermission(this)) {
            requestCameraPermission();
            return;
        }

        if (OpenCVLoader.initDebug()) {
            Log.i("ARNavigationActivity", "OpenCV initialized successfully");
        } else {
            Log.e("ARNavigationActivity", "Failed to initialize OpenCV");
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            checkARCoreAvailability();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        initARSession();
        setupCameraParameters();

        try {
            dbHelper.createDataBase();
            database = dbHelper.openDataBase();
            loadLocationDataFromDatabase();
        } catch (IOException e) {
            Log.e("ARNavigationActivity", "Error creating database: " + e.getMessage());
        }

        setupButtonListeners();
        textureView.setSurfaceTextureListener(textureListener);
    }
    private void initARSession() {
        if (arSession == null) {
            try {
                arSession = new Session(this);
                CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(arSession);
                cameraConfigFilter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
                cameraConfigFilter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE));

                List<CameraConfig> cameraConfigs = arSession.getSupportedCameraConfigs(cameraConfigFilter);

                if (!cameraConfigs.isEmpty()) {
                    CameraConfig selectedConfig = cameraConfigs.get(0);
                    Config sessionConfig = new Config(arSession);
                    sessionConfig.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                    sessionConfig.setFocusMode(Config.FocusMode.AUTO);

                    arSession.configure(sessionConfig);
                    arSceneView.setupSession(arSession);
                } else {
                    Config config = new Config(arSession);
                    config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                    config.setFocusMode(Config.FocusMode.AUTO);
                    arSession.configure(config);
                    arSceneView.setupSession(arSession);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to create AR session: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupCameraParameters() {
        Scene scene = arSceneView.getScene();
        scene.getCamera().setNearClipPlane(0.1f);
        scene.getCamera().setFarClipPlane(100.0f);
    }

    private void setupButtonListeners() {
        startOrbDetectionButton.setOnClickListener(v -> {
            if (arSession != null) {
                arSession.pause();
                Log.d("ARNavigationActivity", "AR session paused for ORB");
            }
            arSceneView.pause();
            arSceneView.setVisibility(View.GONE);
            textureView.setVisibility(View.VISIBLE);
            processedImageView.setVisibility(View.VISIBLE);
            processedImageView.bringToFront();
            startCamera();
            // 延遲設置 shouldCaptureFeatures，確保相機準備好
            new Handler().postDelayed(() -> {
                shouldCaptureFeatures = true;
                processingFrame = false;
                Toast.makeText(this, "ORB 檢測開始", Toast.LENGTH_SHORT).show();
                startOrbDetectionButton.setText("重新檢測ORB");
            }, 500); // 延遲 500ms
        });

        startArNavigationButton.setOnClickListener(v -> {
            processedImageView.setVisibility(View.GONE);
            textureView.setVisibility(View.GONE);
            arSceneView.setVisibility(View.VISIBLE);
            stopCamera();
            if (arSession != null) {
                try {
                    arSession.resume();
                    arSceneView.resume();
                    Log.d("ARNavigationActivity", "AR session resumed");
                } catch (CameraNotAvailableException e) {
                    Log.e("ARNavigationActivity", "Camera not available: " + e.getMessage());
                }
            }
            startOrbDetectionButton.setText("開始ORB檢測");
        });
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            if (!processingFrame) {
                Bitmap bitmap = textureView.getBitmap();
                if (bitmap == null) {
                    Log.e("ARNavigationActivity", "Bitmap from TextureView is null");
                    return;
                }
                Log.d("ARNavigationActivity", "Bitmap captured: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                Mat frame = new Mat();
                Utils.bitmapToMat(bitmap, frame);

                if (shouldCaptureFeatures) {
                    processingFrame = true;
                    Mat frameCopy = frame.clone();
                    new Thread(() -> {
                        Mat processedFrame = processFeatures(frameCopy);
                        if (processedFrame != null && !processedFrame.empty()) {
                            String location = compareFeaturesWithDatabase(keypoints, descriptors);
                            if (location != null) {
                                runOnUiThread(() -> {
                                    String navigationMessage;
                                    if (location.equals(selectedDestination)) {
                                        navigationMessage = "您已到達: " + location;
                                    } else {
                                        navigationMessage = "目前位置: " + location + "，前往: " + selectedDestination;
                                    }
                                    descriptionTextView.setText(navigationMessage);
                                    Log.d("ARNavigationActivity", "Navigation message updated: " + navigationMessage);
                                });
                            } else {
                                Log.w("ARNavigationActivity", "No valid location found");
                            }
                        }
                        processedFrame.release();
                        frameCopy.release();
                        processingFrame = false;
                        shouldCaptureFeatures = false;
                    }).start();
                }
                frame.release();
            }
        }
    };

    private void startCamera() {
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    private void openCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w("ARNavigationActivity", "Camera permission not granted");
                requestCameraPermission();
                return;
            }
            String cameraId = manager.getCameraIdList()[0]; // Back camera
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e("ARNavigationActivity", "Cannot access camera: " + e.getMessage());
        } catch (SecurityException e) {
            Log.e("ARNavigationActivity", "Security exception: " + e.getMessage());
            requestCameraPermission();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            Log.e("ARNavigationActivity", "Camera error: " + error);
        }
    };

    private void stopCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                        Log.d("ARNavigationActivity", "Camera preview session configured");
                    } catch (CameraAccessException e) {
                        Log.e("ARNavigationActivity", "Capture session error: " + e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("ARNavigationActivity", "Capture session configuration failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e("ARNavigationActivity", "Create preview session error: " + e.getMessage());
        }
    }

    private void checkARCoreAvailability() throws UnavailableDeviceNotCompatibleException, UnavailableUserDeclinedInstallationException {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
            case INSTALL_REQUESTED:
                installRequested = true;
                return;
            case INSTALLED:
                break;
        }

        // ARCore requires camera permissions to operate.
        if (!hasCameraPermission(this)) {
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
    }


    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (arSession != null) {
            try {
                arSession.resume();
                arSceneView.resume();
                arSceneView.setVisibility(View.VISIBLE);
                textureView.setVisibility(View.GONE);
                processedImageView.setVisibility(View.GONE);
            } catch (CameraNotAvailableException e) {
                Log.e("ARNavigationActivity", "Camera not available: " + e.getMessage());
                arSession = null;
            }
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        stopBackgroundThread();
        if (arSession != null) {
            arSceneView.pause();
            arSession.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (arSession != null) {
            arSession.close();
        }
        arSceneView.destroy();
        super.onDestroy();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e("ARNavigationActivity", "Background thread interrupted: " + e.getMessage());
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (hasCameraPermission(this)) {
            //initializeOpenCVAndCamera();
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            finish();
        }
    }

     private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    //關於orb
    private void stopARSession() {
        if (arSession != null) {
            try {
                arSession.pause();
            } catch (Exception e) {
                Log.e("ARNavigationActivity", "Error pausing AR session: " + e.getMessage());
            }
        }

        if (arSceneView != null) {
            arSceneView.pause();
        }

        if (startArNavigationButton != null) {
            startArNavigationButton.setEnabled(false);
        }
    }



    private void captureFeatures() {

        shouldCaptureFeatures = true;
        Toast.makeText(this, "Capturing features on next frame...", Toast.LENGTH_SHORT).show();
    }

    // 增加一個標誌變量

    //顯示特徵點用
    /*
    private void displayProcessedImage(Mat processedFrame) {
        if (processedFrame == null || processedFrame.empty()) {
            Log.e("ARNavigationActivity", "Processed frame is null or empty");
            return;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(processedFrame.cols(), processedFrame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(processedFrame, bitmap);
            processedImageView.setImageBitmap(bitmap);
            processedImageView.setVisibility(View.VISIBLE);
            processedImageView.invalidate(); // Force redraw
            Log.d("ARNavigationActivity", "Image displayed with size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        } catch (Exception e) {
            Log.e("ARNavigationActivity", "Error converting Mat to Bitmap: " + e.getMessage());
        }
    }

     */
        /*

        try {
            // Convert Mat to Bitmap
            Bitmap bitmap = Bitmap.createBitmap(processedFrame.cols(), processedFrame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(processedFrame, bitmap);

            // Get reference to ImageView
            ImageView processedImageView = findViewById(R.id.processed_image_view);
            if (processedImageView != null) {
                // Set bitmap to ImageView
                processedImageView.setImageBitmap(bitmap);
                processedImageView.setVisibility(View.VISIBLE);

                // Optionally hide the camera view once processing is done
                // javaCameraView.setVisibility(View.INVISIBLE);
            } else {
                Log.e("ARNavigationActivity", "processed_image_view is null");
            }
        } catch (Exception e) {
            Log.e("ARNavigationActivity", "Error displaying processed image: " + e.getMessage());
        }
    }

         */


    // 新增一個處理特徵的方法
    //印出檢測到的關鍵點數量和描述符的大小，以確保是否正確檢測到了足夠的特徵點。
    // 在 processFeatures 方法中添加繪製特徵點的代碼
    private Mat processFeatures(Mat frame) {
        if (frame == null || frame.empty()) {
            Log.e("ARNavigationActivity", "Empty frame in processFeatures");
            return frame != null ? frame.clone() : new Mat();
        }

        Mat grayFrame = new Mat();
        Mat outputFrame = frame.clone();

        try {
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            ORB orb = ORB.create();
            orb.setFastThreshold(20);
            orb.setScaleFactor(1.2f);

            keypoints = new MatOfKeyPoint();
            descriptors = new Mat();
            orb.detectAndCompute(grayFrame, new Mat(), keypoints, descriptors);

            int keypointCount = keypoints.toArray().length;
            Log.d("ARNavigationActivity", "Detected " + keypointCount + " keypoints, Descriptors: " + descriptors.rows() + "x" + descriptors.cols());

            if (keypointCount == 0) {
                Log.w("ARNavigationActivity", "No keypoints detected");
            }
        } catch (Exception e) {
            Log.e("ARNavigationActivity", "Error in processFeatures: " + e.getMessage());
        } finally {
            grayFrame.release();
        }

        return outputFrame;
    }




    // 顯示圖像的方法

    private Mat getFrameFromCameraPreview() {
        // 這裡需要實現從相機預覽中獲取圖像的邏輯
        // 可能需要使用 Camera2 API 或其他相機 API 來實現
        // 暫時返回 null 作為占位符
        return new Mat(); // 返回一個空的 Mat 以避免 NullPointerException

    }

    private String compareFeaturesWithDatabase(MatOfKeyPoint keypoints, Mat descriptors) {
        String bestMatchLocation = "Unknown";
        int maxMatches = 0;

        if (locationDataList.isEmpty()) {
            Log.w("ARNavigationActivity", "Location data list is empty");
            return "No location data available";
        }

        for (LocationData locationData : locationDataList) {
            String imageFileName = locationData.getImageFileName();
            Bitmap bitmap = getBitmapFromAsset(imageFileName);

            if (bitmap == null) {
                Log.w("ARNavigationActivity", "Could not load bitmap for: " + imageFileName);
                continue;
            }

            Mat databaseImage = new Mat();
            Utils.bitmapToMat(bitmap, databaseImage);
            Imgproc.cvtColor(databaseImage, databaseImage, Imgproc.COLOR_BGR2GRAY);

            MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
            Mat descriptors2 = new Mat();
            ORB orb = ORB.create();
            orb.detectAndCompute(databaseImage, new Mat(), keypoints2, descriptors2);

            if (descriptors.empty() || descriptors2.empty() || descriptors.rows() == 0 || descriptors2.rows() == 0) {
                Log.w("ARNavigationActivity", "Empty descriptors for: " + imageFileName);
                databaseImage.release();
                descriptors2.release();
                continue;
            }

            MatOfDMatch matches = new MatOfDMatch();
            try {
                DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                matcher.match(descriptors, descriptors2, matches);

                List<DMatch> listOfMatches = matches.toList();
                List<DMatch> goodMatches = new ArrayList<>();

                double distanceThreshold = 50.0;
                double maxDist = 0;
                double minDist = 100;

                for (DMatch match : listOfMatches) {
                    double dist = match.distance;
                    if (dist < minDist) minDist = dist;
                    if (dist > maxDist) maxDist = dist;
                }

                for (DMatch match : listOfMatches) {
                    if (match.distance <= Math.max(2 * minDist, 0.02) && match.distance < distanceThreshold) {
                        goodMatches.add(match);
                    }
                }

                int numMatches = goodMatches.size();
                Log.d("ARNavigationActivity", "Location " + locationData.getLocationName() +
                        " has " + numMatches + " good matches out of " + listOfMatches.size() + " total matches");

                if (numMatches > maxMatches) {
                    maxMatches = numMatches;
                    bestMatchLocation = locationData.getLocationName();
                }
            } catch (Exception e) {
                Log.e("ARNavigationActivity", "Error matching descriptors: " + e.getMessage());
            }

            databaseImage.release();
            descriptors2.release();
            matches.release();
        }

        Log.d("ARNavigationActivity", "Best match location: " + bestMatchLocation + " with " + maxMatches + " matches");
        return bestMatchLocation;
    }


    private void displayLocationData(final String location) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (descriptionTextView != null) {
                    descriptionTextView.setText("Location: " + location);
                    Intent intent = new Intent();
                    intent.putExtra("location", location);
                    setResult(RESULT_OK, intent);


                } else {
                    Log.e("ARNavigationActivity", "descriptionTextView is null");
                    Toast.makeText(ARNavigationActivity.this,
                            "Location: " + location,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    private Bitmap getBitmapFromAsset(String fileName) {
        try {
            String filePath = "images/" + fileName;
            Log.d("ARNavigationActivity", "Trying to load image from: " + filePath);
            InputStream inputStream = getAssets().open(filePath);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap == null) {
                Log.e("ARNavigationActivity", "Failed to decode bitmap from: " + filePath);
            } else {
                Log.d("ARNavigationActivity", "Successfully loaded bitmap with dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            }

            return bitmap;
        } catch (IOException e) {
            Log.e("ARNavigationActivity", "Error loading image from asset: " + fileName + ", Error: " + e.getMessage());
            return null;
        }
    }

    //相機預覽圖片

    //讀取圖片
    private String getImageFileName(int imageId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        String fileName = null;

        try {
            cursor = db.query(
                    "picture_data",
                    new String[]{"image", "file_extension"},
                    "image = ?",
                    new String[]{String.valueOf(imageId)},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String imageName = cursor.getString(cursor.getColumnIndexOrThrow("image"));
                String fileExtension = cursor.getString(cursor.getColumnIndexOrThrow("file_extension"));
                fileName = imageName + fileExtension;
            }
        } catch (Exception e) {
            Log.e("ORBActivity", "Error getting image file name from database: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return fileName;
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private static class LocationData {
        private String locationName;
        private String imageData;
        private String imageFileName;

        public LocationData(String locationName, String imageData, String imageFileName) {
            this.locationName = locationName;
            this.imageData = imageData;
            this.imageFileName = imageFileName;
        }

        public String getLocationName() {
            return locationName;
        }

        public String getImageData() {
            return imageData;
        }

        public String getImageFileName() {
            return imageFileName;
        }
    }



    private void loadLocationDataFromDatabase() {
        locationDataList.clear(); // 清空現有數據，確保只使用資料庫數據
        SQLiteDatabase db = dbHelper.openDataBase();
        Cursor cursor = null;
        try {
            cursor = db.query("picture_data", new String[]{"location_data", "image"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                String locationName = cursor.getString(cursor.getColumnIndexOrThrow("location_data"));
                int imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image"));
                String imageFileName = getImageFileName(imageId);
                Bitmap bitmap = getBitmapFromAsset(imageFileName);
                if (bitmap != null) {
                    String imageData = convertBitmapToBase64(bitmap);
                    locationDataList.add(new LocationData(locationName, imageData, imageFileName));
                    Log.d("ARNavigationActivity", "Loaded location: " + locationName + " with image: " + imageFileName);
                } else {
                    Log.w("ARNavigationActivity", "Failed to load bitmap for: " + imageFileName);
                }
            }
        } catch (Exception e) {
            Log.e("ARNavigationActivity", "Error loading location data: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }


}
