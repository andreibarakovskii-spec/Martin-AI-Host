package com.imagine.martinhost;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.Image;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local-only front-camera gaze tracker. No frame leaves the device and no image is stored.
 * It emits just normalized (-1..1) face coordinates for Martin's head/eye look target.
 */
@ExperimentalGetImage
public final class MartinFaceTracker implements AutoCloseable {
    public interface Listener {
        void onLook(float x, float y, boolean faceVisible);
        void onStatus(boolean active, String message);
    }

    private final FragmentActivity activity;
    private final Listener listener;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final FaceDetector detector;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private volatile boolean running;
    private float smoothX = 0f, smoothY = 0f;
    private long lastFaceAt = 0L;
    private long lastEmitAt = 0L;

    public MartinFaceTracker(FragmentActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.12f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);
    }

    public void start() {
        if (running) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus(false, "Нужен доступ к камере");
            return;
        }
        running = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                provider = future.get();
                bind();
            } catch (Exception e) {
                running = false;
                listener.onStatus(false, "Камера недоступна");
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void bind() {
        if (!running || provider == null) return;
        analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyze);
        try {
            provider.unbindAll();
            provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
            listener.onStatus(true, "Взгляд по камере");
        } catch (Exception e) {
            running = false;
            listener.onStatus(false, "Нет фронтальной камеры");
        }
    }

    private void analyze(@NonNull ImageProxy proxy) {
        if (!running || !inFlight.compareAndSet(false, true)) {
            proxy.close();
            return;
        }
        Image media = proxy.getImage();
        if (media == null) {
            inFlight.set(false);
            proxy.close();
            return;
        }
        int rotation = proxy.getImageInfo().getRotationDegrees();
        InputImage input = InputImage.fromMediaImage(media, rotation);
        detector.process(input)
                .addOnSuccessListener(cameraExecutor, faces -> emitFace(faces, proxy.getWidth(), proxy.getHeight(), rotation))
                .addOnFailureListener(cameraExecutor, e -> emitCenterIfNeeded())
                .addOnCompleteListener(cameraExecutor, task -> {
                    inFlight.set(false);
                    proxy.close();
                });
    }

    private void emitFace(List<Face> faces, int rawW, int rawH, int rotation) {
        if (faces == null || faces.isEmpty()) {
            emitCenterIfNeeded();
            return;
        }
        Face best = null;
        int area = -1;
        for (Face f : faces) {
            Rect r = f.getBoundingBox();
            int a = Math.max(0, r.width()) * Math.max(0, r.height());
            if (a > area) { best = f; area = a; }
        }
        if (best == null) return;
        Rect r = best.getBoundingBox();
        float width = (rotation == 90 || rotation == 270) ? rawH : rawW;
        float height = (rotation == 90 || rotation == 270) ? rawW : rawH;
        float cx = r.exactCenterX();
        float cy = r.exactCenterY();

        // Front camera is mirror-like to the guest: invert X for natural eye contact.
        float nx = -clamp((cx / Math.max(1f, width)) * 2f - 1f);
        float ny = -clamp((cy / Math.max(1f, height)) * 2f - 1f);
        smoothX = smoothX * 0.76f + nx * 0.24f;
        smoothY = smoothY * 0.76f + ny * 0.24f;
        lastFaceAt = SystemClock.elapsedRealtime();
        long now = lastFaceAt;
        if (now - lastEmitAt >= 65L) {
            lastEmitAt = now;
            listener.onLook(smoothX, smoothY, true);
        }
    }

    private void emitCenterIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFaceAt < 700L || now - lastEmitAt < 100L) return;
        smoothX *= 0.82f;
        smoothY *= 0.82f;
        lastEmitAt = now;
        listener.onLook(smoothX, smoothY, false);
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    public void stop() {
        running = false;
        if (analysis != null) analysis.clearAnalyzer();
        if (provider != null) provider.unbindAll();
        analysis = null;
        listener.onLook(0f, 0f, false);
        listener.onStatus(false, "Камера выключена");
    }

    @Override public void close() {
        stop();
        detector.close();
        cameraExecutor.shutdownNow();
    }
}
