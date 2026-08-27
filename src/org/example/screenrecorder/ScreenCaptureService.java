package org.example.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;   // ← اضافه شد
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START = "org.example.screenrecorder.START";
    public static final String ACTION_SCREENSHOT = "org.example.screenrecorder.SCREENSHOT";
    public static final String ACTION_STOP = "org.example.screenrecorder.STOP";

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;

    private VirtualDisplay recordingDisplay;
    private MediaRecorder mediaRecorder;

    private VirtualDisplay screenshotDisplay;
    private ImageReader imageReader;

    private HandlerThread workerThread;
    private Handler workerHandler;

    private int screenWidth, screenHeight, screenDensity;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();

        workerThread = new HandlerThread("ScreenCaptureWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopRecording();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // ✅ اصلاح: برای اندروید ۱۰ و بالاتر باید foregroundServiceType مشخص شود
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode == 0 || data == null) {
            Log.e(TAG, "داده مجوز MediaProjection نامعتبر است");
            stopSelf();
            return START_NOT_STICKY;
        }

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "دریافت MediaProjection ناموفق بود");
            stopSelf();
            return START_NOT_STICKY;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopRecording();
            }
        }, workerHandler);

        if (ACTION_START.equals(action)) {
            startRecording();
        } else if (ACTION_SCREENSHOT.equals(action)) {
            takeScreenshot();
        }

        return START_NOT_STICKY;
    }

    private void startRecording() {
        File outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (outDir != null && !outDir.exists()) outDir.mkdirs();
        String fileName = "record_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        File outFile = new File(outDir, fileName);

        if (!tryStartRecorder(outFile, true)) {
            Log.w(TAG, "ضبط با صدا ناموفق بود، تلاش بدون صدا...");
            if (!tryStartRecorder(outFile, false)) {
                Log.e(TAG, "ضبط حتی بدون صدا هم شکست خورد");
                stopSelf();
            }
        }
    }

    private boolean tryStartRecorder(File outFile, boolean withAudio) {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (withAudio) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outFile.getAbsolutePath());
            mediaRecorder.setVideoSize(screenWidth, screenHeight);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (withAudio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(128_000);
                mediaRecorder.setAudioSamplingRate(44100);
            }
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoEncodingBitRate(8_000_000);
            mediaRecorder.prepare();

            recordingDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenRecording",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, workerHandler
            );

            mediaRecorder.start();
            Log.i(TAG, "ضبط شروع شد (صدا: " + withAudio + "): " + outFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "خطا در شروع ضبط (صدا: " + withAudio + ")", e);
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ignored) {
                }
                mediaRecorder = null;
            }
            return false;
        }
    }

    private void takeScreenshot() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        screenshotDisplay = mediaProjection.createVirtualDisplay(
                "ScreenShot",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, workerHandler
        );

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    saveImage(image);
                }
            } catch (Exception e) {
                Log.e(TAG, "خطا در گرفتن عکس", e);
            } finally {
                if (image != null) image.close();
                cleanupScreenshot();
                stopSelf();
            }
        }, workerHandler);
    }

    private void saveImage(Image image) throws Exception {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

        File outDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (outDir != null && !outDir.exists()) outDir.mkdirs();
        String fileName = "screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        File outFile = new File(outDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        Log.i(TAG, "عکس ذخیره شد: " + outFile.getAbsolutePath());
    }

    private void cleanupScreenshot() {
        if (screenshotDisplay != null) {
            screenshotDisplay.release();
            screenshotDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در توقف ضبط", e);
        }
        if (recordingDisplay != null) {
            recordingDisplay.release();
            recordingDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("ضبط صفحه")
                .setContentText("در حال اجرا...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
        cleanupScreenshot();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }
