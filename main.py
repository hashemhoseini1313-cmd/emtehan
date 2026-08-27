# -*- coding: utf-8 -*-

import traceback

try:
    import os
    import re
    from kivy.app import App
    from kivy.uix.boxlayout import BoxLayout
    from kivy.uix.button import Button
    from kivy.uix.label import Label
    from kivy.core.text import LabelBase
    from kivy.utils import platform

    import arabic_reshaper

    # ---------- متغیرهای اندروید ----------
    android_activity = None
    autoclass = None
    cast = None
    PythonActivity = None
    Intent = None
    Context = None
    BuildVersion = None

    SERVICE_CLASS = "org.example.screenrecorder.ScreenCaptureService"
    ACTION_START = "org.example.screenrecorder.START"
    ACTION_SCREENSHOT = "org.example.screenrecorder.SCREENSHOT"
    ACTION_STOP = "org.example.screenrecorder.STOP"

    REQUEST_RECORD = 1001
    REQUEST_SCREENSHOT = 1002

    if platform == "android":
        try:
            from android import activity as android_activity
            from jnius import autoclass, cast

            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            Intent = autoclass("android.content.Intent")
            Context = autoclass("android.content.Context")
            BuildVersion = autoclass('android.os.Build$VERSION')
        except Exception as e:
            print(f"Android init failed: {e}")

    # بقیه کد...
    # (بقیه کدها همان باشد)

    class ScreenRecorderApp(App):
        # ...
        # تمام کلاس‌ها و توابع

    if __name__ == "__main__":
        ScreenRecorderApp().run()

except Exception:
    error_msg = traceback.format_exc()
    # ذخیره در فایل
    try:
        with open("error_log.txt", "w") as f:
            f.write(error_msg)
    except:
        pass
    # چاپ در logcat
    print(error_msg)
