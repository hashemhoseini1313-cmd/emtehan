# --- قوانین Proguard/R8 برای این پروژه ---

# تست: تمام کلاس‌ها و اعضای Java پروژه بدون تغییر نگه داشته شوند
-keep class org.example.screenrecorder.** { *; }

# اجزای هسته‌ای Kivy/SDL2
-keep class org.kivy.android.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.renpy.android.** { *; }

-dontwarn org.kivy.android.**
-dontwarn org.libsdl.app.**
