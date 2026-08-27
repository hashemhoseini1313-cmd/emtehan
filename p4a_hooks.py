from pathlib import Path
from pythonforandroid.toolchain import ToolchainCL

# اندروید 14+ بدون این تگ توی منیفست، سرویس رو موقع startForeground کرش می‌ده.
# چون از p4a مستقیم استفاده می‌کنیم (نه buildozer)، این تگ رو با یه hook تزریق می‌کنیم.
SERVICE_XML = """
    <service
        android:name="org.example.screenrecorder.ScreenCaptureService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="mediaProjection" />
"""

# مجوزهایی که نیاز به maxSdkVersion دارن رو نمیشه با فلگ --permission ساخت،
# پس اینجا تزریق می‌شن تا یه منبع واحد و هماهنگ با main.py و ScreenCaptureService.java باشن.
# بقیه مجوزهای ساده (INTERNET, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO) همچنان از
# فلگ‌های --permission توی ورک‌فلو میان، پس اینجا تکرار نمی‌شن.
PERMISSIONS_XML = """
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
"""


@ToolchainCL.after_apk_build
def after_apk_build(toolchain: ToolchainCL):
    manifest_file = Path(toolchain._dist.dist_dir) / "src" / "main" / "AndroidManifest.xml"
    manifest = manifest_file.read_text(encoding="utf-8")

    if "ScreenCaptureService" not in manifest:
        manifest = manifest.replace("</application>", f"{SERVICE_XML}\n</application>")
        print("[hook] ScreenCaptureService به AndroidManifest.xml اضافه شد")
    else:
        print("[hook] ScreenCaptureService از قبل توی منیفست بود، رد شد")

    if "FOREGROUND_SERVICE_MEDIA_PROJECTION" not in manifest:
        manifest = manifest.replace("</manifest>", f"{PERMISSIONS_XML}\n</manifest>")
        print("[hook] مجوزهای اضافی به AndroidManifest.xml اضافه شدن")
    else:
        print("[hook] مجوزها از قبل توی منیفست بودن، رد شد")

    manifest_file.write_text(manifest, encoding="utf-8")
