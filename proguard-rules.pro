# --- قوانین Proguard/R8 برای این پروژه ---

# NetworkMonitor از پایتون با نام دقیق صدا زده می‌شود
-keep class org.example.screenrecorder.NetworkMonitor {
    public static boolean isConnected;
    public static boolean getIsConnected();
    public static void startMonitoring(android.content.Context);
}

# کامپوننت‌هایی که در AndroidManifest.xml با نام کامل ثبت شده‌اند
-keep public class org.example.screenrecorder.ScreenCaptureService { *; }
-keep public class org.example.screenrecorder.FloatingWidgetService { *; }
-keep public class org.example.screenrecorder.CaptureRequestActivity { *; }

# جلوگیری از خراب شدن منطق MediaProjection و نتیجه مجوز
-keepclassmembers class org.example.screenrecorder.** {
    public *;
    protected *;
}

# نگه داشتن متدهای مهم مربوط به Intent و نتیجه اکتیویتی
-keepclassmembers class * {
    public void onActivityResult(int, int, android.content.Intent);
}

# اجزای هسته‌ای Kivy/SDL2
-keep class org.kivy.android.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.renpy.android.** { *; }

-dontwarn org.kivy.android.**
-dontwarn org.libsdl.app.**
-dontwarn org.renpy.android.**
