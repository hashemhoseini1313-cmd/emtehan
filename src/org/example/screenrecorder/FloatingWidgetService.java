package org.example.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingWidgetService extends Service {

    private static final String TAG = "FloatingWidgetService";
    private static final String CHANNEL_ID = "floating_widget_channel";
    private static final int NOTIFICATION_ID = 2;

    private WindowManager windowManager;
    private LinearLayout rootView;
    private LinearLayout menuView;
    private boolean menuOpen = false;
    private WindowManager.LayoutParams rootParams;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        addFloatingButton();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Floating Button", NotificationManager.IMPORTANCE_LOW);
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
                .setContentTitle("دکمه شناور فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
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
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);

        TextView mainButton = new TextView(this);
        mainButton.setText("\u2699");
        mainButton.setTextSize(24);
        mainButton.setTextColor(Color.WHITE);
        mainButton.setGravity(Gravity.CENTER);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#CC2196F3"));
        mainButton.setBackground(circle);
        mainButton.setPadding(30, 30, 30, 30);

        rootView.addView(mainButton);

        rootParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        rootParams.gravity = Gravity.TOP | Gravity.START;
        rootParams.x = 0;
        rootParams.y = 300;

        windowManager.addView(rootView, rootParams);

        rootView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = rootParams.x;
                        initialY = rootParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        rootParams.x = initialX + dx;
                        rootParams.y = initialY + dy;
                        windowManager.updateViewLayout(rootView, rootParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            toggleMenu(rootParams);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // ---------- اصلاح: نمایش اجباری دکمه‌ی شناور ----------
    // اندروید هنگام نمایش پاپ‌آپ حساس (مثل مجوز MediaProjection)، به‌طور خودکار
    // پنجره‌های شناور (Overlay) را مخفی می‌کند تا از حملات tapjacking جلوگیری شود.
    // روی برخی گوشی‌ها (مثل سامسونگ) این پنجره بعد از بسته‌شدن پاپ‌آپ خودکار
    // برنمی‌گردد، پس دستی حذف و دوباره اضافه‌اش می‌کنیم تا مطمئن شویم نمایش داده می‌شود.
    private void refreshOverlay() {
        try {
            if (rootView != null && windowManager != null) {
                windowManager.removeView(rootView);
                windowManager.addView(rootView, rootParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در بازسازی دکمه شناور", e);
        }
    }

    private void toggleMenu(WindowManager.LayoutParams anchorParams) {
        if (menuOpen) {
            closeMenu();
        } else {
            openMenu(anchorParams);
        }
    }

    private void openMenu(WindowManager.LayoutParams anchorParams) {
        menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#DD222222"));
        bg.setCornerRadius(24);
        menuView.setBackground(bg);
        menuView.setPadding(16, 16, 16, 16);

        addMenuItem(menuView, "\uD83D\uDCF8  عکس از صفحه", () -> requestCapture(ScreenCaptureService.ACTION_SCREENSHOT));
        addMenuItem(menuView, "\u23FA  شروع ضبط", () -> requestCapture(ScreenCaptureService.ACTION_START));
        addMenuItem(menuView, "\u23F9  توقف ضبط", this::stopRecordingDirect);
        addMenuItem(menuView, "\u2715  بستن دکمه شناور", () -> {
            closeMenu();
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
        menuParams.x = anchorParams.x + 140;
        menuParams.y = anchorParams.y;

        windowManager.addView(menuView, menuParams);
        menuOpen = true;
    }

    private void closeMenu() {
        if (menuView != null) {
            try {
                windowManager.removeView(menuView);
            } catch (Exception ignored) {
            }
            menuView = null;
        }
        menuOpen = false;
    }

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
            action.run();
            closeMenu();
        });
        parent.addView(item);
    }

    private void requestCapture(String action) {
        try {
            Intent intent = new Intent(this, CaptureRequestActivity.class);
            intent.putExtra(CaptureRequestActivity.EXTRA_ACTION, action);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);

            // بعد از بسته‌شدن پاپ‌آپ مجوز، دکمه‌ی شناور را دستی دوباره نمایش می‌دهیم
            handler.postDelayed(this::refreshOverlay, 1500);
        } catch (Exception e) {
            Log.e(TAG, "خطا در باز کردن CaptureRequestActivity", e);
        }
    }

    private void stopRecordingDirect() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(stopIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        closeMenu();
        if (rootView != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    }
