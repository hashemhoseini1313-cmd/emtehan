import os
import shutil
from pathlib import Path

SERVICE_XML = """
    <service
        android:name="org.example.screenrecorder.ScreenCaptureService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="mediaProjection|microphone" />
    <service
        android:name="org.example.screenrecorder.FloatingWidgetService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="specialUse">
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="floating_control_button" />
    </service>
    <activity
        android:name="org.example.screenrecorder.CaptureRequestActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:excludeFromRecents="true"
        android:launchMode="singleInstance"
        android:process=":capture"
        android:exported="false" />
"""

PERMISSIONS_XML = """
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
"""


def before_apk_build(toolchain):
    # ۱. کپی کردن فایل‌های جاوا از پوشه java_src به سورس اندروید
    try:
        src_dir = os.path.join(os.getcwd(), "java_src")
        dest_dir = Path(toolchain._dist.dist_dir) / "src" / "main" / "java" / "org" / "example" / "screenrecorder"
        
        if os.path.exists(src_dir):
            os.makedirs(dest_dir, exist_ok=True)
            for f in os.listdir(src_dir):
                if f.endswith(".java"):
                    shutil.copy2(os.path.join(src_dir, f), dest_dir / f)
                    print(f"[hook] فایل جاوا کپی شد: {f}")
        else:
            print("[hook] پوشه java_src یافت نشد!")
    except Exception as e:
        print(f"[hook] خطا در کپی فایل‌های جاوا: {e}")

    # ۲. اصلاح AndroidManifest.xml پیش از شروع کامپایل Gradle
    try:
        manifest_file = Path(toolchain._dist.dist_dir) / "src" / "main" / "AndroidManifest.xml"
        if manifest_file.exists():
            manifest = manifest_file.read_text(encoding="utf-8")

            # تزریق سرویس‌ها و اکتیویتی به داخل تگ application
            if "ScreenCaptureService" not in manifest:
                manifest = manifest.replace("</application>", f"{SERVICE_XML}\n</application>")
                print("[hook] سرویس‌ها به AndroidManifest.xml اضافه شدند")
            else:
                print("[hook] سرویس‌ها از قبل در مانیفست بودند، رد شد")

            # تزریق مجوزها درست قبل از تگ شروع application
            if "FOREGROUND_SERVICE_MEDIA_PROJECTION" not in manifest:
                manifest = manifest.replace("<application", f"{PERMISSIONS_XML}\n<application")
                print("[hook] مجوزهای اضافی به AndroidManifest.xml اضافه شدند")
            else:
                print("[hook] مجوزها از قبل در مانیفست بودند، رد شد")

            manifest_file.write_text(manifest, encoding="utf-8")
        else:
            print("[hook] فایل AndroidManifest.xml یافت نشد!")
    except Exception as e:
        print(f"[hook] خطا در اصلاح مانیفست: {e}")


def after_apk_build(toolchain):
    pass
