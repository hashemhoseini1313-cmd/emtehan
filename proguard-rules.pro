# --- قوانین Proguard/R8 برای این پروژه ---

# NetworkMonitor از پایتون و جاوا با نام دقیق صدا زده می‌شود
-keep class org.example.screenrecorder.NetworkMonitor { *; }

# کامپوننت‌هایی که در AndroidManifest.xml ثبت شده‌اند — باید همه‌ی متدها
# (چرخه‌ی حیات مثل onCreate/onResume/onActivityResult/onNewIntent) دست‌نخورده بمانند،
# چون توسط خود سیستم‌عامل اندروید صدا زده می‌شوند، نه از داخل کد اپ.
-keep class org.example.screenrecorder.ScreenCaptureService { *; }
-keep class org.example.screenrecorder.FloatingWidgetService { *; }
-keep class org.example.screenrecorder.CaptureRequestActivity { *; }

# اجزای هسته‌ای Kivy/SDL2 که از طریق کد native فراخوانی می‌شوند
-keep class org.kivy.android.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.renpy.android.** { *; }

-dontwarn org.kivy.android.**
-dontwarn org.libsdl.app.**
