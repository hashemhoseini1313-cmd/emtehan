# -*- coding: utf-8 -*-

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


def ftext(text):
    if not text:
        return ""
    try:
        reshaped_text = arabic_reshaper.reshape(text)
    except Exception:
        reshaped_text = text
    # تعویض پرانتزها و معکوس‌سازی ساده
    swapped = []
    for char in reshaped_text:
        if char == '(':
            swapped.append(')')
        elif char == ')':
            swapped.append('(')
        else:
            swapped.append(char)
    temp_text = "".join(swapped)
    reversed_text = temp_text[::-1]
    return re.sub(r'\d+', lambda m: m.group(0)[::-1], reversed_text)


# ---------- غیرفعال کردن موقت فونت فارسی ----------
# برای جلوگیری از کرش ناشی از فونت، از فونت پیش‌فرض استفاده می‌کنیم.
FONT_FILE = None
_FONT_NAME = "Roboto"


class PersianLabel(Label):
    def __init__(self, **kwargs):
        if "text" in kwargs:
            kwargs["text"] = ftext(kwargs["text"])
        kwargs.setdefault("font_name", _FONT_NAME)
        kwargs.setdefault("halign", "right")
        kwargs.setdefault("text_size", (None, None))
        super().__init__(**kwargs)

    def on_size(self, *args):
        self.text_size = (self.width, None)


class PersianButton(Button):
    def __init__(self, **kwargs):
        if "text" in kwargs:
            kwargs["text"] = ftext(kwargs["text"])
        kwargs.setdefault("font_name", _FONT_NAME)
        kwargs.setdefault("halign", "center")
        super().__init__(**kwargs)


class ScreenRecorderApp(App):
    def build(self):
        self.pending_action = None
        self.status_label = PersianLabel(text="آماده", font_size="16sp")

        layout = BoxLayout(orientation="vertical", padding=30, spacing=15)

        title = PersianLabel(text="ضبط صفحه گوشی (اندروید 15)", font_size="24sp")
        start_button = PersianButton(text="🎥 شروع ضبط صفحه", font_size="18sp", size_hint_y=None, height=65)
        stop_button = PersianButton(text="⏹ توقف ضبط", font_size="18sp", size_hint_y=None, height=65)
        photo_button = PersianButton(text="📸 عکس از صفحه", font_size="18sp", size_hint_y=None, height=65)

        start_button.bind(on_press=self.start_recording)
        stop_button.bind(on_press=self.stop_recording)
        photo_button.bind(on_press=self.take_screenshot)

        layout.add_widget(title)
        layout.add_widget(self.status_label)
        layout.add_widget(start_button)
        layout.add_widget(stop_button)
        layout.add_widget(photo_button)

        if platform == "android":
            try:
                android_activity.bind(on_activity_result=self.on_activity_result)
            except Exception as e:
                print(f"bind activity failed: {e}")

            # درخواست مجوزها را موقتاً غیرفعال کردیم تا ببینیم کرش از کجاست
            # self._request_runtime_permissions()

        return layout

    def _request_runtime_permissions(self):
        try:
            from android.permissions import request_permissions, Permission
            perms = [Permission.FOREGROUND_SERVICE, Permission.RECORD_AUDIO]
            if BuildVersion is not None and BuildVersion.SDK_INT >= 33:
                perms.append(Permission.POST_NOTIFICATIONS)
            request_permissions(perms)
        except Exception as e:
            print(f"permission request failed: {e}")

    def _request_capture(self, action, request_code):
        if platform != "android" or PythonActivity is None or autoclass is None:
            self.status_label.text = ftext("فقط روی اندروید")
            return
        try:
            self.pending_action = action
            activity = PythonActivity.mActivity
            MediaProjectionManager = autoclass("android.media.projection.MediaProjectionManager")
            projection_service = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            mgr = cast(MediaProjectionManager, projection_service)
            intent = mgr.createScreenCaptureIntent()
            activity.startActivityForResult(intent, request_code)
            self.status_label.text = ftext("منتظر تأیید مجوز...")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در درخواست مجوز: {e}")

    def start_recording(self, instance):
        self._request_capture("record", REQUEST_RECORD)

    def take_screenshot(self, instance):
        self._request_capture("screenshot", REQUEST_SCREENSHOT)

    def on_activity_result(self, request_code, result_code, data):
        if request_code not in (REQUEST_RECORD, REQUEST_SCREENSHOT):
            return
        if result_code != -1:
            self.status_label.text = ftext("مجوز رد شد")
            self.pending_action = None
            return

        action = ACTION_START if request_code == REQUEST_RECORD else ACTION_SCREENSHOT
        self.status_label.text = ftext("مجوز گرفته شد...")
        self._start_service(action, result_code, data)

    def _start_service(self, action, result_code, data):
        if PythonActivity is None or autoclass is None or cast is None:
            self.status_label.text = ftext("Android init failed")
            return
        try:
            activity = PythonActivity.mActivity
            service_intent = Intent(activity, autoclass(SERVICE_CLASS))
            service_intent.setAction(action)
            service_intent.putExtra("resultCode", result_code)
            service_intent.putExtra("data", cast('android.os.Parcelable', data))

            if BuildVersion is not None and BuildVersion.SDK_INT >= 26:
                activity.startForegroundService(service_intent)
            else:
                activity.startService(service_intent)

            self.status_label.text = ftext("در حال ضبط..." if action == ACTION_START else "در حال گرفتن عکس...")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در شروع سرویس: {e}")

    def stop_recording(self, instance):
        if platform != "android" or PythonActivity is None or Intent is None or autoclass is None:
            return
        try:
            activity = PythonActivity.mActivity
            service_intent = Intent(activity, autoclass(SERVICE_CLASS))
            service_intent.setAction(ACTION_STOP)
            activity.startService(service_intent)
            self.status_label.text = ftext("ضبط متوقف شد")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در توقف سرویس: {e}")


if __name__ == "__main__":
    ScreenRecorderApp().run()
