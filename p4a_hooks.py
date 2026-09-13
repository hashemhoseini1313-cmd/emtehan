from pathlib import Path
import shutil

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
        android:theme="@android:style/Theme.NoDisplay"
        android:excludeFromRecents="true"
        android:launchMode="singleInstance"
        android:process=":capture"
        android:exported="false" />
"""

PERMISSIONS_XML = """
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
    # ---------- اضافه‌کردن Proguard/R8 برای مبهم‌سازی کد جاوا ----------
    dist_dir = Path(toolchain._dist.dist_dir)
    build_gradle_file = dist_dir / "build.gradle"
    proguard_target = dist_dir / "proguard-rules.pro"
    proguard_source = Path(__file__).parent / "proguard-rules.pro"

    try:
        if proguard_source.exists():
            shutil.copy(str(proguard_source), str(proguard_target))
            print("[hook] proguard-rules.pro کپی شد")
        else:
            print("[hook] هشدار: proguard-rules.pro در ریشه‌ی پروژه پیدا نشد")

        if build_gradle_file.exists():
            content = build_gradle_file.read_text(encoding="utf-8")

            if "minifyEnabled true" not in content:
                marker = "buildTypes {"
                if marker in content:
                    injection = (
                        "buildTypes {\n"
                        "        debug {\n"
                        "            minifyEnabled true\n"
                        "            shrinkResources false\n"
                        "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n"
                        "        }\n"
                    )
                    content = content.replace(marker, injection, 1)
                    build_gradle_file.write_text(content, encoding="utf-8")
                    print("[hook] minifyEnabled و proguardFiles به build.gradle اضافه شد")
                else:
                    print("[hook] هشدار: بخش buildTypes در build.gradle پیدا نشد")
            else:
                print("[hook] minifyEnabled از قبل بود، رد شد")
        else:
            print("[hook] هشدار: build.gradle پیدا نشد")
    except Exception as e:
        print(f"[hook] خطا در تنظیم Proguard: {e}")


def after_apk_build(toolchain):
    manifest_file = Path(toolchain._dist.dist_dir) / "src" / "main" / "AndroidManifest.xml"
    manifest = manifest_file.read_text(encoding="utf-8")

    if "ScreenCaptureService" not in manifest:
        manifest = manifest.replace("</application>", f"{SERVICE_XML}\n</application>")
        print("[hook] سرویس‌ها به AndroidManifest.xml اضافه شدن")
    else:
        print("[hook] سرویس‌ها از قبل توی منیفست بودن، رد شد")

    if "FOREGROUND_SERVICE_MEDIA_PROJECTION" not in manifest:
        manifest = manifest.replace("</manifest>", f"{PERMISSIONS_XML}\n</manifest>")
        print("[hook] مجوزهای اضافی به AndroidManifest.xml اضافه شدن")
    else:
        print("[hook] مجوزها از قبل توی منیفست بودن، رد شد")

    manifest_file.write_text(manifest, encoding="utf-8")
