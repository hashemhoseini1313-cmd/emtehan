# --- قوانین Proguard/R8 برای این پروژه ---

# تمام کلاس‌های پکیج خودمان را کامل نگه دار
-keep class org.example.screenrecorder.** { *; }
-keepclassmembers class org.example.screenrecorder.** { *; }

# NetworkMonitor از پایتون صدا زده می‌شود
-keep class org.example.screenrecorder.NetworkMonitor {
    public static boolean isConnected;
    public static boolean getIsConnected();
    public static void startMonitoring(android.content.Context);
}

# ثابت‌های اکشن و کلیدهای Intent نباید حذف یا تغییر نام داده شوند
-keepclassmembers class org.example.screenrecorder.ScreenCaptureService {
    public static final java.lang.String ACTION_START;
    public static final java.lang.String ACTION_SCREENSHOT;
    public static final java.lang.String ACTION_STOP;
}

-keepclassmembers class org.example.screenrecorder.CaptureRequestActivity {
    public static final java.lang.String EXTRA_ACTION;
}

# جلوگیری از خراب شدن MediaProjection و Intent
-keep class android.media.projection.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# اجزای Kivy / SDL2
-keep class org.kivy.android.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.renpy.android.** { *; }

-dontwarn org.kivy.android.**
-dontwarn org.libsdl.app.**
-dontwarn org.renpy.android.**
-dontwarn android.media.projection.**
