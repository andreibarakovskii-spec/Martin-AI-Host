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
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local face presence. An explicit requestFrame emits one JPEG to the host;
 * the host obtains separate upload consent. No images are stored.
 * It emits just normalized (-1..1) face coordinates for Martin's head/eye look target.
 */
@ExperimentalGetImage
public final class MartinFaceTracker implements AutoCloseable {
    public interface Listener {
        void onLook(float x, float y, boolean faceVisible);
        void onStatus(boolean active, String message);
        default void onPerson(int trackingId,int faceCount,boolean visible,float[] appearance){}
        default void onFrame(byte[] jpeg) {}
    }

    private final FragmentActivity activity;
    private final Listener listener;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final FaceDetector detector;
    private final PoseDetector poseDetector;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private volatile boolean running, closing, wantFrame;
    public void requestFrame(){wantFrame=true;}
    private float smoothX = 0f, smoothY = 0f;
    private long lastFaceAt = 0L;
    private long lastEmitAt = 0L;
    private int lastTrackingId=-1,lastFaceCount=0;
    private long lastPersonAt;private int frameNumber;

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
        poseDetector=PoseDetection.getClient(new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());
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
        if(wantFrame){wantFrame=false;try{listener.onFrame(CameraFrameEncoder.jpeg(media,rotation));}catch(Exception e){listener.onStatus(false,"Не удалось получить кадр");}}
        InputImage input = InputImage.fromMediaImage(media, rotation);
        detector.process(input).addOnSuccessListener(cameraExecutor,faces->{
            if(faces!=null&&faces.size()==1&&(++frameNumber%8)==0)poseDetector.process(input)
             .addOnSuccessListener(cameraExecutor,pose->emitFace(faces,proxy.getWidth(),proxy.getHeight(),rotation,media,pose))
             .addOnFailureListener(cameraExecutor,e->emitFace(faces,proxy.getWidth(),proxy.getHeight(),rotation,media,null))
             .addOnCompleteListener(cameraExecutor,t->finish(proxy));
            else{emitFace(faces,proxy.getWidth(),proxy.getHeight(),rotation,media,null);finish(proxy);}
        }).addOnFailureListener(cameraExecutor,e->{emitCenterIfNeeded();finish(proxy);});
    }

    private void finish(ImageProxy proxy){inFlight.set(false);proxy.close();if(closing){detector.close();poseDetector.close();cameraExecutor.shutdown();}}

    private void emitFace(List<Face> faces, int rawW, int rawH, int rotation,Image media,Pose pose) {
        if(!running)return;
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
            Integer tracking=best.getTrackingId();
            int id=faces.size()==1&&tracking!=null?tracking:-1;
            if(id!=lastTrackingId||faces.size()!=lastFaceCount||now-lastPersonAt>700){lastTrackingId=id;lastFaceCount=faces.size();lastPersonAt=now;listener.onPerson(id,faces.size(),true,faces.size()==1?appearance(media,rawW,rawH,best,pose):null);}
        }
    }

    private void emitCenterIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFaceAt < 700L || now - lastEmitAt < 100L) return;
        smoothX *= 0.82f;
        smoothY *= 0.82f;
        lastEmitAt = now;
        listener.onLook(smoothX, smoothY, false);
        if(lastFaceCount!=0){lastFaceCount=0;lastTrackingId=-1;listener.onPerson(-1,0,false,null);}
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    public void stop() {
        running = false;
        wantFrame=false;
        if (analysis != null) analysis.clearAnalyzer();
        if (provider != null) provider.unbindAll();
        analysis = null;
        listener.onLook(0f, 0f, false);
        listener.onPerson(-1,0,false,null);
        listener.onStatus(false, "Камера выключена");
    }

    @Override public void close() {
        closing=true;
        stop();
        if(!inFlight.get()){detector.close();poseDetector.close();cameraExecutor.shutdown();}
    }

    private static float[] appearance(Image image,int width,int height,Face face,Pose pose){
        float y=0,u=0,v=0;int count=0;try{Image.Plane[] p=image.getPlanes();for(int gy=5;gy<=8;gy++)for(int gx=3;gx<=7;gx++){int x=Math.min(width-1,width*gx/10),yy=Math.min(height-1,height*gy/10);y+=sample(p[0],x,yy);u+=sample(p[1],x/2,yy/2);v+=sample(p[2],x/2,yy/2);count++;}}catch(Exception ignored){}
        Rect f=face.getBoundingBox();float shoulder=0,torso=0;if(pose!=null){PoseLandmark ls=pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),rs=pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),lh=pose.getPoseLandmark(PoseLandmark.LEFT_HIP),rh=pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);if(good(ls)&&good(rs))shoulder=Math.abs(ls.getPosition().x-rs.getPosition().x)/Math.max(1f,width);if(good(ls)&&good(rs)&&good(lh)&&good(rh)){float sy=(ls.getPosition().y+rs.getPosition().y)/2f,hy=(lh.getPosition().y+rh.getPosition().y)/2f;torso=Math.abs(hy-sy)/Math.max(1f,height);}}
        return new float[]{count==0?0:y/count/255f,count==0?.5f:u/count/255f,count==0?.5f:v/count/255f,shoulder,torso,f.width()/Math.max(1f,(float)f.height())};
    }
    private static boolean good(PoseLandmark p){return p!=null&&p.getInFrameLikelihood()>.55f;}
    private static int sample(Image.Plane p,int x,int y){java.nio.ByteBuffer b=p.getBuffer();int i=y*p.getRowStride()+x*p.getPixelStride();return i>=0&&i<b.limit()?b.get(i)&255:0;}
}
