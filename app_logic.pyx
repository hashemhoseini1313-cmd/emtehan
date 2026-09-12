# app_logic.pyx
# -*- coding: utf-8 -*-

import traceback
import os
import re
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.widget import Widget
from kivy.core.text import LabelBase
from kivy.core.window import Window
from kivy.utils import platform
from kivy.clock import Clock, mainthread

import arabic_reshaper

android_activity = None
autoclass = None
cast = None
PythonJavaClass = None
java_method = None
PythonActivity = None
Intent = None
Context = None
BuildVersion = None
NetworkMonitor = None

SERVICE_CLASS = "org.example.screenrecorder.ScreenCaptureService"
FLOATING_SERVICE_CLASS = "org.example.screenrecorder.FloatingWidgetService"
NETWORK_MONITOR_CLASS = "org.example.screenrecorder.NetworkMonitor"
ACTION_START = "org.example.screenrecorder.START"
ACTION_SCREENSHOT = "org.example.screenrecorder.SCREENSHOT"
ACTION_STOP = "org.example.screenrecorder.STOP"

REQUEST_RECORD = 1001
REQUEST_SCREENSHOT = 1002

if platform == "android":
    try:
        from android import activity as android_activity
        from jnius import autoclass, cast, PythonJavaClass, java_method

        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        Intent = autoclass("android.content.Intent")
        Context = autoclass("android.content.Context")
        BuildVersion = autoclass('android.os.Build$VERSION')

        try:
            NetworkMonitor = autoclass(NETWORK_MONITOR_CLASS)
        except Exception:
            NetworkMonitor = None
    except Exception:
        pass

def ftext(text):
    if not text:
        return ""
    try:
        reshaped_text = arabic_reshaper.reshape(text)
    except Exception:
        reshaped_text = text
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

FONT_FILE = None
_FONT_NAME = "Roboto"

if platform == "android":
    candidate = "fonts/Vazirmatn-Light.ttf"
    if os.path.exists(candidate):
        try:
            LabelBase.register(name="PersianFont", fn_regular=candidate)
            FONT_FILE = candidate
            _FONT_NAME = "PersianFont"
        except Exception:
            _FONT_NAME = "Roboto"
    else:
        _FONT_NAME = "Roboto"
else:
    _FONT_NAME = "Roboto"

class PersianLabel(Label):
    def __init__(self, **kwargs):
        if "text" in kwargs:
            kwargs["text"] = ftext(kwargs["text"])
        kwargs.setdefault("font_name", _FONT_NAME)
        kwargs.setdefault("halign", "center")
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
        self.status_label = PersianLabel(text="آماده", font_size="18sp", size_hint_y=None, height=50)

        Window.clearcolor = (1.0, 0.45, 0.0, 1)

        layout = BoxLayout(orientation="vertical", padding=25, spacing=12)

        layout.add_widget(Widget(size_hint_y=0.28))

        title = PersianLabel(text="ثبت صفحه", font_size="28sp", size_hint_y=None, height=70)
        layout.add_widget(title)

        layout.add_widget(Widget(size_hint_y=0.22))

        layout.add_widget(self.status_label)

        layout.add_widget(Widget())

        top_row = BoxLayout(orientation="horizontal", spacing=15, size_hint_y=None, height=90)
        self.start_button = PersianButton(text="شروع ضبط", font_size="18sp")
        self.stop_button = PersianButton(text="توقف ضبط", font_size="18sp")
        top_row.add_widget(self.start_button)
        top_row.add_widget(self.stop_button)

        bottom_row = BoxLayout(orientation="horizontal", spacing=15, size_hint_y=None, height=90)
        self.photo_button = PersianButton(text="عکس از صفحه", font_size="18sp")
        self.floating_button = PersianButton(text="دکمه شناور", font_size="18sp")
        bottom_row.add_widget(self.photo_button)
        bottom_row.add_widget(self.floating_button)

        layout.add_widget(top_row)
        layout.add_widget(bottom_row)

        self.start_button.bind(on_press=self.start_recording)
        self.stop_button.bind(on_press=self.stop_recording)
        self.photo_button.bind(on_press=self.take_screenshot)
        self.floating_button.bind(on_press=self.open_floating_widget)

        if platform == "android":
            try:
                android_activity.bind(on_activity_result=self.on_activity_result)
            except Exception:
                pass

            self._request_runtime_permissions()
            self._register_network_callback()

        self._update_connectivity_ui()
        Clock.schedule_interval(self._update_connectivity_ui, 1)

        return layout

    def _register_network_callback(self):
        try:
            if NetworkMonitor is not None and PythonActivity is not None:
                activity = PythonActivity.mActivity
                if hasattr(NetworkMonitor, 'startMonitoring'):
                    NetworkMonitor.startMonitoring(activity)
        except Exception:
            pass

    def _is_connected(self):
        if platform != "android":
            return True
        try:
            if NetworkMonitor is not None:
                if hasattr(NetworkMonitor, 'getIsConnected'):
                    return bool(NetworkMonitor.getIsConnected())
                elif hasattr(NetworkMonitor, 'isConnected'):
                    return bool(NetworkMonitor.isConnected)
            return False
        except Exception:
            return False

    @mainthread
    def _update_connectivity_ui(self, *args):
        connected = self._is_connected()

        self.start_button.disabled = not connected
        self.stop_button.disabled = not connected
        self.photo_button.disabled = not connected
        self.floating_button.disabled = not connected

        if connected:
            self.status_label.text = ftext("آماده")
        else:
            self.status_label.text = ftext("اتصال اینترنت برقرار نیست")

        return connected

    def _request_runtime_permissions(self):
        try:
            from android.permissions import request_permissions, Permission
            perms = [Permission.FOREGROUND_SERVICE, Permission.RECORD_AUDIO]
            if BuildVersion is not None and BuildVersion.SDK_INT >= 33:
                perms.append(Permission.POST_NOTIFICATIONS)
            request_permissions(perms)
        except Exception:
            pass

    def _request_capture(self, action, request_code):
        if not self._is_connected():
            self.status_label.text = ftext("برای شروع، اینترنت لازم است.")
            return

        if platform != "android" or PythonActivity is None or autoclass is None:
            self.status_label.text = ftext("این قابلیت فقط روی اندروید کار می‌کند.")
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

            Bundle = autoclass('android.os.Bundle')
            extras = Bundle()
            extras.putInt("resultCode", result_code)
            if data is not None:
                extras.putParcelable("data", cast('android.os.Parcelable', data))

            service_intent.putExtras(extras)

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

    def open_floating_widget(self, instance):
        if platform != "android" or PythonActivity is None or autoclass is None:
            self.status_label.text = ftext("این قابلیت فقط روی اندروید کار می‌کند.")
            return
        try:
            Settings = autoclass('android.provider.Settings')
            activity = PythonActivity.mActivity

            has_permission = True
            if BuildVersion is not None and BuildVersion.SDK_INT >= 23:
                has_permission = Settings.canDrawOverlays(activity)

            if not has_permission:
                Uri = autoclass('android.net.Uri')
                package_name = activity.getPackageName()
                intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + package_name)
                )
                activity.startActivity(intent)
                self.status_label.text = ftext("لطفاً اجازه نمایش روی برنامه‌های دیگر را فعال کنید")
                return

            floating_intent = Intent(activity, autoclass(FLOATING_SERVICE_CLASS))
            if BuildVersion is not None and BuildVersion.SDK_INT >= 26:
                activity.startForegroundService(floating_intent)
            else:
                activity.startService(floating_intent)

            self.status_label.text = ftext("دکمه شناور فعال شد")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در باز کردن دکمه شناور: {e}")
