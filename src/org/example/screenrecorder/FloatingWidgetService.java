package org.example.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver; // Import BroadcastReceiver
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter; // Import IntentFilter
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater; // Import LayoutInflater
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast; // برای نمایش پیام کوتاه

// فرض می کنیم این کلاس NetworkMonitor در پکیج org.example.screenrecorder وجود دارد
// import org.example.screenrecorder.NetworkMonitor; // در صورت نیاز این را اضافه کنید

public class FloatingWidgetService extends Service {

    private static final String TAG = "FloatingWidgetService";
    private static final String CHANNEL_ID = "floating_widget_channel";
    private static final int NOTIFICATION_ID = 2;

    private WindowManager windowManager;
    private LinearLayout rootView; // دکمه اصلی شناور
    private LinearLayout menuView; // منوی باز شده
    private boolean menuOpen = false;
    private WindowManager.LayoutParams rootParams;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- متغیرهای جدید برای مدیریت وضعیت ویجت و شبکه ---
    private boolean isWidgetVisible = false; // آیا ویجت در حال حاضر نمایش داده می شود؟
    private boolean isInternetConnected = false; // آخرین وضعیت شناخته شده اینترنت

    // اکشن و کلید مربوط به broadcast وضعیت شبکه
    private static final String NETWORK_STATUS_ACTION = "com.example.screenrecorder.NETWORK_STATUS_CHANGED";
    private static final String EXTRA_IS_CONNECTED = "is_connected";

