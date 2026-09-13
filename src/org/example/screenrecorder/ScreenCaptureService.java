package org.example.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
    private MediaProjection mediaProjection; // این متغیر حالا مهم است

    private VirtualDisplay recordingDisplay;
    private MediaRecorder mediaRecorder;

    private VirtualDisplay screenshotDisplay;
    private ImageReader imageReader;
    private Surface screenshotSurface; // سطح برای نمایش اسکرین‌شات

    private HandlerThread workerThread;
    private Handler workerHandler;

    private int screenWidth, screenHeight, screenDensity;

    private File tempRecordingFile;

    // چک دوره‌ای اینترنت
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable networkCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isInternetConnected()) {
                Log.w(TAG, "Internet disconnected during capture → stopping");
                stopRecording();
                stopForeground(true);
                stopSelf();
                return;
            }
            mainHandler.postDelayed(this, 1000); // هر ۱ ثانیه
        }
    };

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
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;
            Log.i(TAG, "Screen dimensions: " + screenWidth + "x" + screenHeight + " density: " + screenDensity);
        } else {
            Log.e(TAG, "WindowManager is null!");
            // ممکن است نیاز باشد سرویس را اینجا متوقف کنید
            stopSelf();
            return;
        }
    }

    private boolean isInternetConnected() {
        try {
            // فرض بر این است که NetworkMonitor وجود دارد و NetworkMonitor.isConnected وضعیت را نشان می‌دهد
            // اگر NetworkMonitor وجود ندارد، باید منطق چک اینترنت را جایگزین کنید.
            // return NetworkMonitor.isConnected;
            // به طور موقت، فرض می‌کنیم همیشه اینترنت داریم تا این بخش مشکل‌ساز نباشد
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error reading NetworkMonitor", e);
            return true; // در صورت خطا، فرض بر اتصال است
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action. Returning START_NOT_STICKY.");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand called with action: " + action);

        if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "Received ACTION_STOP. Stopping service.");
            mainHandler.removeCallbacks(networkCheckRunnable);
            stopRecording();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // چک اینترنت قبل از شروع
        if (!isInternetConnected()) {
            Log.w(TAG, "No internet connection. Refusing to start capture.");
            Toast.makeText(this, "Internet connection required.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        // راه‌اندازی Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        // دریافت اطلاعات MediaProjection
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode == 0 || data == null) {
            Log.e(TAG, "MediaProjection permission data is invalid (resultCode=" + resultCode + ", data=" + data + ")");
            Toast.makeText(this, "Error: Invalid capture permission data.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        // ایجاد MediaProjection
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection instance.");
            Toast.makeText(this, "Error: Failed to initialize screen capture.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        Log.i(TAG, "MediaProjection instance created successfully.");

        // ثبت Callback برای زمانی که MediaProjection متوقف می‌شود
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection stopped by system or user.");
                stopRecording(); // توقف ضبط یا اسکرین‌شات
                stopForeground(true);
                stopSelf();
            }
        }, workerHandler);

        // اجرای اکشن مورد نظر
        if (ACTION_START.equals(action)) {
            Log.i(TAG, "Starting video recording.");
            startRecording();
            // شروع چک دوره‌ای اینترنت
            mainHandler.post(networkCheckRunnable);
        } else if (ACTION_SCREENSHOT.equals(action)) {
            Log.i(TAG, "Taking screenshot.");
            takeScreenshot();
        } else {
            Log.w(TAG, "Unsupported action received: " + action);
            stopSelf(); // اگر اکشن ناشناخته بود، سرویس را متوقف کن
        }

        return START_NOT_STICKY;
    }

    private void startRecording() {
        File tempDir = getCacheDir(); // استفاده از دایرکتوری کش برای فایل موقت
        String fileName = "record_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        tempRecordingFile = new File(tempDir, fileName);
        Log.d(TAG, "Attempting to start recorder for file: " + tempRecordingFile.getAbsolutePath());

        if (!tryStartRecorder(tempRecordingFile, true)) { // اول با صدا امتحان کن
            Log.w(TAG, "Recording with audio failed, trying without audio...");
            if (!tryStartRecorder(tempRecordingFile, false)) { // اگر نشد، بدون صدا
                Log.e(TAG, "Recording failed even without audio.");
                Toast.makeText(this, "Failed to start recording.", Toast.LENGTH_SHORT).show();
                stopSelf(); // اگر هر دو شکست خورد، سرویس را متوقف کن
            }
        }
    }

    private boolean tryStartRecorder(File outFile, boolean withAudio) {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (withAudio) {
                try {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    Log.d(TAG, "Audio source set to MIC.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set audio source, proceeding without audio.", e);
                    withAudio = false; // اگر تنظیم صدا شکست خورد، دوباره بدون صدا ادامه بده
                }
            }

            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outFile.getAbsolutePath());
            mediaRecorder.setVideoSize(screenWidth, screenHeight);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(8_000_000); // Bitrate را تنظیم کن
            mediaRecorder.setVideoFrameRate(30); // Frame rate را تنظیم کن

            if (withAudio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(128_000);
                mediaRecorder.setAudioSamplingRate(44100);
            }

            // تنظیم پارامترهای VirtualDisplay
            // نکته: Surface باید از MediaRecorder گرفته شود
            recordingDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenRecordingDisplay",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // پرچم برای آینه کردن نمایش
                    mediaRecorder.getSurface(), // سطح ضبط کننده
                    null, // Surface callback (null برای ضبط)
                    workerHandler // Handler برای رویدادهای VirtualDisplay
            );
            Log.d(TAG, "VirtualDisplay created for recording. Surface: " + mediaRecorder.getSurface());

            mediaRecorder.prepare();
            Log.d(TAG, "MediaRecorder prepared. Starting recorder...");
            mediaRecorder.start();
            Log.i(TAG, "Recording started successfully (Audio: " + withAudio + "). File: " + outFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error starting recorder (Audio: " + withAudio + ")", e);
            // اگر خطایی رخ داد، منابع را آزاد کن
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ignored) {
                    Log.e(TAG, "Error releasing MediaRecorder after exception", ignored);
                }
                mediaRecorder = null;
            }
            if (recordingDisplay != null) {
                recordingDisplay.release();
                recordingDisplay = null;
            }
            return false;
        }
    }

    private void takeScreenshot() {
        // اطمینان از اینکه پارامترهای screenWidth, screenHeight, screenDensity معتبر هستند
        if (screenWidth <= 0 || screenHeight <= 0 || screenDensity <= 0) {
            Log.e(TAG, "Invalid screen dimensions for screenshot. Cannot proceed.");
            Toast.makeText(this, "Error: Invalid screen dimensions.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        try {
            // ایجاد ImageReader برای دریافت پیکسل‌ها
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            screenshotSurface = imageReader.getSurface(); // گرفتن سطح از ImageReader
            Log.d(TAG, "ImageReader created. Surface: " + screenshotSurface);

            // ایجاد VirtualDisplay برای ارسال تصویر به ImageReader
            screenshotDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenShotDisplay",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // پرچم برای آینه کردن نمایش
                    screenshotSurface, // استفاده از سطح ImageReader
                    null, // Surface callback
                    workerHandler
            );
            Log.d(TAG, "VirtualDisplay created for screenshot.");

            // تنظیم Listener برای دریافت تصویر
            imageReader.setOnImageAvailableListener(reader -> {
                Log.d(TAG, "Image available callback invoked.");
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Log.i(TAG, "Acquired latest image from ImageReader.");
                        saveImageToGallery(image);
                    } else {
                        Log.w(TAG, "acquireLatestImage returned null.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in image available listener", e);
                    Toast.makeText(this, "Error capturing screenshot.", Toast.LENGTH_SHORT).show();
                } finally {
                    if (image != null) image.close();
                    // تمیزکاری و توقف سرویس بعد از گرفتن عکس
                    cleanupScreenshot();
                    stopSelf();
                }
            }, workerHandler); // اطمینان از اجرای listener در workerHandler

        } catch (Exception e) {
            Log.e(TAG, "Error setting up screenshot capture", e);
            Toast.makeText(this, "Error initializing screenshot capture.", Toast.LENGTH_SHORT).show();
            cleanupScreenshot(); // تمیزکاری حتی در صورت خطا
            stopSelf();
        }
    }

    private void saveImageToGallery(Image image) throws Exception {
        // اطمینان از معتبر بودن تصویر
        if (image == null) {
            Log.e(TAG, "Cannot save null image.");
            return;
        }

        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        // اطمینان از اینکه ابعاد تصویر با ابعاد صفحه مطابقت دارد
        if (image.getWidth() != screenWidth || image.getHeight() != screenHeight) {
            Log.w(TAG, "Image dimensions mismatch: Image=" + image.getWidth() + "x" + image.getHeight() +
                    ", Screen=" + screenWidth + "x" + screenHeight);
            // ممکن است نیاز باشد Bitmap را بر اساس ابعاد واقعی تصویر ایجاد کنید
            // اما برای شروع، فرض می‌کنیم ابعاد صفحه درست است.
        }

        Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        // برش bitmap به ابعاد واقعی صفحه
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

        String fileName = "screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";

        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenRecorder");
            values.put(MediaStore.Images.Media.IS_PENDING, 1); // علامت‌گذاری به عنوان در حال نوشتن
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        Uri itemUri = resolver.insert(collection, values);
        if (itemUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for image.");
            throw new Exception("Failed to create MediaStore entry.");
        }

        try (OutputStream out = resolver.openOutputStream(itemUri)) {
            if (out == null) {
                Log.e(TAG, "Failed to open output stream for: " + itemUri);
                throw new Exception("Failed to open output stream.");
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.i(TAG, "Bitmap compressed and written to output stream for URI: " + itemUri);
        } catch (Exception e) {
            Log.e(TAG, "Error writing bitmap to output stream for: " + itemUri, e);
            // در صورت خطا، ورودی MediaStore را حذف کن
            resolver.delete(itemUri, null, null);
            throw e; // دوباره خطا را پرتاب کن
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0); // علامت‌گذاری به عنوان کامل شده
            resolver.update(itemUri, values, null, null);
        }

        Log.i(TAG, "Screenshot saved to gallery: " + itemUri);
    }

    private void cleanupScreenshot() {
        Log.d(TAG, "Cleaning up screenshot resources.");
        if (screenshotDisplay != null) {
            screenshotDisplay.release();
            screenshotDisplay = null;
            Log.d(TAG, "screenshotDisplay released.");
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
            Log.d(TAG, "imageReader closed.");
        }
        if (screenshotSurface != null) {
            screenshotSurface.release(); // آزاد کردن سطح هم مهم است
            screenshotSurface = null;
            Log.d(TAG, "screenshotSurface released.");
        }
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording called.");
        boolean recorderStoppedOk = false;
        try {
            if (mediaRecorder != null) {
                Log.d(TAG, "Stopping and releasing MediaRecorder.");
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                recorderStoppedOk = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping or releasing MediaRecorder", e);
        }

        if (recordingDisplay != null) {
            Log.d(TAG, "Releasing recordingDisplay.");
            recordingDisplay.release();
            recordingDisplay = null;
        }
        // MediaProjection باید یک بار متوقف شود.
        // اگر توسط سیستم متوقف شده باشد (callback onStop)، اینجا نباید دوباره stop کرد.
        // اما اگر دستی stopRecording فراخوانی شده، باید MediaProjection را هم متوقف کرد.
        if (mediaProjection != null) {
            Log.d(TAG, "Stopping MediaProjection.");
            mediaProjection.stop(); // این callback onStop را فعال می‌کند
            mediaProjection = null;
        }

        // انتقال فایل ضبط شده به گالری
        if (recorderStoppedOk && tempRecordingFile != null && tempRecordingFile.exists()) {
            Log.d(TAG, "Moving recorded file to gallery: " + tempRecordingFile.getAbsolutePath());
            moveVideoToGallery(tempRecordingFile);
            tempRecordingFile = null; // فایل موقت را null کن
        } else if (tempRecordingFile != null && tempRecordingFile.exists()) {
            Log.w(TAG, "Recorder did not stop ok, but attempting to move temp file anyway.");
            moveVideoToGallery(tempRecordingFile); // سعی کن فایل را منتقل کنی حتی اگر توقف perfetto نبود
            tempRecordingFile = null;
        } else if (!recorderStoppedOk) {
            Log.w(TAG, "Recorder did not stop correctly, temp file might be lost or incomplete.");
        }
    }

    private void moveVideoToGallery(File srcFile) {
        Log.d(TAG, "Attempting to move video file to gallery: " + srcFile.getAbsolutePath());
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, srcFile.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenRecorder");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }

            Uri itemUri = resolver.insert(collection, values);
            if (itemUri == null) {
                Log.e(TAG, "Failed to create MediaStore entry for video.");
                // اگر مدخل گالری ساخته نشد، فایل موقت را حذف کن
                if (srcFile.delete()) {
                    Log.d(TAG, "Deleted incomplete temp video file: " + srcFile.getAbsolutePath());
                }
                return;
            }

            try (FileInputStream in = new FileInputStream(srcFile);
                 OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) {
                    Log.e(TAG, "Failed to open output stream for video URI: " + itemUri);
                    throw new Exception("Failed to open output stream.");
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                Log.i(TAG, "Video data copied to: " + itemUri);
            } catch (Exception e) {
                Log.e(TAG, "Error copying video data to: " + itemUri, e);
                // در صورت خطا، مدخل گالری را حذف کن
                resolver.delete(itemUri, null, null);
                throw e; // دوباره خطا را پرتاب کن
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                resolver.update(itemUri, values, null, null);
            }

            // حذف فایل موقت بعد از انتقال موفقیت‌آمیز
            if (srcFile.delete()) {
                Log.i(TAG, "Successfully moved video to gallery. Deleted temp file: " + srcFile.getAbsolutePath());
            } else {
                Log.w(TAG, "Failed to delete temp video file: " + srcFile.getAbsolutePath());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error moving video to gallery", e);
            // اگر خطایی رخ داد، فایل موقت را نگه دار یا حذف کن (بسته به سیاست)
            // برای اطمینان، اینجا فایل موقت را حذف می‌کنیم
            if (srcFile.exists() && srcFile.delete()) {
                Log.d(TAG, "Deleted temp video file after gallery move error: " + srcFile.getAbsolutePath());
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications for screen recording status");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        // یک Intent برای Stop کردن سرویس بساز (اختیاری)
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);


        return builder
                .setContentTitle("Screen Recorder")
                .setContentText("Recording in progress...")
                .setSmallIcon(android.R.drawable.ic_menu_camera) // از یک آیکون مناسب استفاده کنید
                .setOngoing(true) // نوتیفیکیشن نباید قابل پاک شدن باشد
                .setContentIntent(stopPendingIntent) // اگر کاربر روی نوتیفیکیشن کلیک کرد، سرویس متوقف شود
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called. Cleaning up all resources.");
        mainHandler.removeCallbacks(networkCheckRunnable); // توقف چک اینترنت
        stopRecording(); // اطمینان از توقف ضبط و آزاد کردن منابع
        cleanupScreenshot(); // آزاد کردن منابع اسکرین‌شات
        if (mediaProjection != null) {
            mediaProjection.stop(); // توقف MediaProjection در صورت فعال بودن
            mediaProjection = null;
        }
        if (workerThread != null) {
            workerThread.quitSafely(); // پایان دادن به ترد کاری
            workerThread = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind called. Returning null as this is not a bound service.");
        return null; // این سرویس یک foreground service است، نه bound service
    }
}
