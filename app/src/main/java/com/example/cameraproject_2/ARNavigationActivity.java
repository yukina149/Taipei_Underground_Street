package com.example.cameraproject_2;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.ArrayList;
import java.util.List;

public class ARNavigationActivity extends AppCompatActivity{

    private static final int CAMERA_PERMISSION_CODE = 0;
    private ArSceneView arSceneView;
    private Session session;
    private boolean installRequested;
    private List<Anchor> anchors = new ArrayList<>();
    private Button startNavigationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arnavigation);

        // 初始化AR
        arSceneView = findViewById(R.id.ar_scene_view);
        startNavigationButton = findViewById(R.id.start_navigation_button);

        // 檢查相機權限
        if (checkCameraPermission()) {
            setupAR();
        }

        startNavigationButton.setOnClickListener(v -> startNavigation());
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            return false;
        }
        return true;
    }


    private void setupAR() {
        // 檢查 AR Core 是否可用
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            return;
        }

        if (availability.isSupported()) {
            try {
                // 創建 AR Session
                session = new Session(this);
                // 設置更新模式為 LATEST_CAMERA_IMAGE
                Config config = new Config(session);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                session.configure(config);
                arSceneView.setupSession(session);
            } catch (Exception e) {
                Toast.makeText(this, "無法初始化 AR session", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            Toast.makeText(this, "此設備不支援 AR", Toast.LENGTH_LONG).show();
            return;
        }

        // 設置 AR 場景更新監聽器
        arSceneView.getScene().addOnUpdateListener(frameTime -> {
            Frame frame = arSceneView.getArFrame();
            if (frame == null) {
                return;
            }
            updateNavigation(frame);
        });
    }



    private void startNavigation() {
        // 開始導航時的處理邏輯
        Toast.makeText(this, "開始導航", Toast.LENGTH_SHORT).show();

        // 加入導航路徑點
        addNavigationPoint(new Vector3(0, 0, -1));
        addNavigationPoint(new Vector3(1, 0, -2));
        addNavigationPoint(new Vector3(-1, 0, -3));
    }

    private void addNavigationPoint(Vector3 position) {
        ViewRenderable.builder()
                .setView(this, R.layout.navigation_marker)
                .build()
                .thenAccept(renderable -> {
                    AnchorNode anchorNode = new AnchorNode();
                    anchorNode.setLocalPosition(position);
                    anchorNode.setRenderable(renderable);
                    arSceneView.getScene().addChild(anchorNode);
                });
    }

    private void updateNavigation(Frame frame) {
        Camera camera = frame.getCamera();
        if (camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        // 更新導航指示器的位置和方向
        for (Anchor anchor : anchors) {
            // 在這裡實現導航邏輯
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) {
            try {
                session.resume();
                arSceneView.resume();
            } catch (CameraNotAvailableException e) {
                Toast.makeText(this, "相機不可用，請檢查相機設置", Toast.LENGTH_LONG).show();
                e.printStackTrace();
            } catch (Exception e) {
                Toast.makeText(this, "發生錯誤：" + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            session.pause();
            arSceneView.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                setupAR();
            } else {
                Toast.makeText(this, "需要相機權限才能使用AR功能", Toast.LENGTH_LONG).show();
            }
        }
    }


}