    // BroadcastReceiver برای دریافت وضعیت شبکه
    private BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null && intent.getAction().equals(NETWORK_STATUS_ACTION)) {
                boolean connected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false);
                Log.d(TAG, "Received network status broadcast: " + connected);

                // فقط در صورتی پردازش کن که وضعیت شبکه تغییر کرده باشد
                if (connected != isInternetConnected) {
                    isInternetConnected = connected;
                    updateWidgetVisibility();
                }
            }
        }
    };
    // --- پایان متغیرهای جدید ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate...");
        createNotificationChannel();

        // شروع سرویس به صورت foreground
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // *** ثبت BroadcastReceiver در onCreate ***
        IntentFilter filter = new IntentFilter(NETWORK_STATUS_ACTION);
        registerReceiver(networkStatusReceiver, filter);
        Log.d(TAG, "Network status receiver registered.");

        // *** بررسی وضعیت اولیه اینترنت ***
        // این را می توان از NetworkMonitor گرفت، اگر NetworkMonitor در حال اجرا باشد
        // فرض می کنیم NetworkMonitor.getIsConnected() مقدار درست را برمی گرداند
        checkInitialNetworkState();

        // اضافه کردن دکمه شناور (فقط اگر اینترنت وصل باشد)
        if (isInternetConnected) {
            addFloatingButton();
            isWidgetVisible = true;
        } else {
            Log.d(TAG, "Internet is not connected initially, floating button will not be added.");
            isWidgetVisible = false;
        }
    }

    private void checkInitialNetworkState() {
        // اگر NetworkMonitor را در جای دیگری از برنامه راه اندازی کرده اید
        // می توانید وضعیت اولیه را از آن بگیرید.
        // اگر NetworkMonitor جداگانه اجرا نمی شود، باید آن را اینجا یا در جای دیگری راه اندازی کنید.
        try {
            // فرض می کنیم NetworkMonitor.getIsConnected() به درستی کار می کند
            // اگر NetworkMonitor کلاس جداگانه ای است، ممکن است نیاز به راه اندازی آن داشته باشید
            // NetworkMonitor.startMonitoring(this); // اگر نیاز است، مانیتورینگ را اینجا شروع کنید
            isInternetConnected = NetworkMonitor.getIsConnected(); // مقدار volatile را بخوانید
            Log.d(TAG, "Initial internet connection state: " + isInternetConnected);
        } catch (Exception e) {
            Log.e(TAG, "Could not get initial network state from NetworkMonitor.", e);
            isInternetConnected = false; // فرض می کنیم قطع است در صورت خطا
        }
    }

    private void updateWidgetVisibility() {
        if (isInternetConnected) {
            // اگر اینترنت وصل است و ویجت هنوز نمایش داده نشده، آن را نمایش بده
            if (!isWidgetVisible) {
                addFloatingButton(); // دوباره دکمه را اضافه می کنیم
                isWidgetVisible = true;
                Log.d(TAG, "Internet connected, showing floating widget.");
                Toast.makeText(this, "اینترنت وصل شد، شناور ظاهر شد", Toast.LENGTH_SHORT).show();
            }
        } else {
            // اگر اینترنت قطع است و ویجت نمایش داده شده، آن را مخفی کن
            if (isWidgetVisible) {
                removeFloatingButton();
                isWidgetVisible = false;
                Log.d(TAG, "Internet disconnected, hiding floating widget.");
                Toast.makeText(this, "اینترنت قطع شد، شناور مخفی شد", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Floating Button", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
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
        return builder
                .setContentTitle("دکمه شناور فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_camera) // آیکون کوچک مناسب را انتخاب کنید
                .build();
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void addFloatingButton() {
        // اگر دکمه از قبل وجود دارد، آن را اضافه نکن
        if (rootView != null && rootView.isAttachedToWindow()) {
            Log.d(TAG, "Floating button already exists.");
            return;
        }
        Log.d(TAG, "Adding floating button.");

        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);

        TextView mainButton = new TextView(this);
        mainButton.setText("\u2699"); // آیکون چرخ دنده
        mainButton.setTextSize(24);
        mainButton.setTextColor(Color.WHITE);
        mainButton.setGravity(Gravity.CENTER);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#CC2196F3")); // رنگ آبی
        mainButton.setBackground(circle);
        mainButton.setPadding(30, 30, 30, 30);

        rootView.addView(mainButton);

        rootParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // مهم: برای اینکه فوکوس را نگیرد
                PixelFormat.TRANSLUCENT
        );
        rootParams.gravity = Gravity.TOP | Gravity.START;
        // موقعیت اولیه: می توانید این را بر اساس تنظیمات کاربر تنظیم کنید
        rootParams.x = 0;
        rootParams.y = 300;

        try {
            windowManager.addView(rootView, rootParams);
            isWidgetVisible = true; // وضعیت ویجت را فعال می کنیم
        } catch (Exception e) {
            Log.e(TAG, "Error adding floating button to window manager", e);
            isWidgetVisible = false;
            // ممکن است نیاز به درخواست مجوز TYPE_APPLICATION_OVERLAY باشد
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "لطفا مجوز نمایش روی برنامه های دیگر را فعال کنید.", Toast.LENGTH_LONG).show();
                // می توانید کاربر را به صفحه تنظیمات هدایت کنید
                // Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                // startActivity(intent);
            }
        }


        rootView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // فقط در صورتی لمس را پردازش کن که ویجت قابل مشاهده باشد
                if (!isWidgetVisible) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = rootParams.x;
                        initialY = rootParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true; // ACTION_DOWN باید true برگرداند تا رویدادهای بعدی دریافت شوند
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        // اگر حرکت قابل توجه بود، آن را به عنوان move در نظر بگیر
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        rootParams.x = initialX + dx;
                        rootParams.y = initialY + dy;
                        // محدود کردن موقعیت دکمه در صفحه (اختیاری)
                        // rootParams.x = Math.max(0, Math.min(rootParams.x, windowManager.getDefaultDisplay().getWidth() - rootView.getWidth()));
                        // rootParams.y = Math.max(0, Math.min(rootParams.y, windowManager.getDefaultDisplay().getHeight() - rootView.getHeight()));
                        windowManager.updateViewLayout(rootView, rootParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // اگر دکمه جابجا نشده بود (کلیک کوتاه)، منو را باز/بسته کن
                        if (!moved) {
                            toggleMenu(rootParams);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // تابع برای حذف ویجت از صفحه
    private void removeFloatingButton() {
        Log.d(TAG, "Removing floating button.");
        if (rootView != null && windowManager != null && rootView.isAttachedToWindow()) {
            try {
                windowManager.removeView(rootView);
                rootView = null; // ریست کردن مرجع
                isWidgetVisible = false; // وضعیت ویجت را غیرفعال می کنیم
            } catch (Exception e) {
                Log.e(TAG, "Error removing floating button from window manager", e);
            }
        }
    }

    // ---------- اصلاح: نمایش اجباری دکمه‌ی شناور ----------
    // تابع refreshOverlay را حذف می کنیم، چون removeFloatingButton و addFloatingButton
    // کار را انجام می دهند. اگر نیاز به refresh بود، این دو تابع را فراخوانی می کنیم.

    private void toggleMenu(WindowManager.LayoutParams anchorParams) {
        if (!isWidgetVisible) return; // اگر ویجت مخفی است، کاری نکن
        if (menuOpen) {
            closeMenu();
        } else {
            openMenu(anchorParams);
        }
    }

    private void openMenu(WindowManager.LayoutParams anchorParams) {
        if (menuView != null && menuView.isAttachedToWindow()) {
            // اگر منو از قبل باز است، آن را نبند، فقط پارامترها را تنظیم کن
            // یا اینجا منطق دیگری پیاده کن
            return;
        }
        Log.d(TAG, "Opening menu...");

        menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#DD222222")); // رنگ پس زمینه تیره
        bg.setCornerRadius(24); // گوشه های گرد
        menuView.setBackground(bg);
        menuView.setPadding(16, 16, 16, 16);

        // آیتم های منو
        addMenuItem(menuView, "\uD83D\uDCF8  عکس از صفحه", () -> requestCapture(ScreenCaptureService.ACTION_SCREENSHOT));
        addMenuItem(menuView, "\u23FA  شروع ضبط", () -> requestCapture(ScreenCaptureService.ACTION_START));
        addMenuItem(menuView, "\u23F9  توقف ضبط", this::stopRecordingDirect);
        addMenuItem(menuView, "\u2715  بستن شناور", () -> {
            // قبل از بستن منو، سرویس را متوقف می کنیم
            stopSelf();
        });

        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        // موقعیت منو نسبت به دکمه اصلی
        menuParams.x = anchorParams.x + 140; // کمی جابجایی به راست
        menuParams.y = anchorParams.y;

        try {
            windowManager.addView(menuView, menuParams);
            menuOpen = true;
        } catch (Exception e) {
            Log.e(TAG, "Error adding menu view to window manager", e);
        }
    }

    private void closeMenu() {
        Log.d(TAG, "Closing menu...");
        if (menuView != null && menuView.isAttachedToWindow()) {
            try {
                windowManager.removeView(menuView);
                menuView = null; // ریست کردن مرجع
            } catch (Exception ignored) {
                Log.w(TAG, "Menu view was already removed or not attached.");
            }
        }
        menuOpen = false;
    }

    // Interface برای اجرای عملیات منو
    private interface MenuAction {
        void run();
    }

    private void addMenuItem(LinearLayout parent, String label, MenuAction action) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextColor(Color.WHITE);
        item.setTextSize(16);
        item.setPadding(20, 20, 20, 20);
        item.setOnClickListener(v -> {
            action.run(); // اجرای عملیات مورد نظر
            // بعد از انتخاب آیتم، منو را می بندیم
            closeMenu();
        });
        parent.addView(item);
    }

    private void requestCapture(String action) {
        try {
            // فرض می کنیم CaptureRequestActivity در همان پکیج است
            Intent intent = new Intent(this, CaptureRequestActivity.class);
            intent.putExtra(CaptureRequestActivity.EXTRA_ACTION, action);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);

            // پس از اینکه کاربر مجوز را داد یا رد کرد و Activity بسته شد،
            // ممکن است Overlay ناپدید شده باشد.
            // با یک تاخیر کوتاه، وضعیت ویجت را دوباره چک می کنیم.
            // این تاخیر بستگی به سرعت گوشی و سیستم عامل دارد.
            handler.postDelayed(this::refreshWidgetAfterCaptureActivity, 2000); // 2 ثانیه تاخیر

        } catch (Exception e) {
            Log.e(TAG, "Error starting CaptureRequestActivity", e);
        }
    }

    // تابعی برای اطمینان از نمایش مجدد ویجت پس از بستن پنجره مجوز
    private void refreshWidgetAfterCaptureActivity() {
        Log.d(TAG, "Checking widget visibility after CaptureRequestActivity...");
        // ابتدا وضعیت اینترنت را دوباره چک می کنیم
        // NetworkMonitor.startMonitoring(this); // اگر لازم است دوباره شروع کنید
        isInternetConnected = NetworkMonitor.getIsConnected(); // خواندن آخرین وضعیت

        // سپس بر اساس وضعیت اینترنت، ویجت را نمایش یا مخفی می کنیم
        updateWidgetVisibility();
    }


    private void stopRecordingDirect() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(stopIntent);
        Toast.makeText(this, "ضبط متوقف شد", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand...");

        // اینجا می توانید دستوراتی را از Intent پردازش کنید
        // مثلا اگر کاربر روی اعلان کلیک کرد و برنامه باز شد،
        // یا اگر دکمه "باز کردن شناور" در برنامه اصلی فشرده شد.

        // اگر intent حاوی دستوری برای نمایش ویجت است (مثلا از دکمه در UI برنامه)
        if (intent != null && intent.getAction() != null && intent.getAction().equals("ACTION_SHOW_FLOATING_WIDGET")) {
            Log.d(TAG, "Received ACTION_SHOW_FLOATING_WIDGET");
            // اطمینان از اتصال اینترنت قبل از نمایش
            if (NetworkMonitor.getIsConnected()) {
                if (!isWidgetVisible) {
                    addFloatingButton();
                    isWidgetVisible = true;
                }
            } else {
                Log.d(TAG, "Cannot show widget, internet is not connected.");
                Toast.makeText(this, "نمی‌توان شناور را فعال کرد، اینترنت متصل نیست.", Toast.LENGTH_SHORT).show();
            }
        } else if (intent != null && intent.getAction() != null && intent.getAction().equals("ACTION_HIDE_FLOATING_WIDGET")) {
            Log.d(TAG, "Received ACTION_HIDE_FLOATING_WIDGET");
            if (isWidgetVisible) {
                removeFloatingButton();
                isWidgetVisible = false;
            }
        }

        // START_STICKY یعنی اگر سیستم سرویس را به دلیل کمبود حافظه کشت،
        // سعی کند آن را دوباره راه اندازی کند.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy...");
        handler.removeCallbacksAndMessages(null); // پاک کردن تمام callback های handler

        // *** عدم ثبت BroadcastReceiver ***
        try {
            unregisterReceiver(networkStatusReceiver);
            Log.d(TAG, "Network status receiver unregistered.");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Network status receiver was not registered or already unregistered.", e);
        }

        // پاکسازی ویجت و منو از صفحه
        removeFloatingButton();
        closeMenu();

        // سرویس foreground را هم متوقف می کنیم
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // چون این سرویس یک Service از نوع Foreground است و با bind کار نمی کند، null برمی گرداند
        return null;
    }
}

// --- کدی که باید در کلاس NetworkMonitor.java داشته باشید (اگر ندارید) ---
/*
package org.example.screenrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    // تعریف اکشن و کلید برای broadcast (باید با FloatingWidgetService یکی باشد)
    public static final String NETWORK_STATUS_ACTION = "com.example.screenrecorder.NETWORK_STATUS_CHANGED";
    public static final String EXTRA_IS_CONNECTED = "is_connected";

    private static volatile boolean isConnected = false;
    private static volatile boolean lastKnownConnectionState = false;
    private static ConnectivityManager.NetworkCallback networkCallback;
    private static Context appContext; // برای ثبت/عدم ثبت receiver و ارسال broadcast

    public static void startMonitoring(Context context) {
        Log.d(TAG, "Starting network monitoring...");
        appContext = context.getApplicationContext();

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                Log.e(TAG, "ConnectivityManager is null.");
                return;
            }

            // بررسی وضعیت اولیه
            checkCurrentNetwork(cm);
            lastKnownConnectionState = isConnected;
            Log.d(TAG, "Initial connection state: " + isConnected);
            sendStatusBroadcast(appContext, isConnected); // ارسال وضعیت اولیه

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) // مهم برای اطمینان از اتصال معتبر
                    .build();

            if (networkCallback == null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                        super.onCapabilitiesChanged(network, capabilities);
                        Log.d(TAG, "Network capabilities changed for network " + network);

                        if (capabilities != null) {
                            boolean hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                            boolean isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                            boolean currentConnectionState = hasInternetCapability && isValidated;

                            updateConnectionStatus(currentConnectionState);
                        } else {
                            Log.w(TAG, "NetworkCapabilities is null.");
                            updateConnectionStatus(false); // اگر capabilities null بود، یعنی معتبر نیست
                        }
                    }

                    @Override
                    public void onLost(Network network) {
                        super.onLost(network);
                        Log.d(TAG, "Network lost: " + network);
                        updateConnectionStatus(false); // شبکه از دست رفت، پس قطع است
                    }
                };
            }

            cm.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "NetworkCallback registered.");

        } catch (Exception e) {
            Log.e(TAG, "Error during network monitoring setup: ", e);
            updateConnectionStatus(false); // در صورت خطا، قطع فرض شود
        }
    }

    private static synchronized void updateConnectionStatus(boolean newStatus) {
        if (newStatus != lastKnownConnectionState) {
            isConnected = newStatus;
            lastKnownConnectionState = newStatus;
            Log.d(TAG, "Connection status updated to: " + isConnected);
            if (appContext != null) {
                sendStatusBroadcast(appContext, isConnected);
            } else {
                Log.w(TAG, "AppContext is null, cannot send status broadcast.");
            }
        }
    }

    private static void sendStatusBroadcast(Context context, boolean isConnected) {
        Intent intent = new Intent(NETWORK_STATUS_ACTION);
        intent.putExtra(EXTRA_IS_CONNECTED, isConnected);
        context.sendBroadcast(intent);
        Log.d(TAG, "Sent network status broadcast: " + isConnected);
    }

    public static void stopMonitoring(Context context) {
        Log.d(TAG, "Stopping network monitoring...");
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback != null) {
                cm.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
                Log.d(TAG, "NetworkCallback unregistered.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping network monitoring: ", e);
        }
        appContext = null; // پاک کردن context
    }

    // چک کردن وضعیت فعلی شبکه (برای زمان شروع)
    public static void checkCurrentNetwork(ConnectivityManager cm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                    isConnected = (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                } else {
                    isConnected = false;
                }
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                isConnected = (netInfo != null && netInfo.isConnected());
            }
            lastKnownConnectionState = isConnected; // وضعیت اولیه را هم ثبت کن
        } catch (Exception e) {
            Log.e(TAG, "Error checking current network: ", e);
            isConnected = false;
            lastKnownConnectionState = false;
        }
    }

    public static boolean getIsConnected() {
        // Log.d(TAG, "getIsConnected() called. Returning: " + isConnected); // ممکن است لاگ زیاد شود
        return isConnected;
    }
}
*/
