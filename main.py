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

if platform == "android":
    from android import activity as android_activity
    from jnius import autoclass, cast

    PythonActivity = autoclass("org.kivy.android.PythonActivity")
    Intent = autoclass("android.content.Intent")
    Context = autoclass("android.content.Context")
    BuildVersion = autoclass('android.os.Build$VERSION')

    SERVICE_CLASS = "org.example.screenrecorder.ScreenCaptureService"
    ACTION_START = "org.example.screenrecorder.START"
    ACTION_SCREENSHOT = "org.example.screenrecorder.SCREENSHOT"
    ACTION_STOP = "org.example.screenrecorder.STOP"

# کدهای درخواست جدا برای ضبط و اسکرین‌شات، چون هر توکن MediaProjection
# فقط یک‌بار قابل استفاده است
REQUEST_RECORD = 1001
REQUEST_SCREENSHOT = 1002


def ftext(text):
    """اصلاح چسبندگی حروف، معکوس‌سازی و اصلاح هوشمند جابه‌جایی پرانتزها"""
    if not text:
        return ""
    reshaped_text = arabic_reshaper.reshape(text)
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


if platform == "android":
    FONT_FILE = "fonts/Vazirmatn-Light.ttf"
else:
    # مسیر هاردکد ویندوزی قبلی روی لینوکس/مک کرش می‌کرد؛
    # حالا چند مسیر رایج چک می‌شه و اگه هیچ‌کدوم نبود، فونت پیش‌فرض کیوی استفاده می‌شه
    _candidates = [
        "C:\\Windows\\Fonts\\arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    FONT_FILE = next((p for p in _candidates if os.path.exists(p)), None)

if FONT_FILE:
    LabelBase.register(name="PersianFont", fn_regular=FONT_FILE)

_FONT_NAME = "PersianFont" if FONT_FILE else "Roboto"


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
        self.pending_action = None  # "record" یا "screenshot"

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
            android_activity.bind(on_activity_result=self.on_activity_result)
            self._request_runtime_permissions()

        return layout

    # ---------- مجوزهای زمان اجرا ----------

    def _request_runtime_permissions(self):
        try:
            from android.permissions import request_permissions, Permission
            perms = [Permission.FOREGROUND_SERVICE, Permission.RECORD_AUDIO]
            if BuildVersion.SDK_INT >= 33:
                perms.append(Permission.POST_NOTIFICATIONS)
            request_permissions(perms)
        except Exception as e:
            print(f"permission request failed: {e}")

    # ---------- درخواست مجوز MediaProjection ----------

    def _request_capture(self, action, request_code):
        if platform != "android":
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

        if result_code != -1:  # Activity.RESULT_OK == -1
            self.status_label.text = ftext("مجوز رد شد")
            self.pending_action = None
            return

        action = ACTION_START if request_code == REQUEST_RECORD else ACTION_SCREENSHOT
        self.status_label.text = ftext("مجوز گرفته شد...")
        # نکته مهم نسخه قبلی: اینجا result_code واقعی پاس داده می‌شه، نه -1 هاردکد
        self._start_service(action, result_code, data)

    def _start_service(self, action, result_code, data):
        try:
            activity = PythonActivity.mActivity
            service_intent = Intent(activity, autoclass(SERVICE_CLASS))
            service_intent.setAction(action)
            service_intent.putExtra("resultCode", result_code)
            service_intent.putExtra("data", cast('android.os.Parcelable', data))

            if BuildVersion.SDK_INT >= 26:
                activity.startForegroundService(service_intent)
            else:
                activity.startService(service_intent)

            if action == ACTION_START:
                self.status_label.text = ftext("در حال ضبط...")
            else:
                self.status_label.text = ftext("در حال گرفتن عکس...")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در شروع سرویس: {e}")

    def stop_recording(self, instance):
        if platform != "android":
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
