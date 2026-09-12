# -*- coding: utf-8 -*-
# app_logic.pyx — منطق اصلی برنامه (این فایل با Cython به باینری کامپایل می‌شود)

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

# ---------- متغیرهای اندروید ----------
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
            print(f"Successfully loaded NetworkMonitor class.")
        except Exception as e_net:
            print(f"Failed to load NetworkMonitor class: {e_net}")
            NetworkMonitor = None
    except Exception as e:
        print(f"Android init failed: {e}")

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

# ---------- فونت فارسی ----------
FONT_FILE = None
_FONT_NAME = "Roboto"

if platform == "android":
    candidate = "fonts/Vazirmatn-Light.ttf"
    if os.path.exists(candidate):
        try:
            LabelBase.register(name="PersianFont", fn_regular=candidate)
            FONT_FILE = candidate
            _FONT_NAME = "PersianFont"
            print("Persian font registered successfully.")
        except Exception as e:
            print(f"font registration failed: {e}")
            _FONT_NAME = "Roboto"
    else:
        print("font file not found, using Roboto")
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
                print("Bound on_activity_result.")
            except Exception as e:
                print(f"bind activity failed: {e}")

            self._request_runtime_permissions()
            self._register_network_callback()

        self._update_connectivity_ui()
        Clock.schedule_interval(self._update_connectivity_ui, 1)
        print("Kivy app build complete. Starting connectivity checks.")

        return layout

    def _register_network_callback(self):
        try:
            if NetworkMonitor is not None and PythonActivity is not None:
                activity = PythonActivity.mActivity
                if hasattr(NetworkMonitor, 'startMonitoring'):
                    NetworkMonitor.startMonitoring(activity)
                    print("Native NetworkMonitor registered successfully.")
                else:
                    print("NetworkMonitor class exists but does not have 'startMonitoring' method.")
            else:
                print("NetworkMonitor or PythonActivity is not available. Cannot register network callback.")
        except Exception as e:
            print(f"network callback registration failed: {e}")

    def _is_connected(self):
        print("DEBUG: Entering _is_connected method.")
        if platform != "android":
            print("DEBUG: Not on Android platform, returning True.")
            return True
        try:
            if NetworkMonitor is not None:
                if hasattr(NetworkMonitor, 'getIsConnected'):
                    is_connected_native = NetworkMonitor.getIsConnected()
                    print(f"DEBUG: NetworkMonitor.getIsConnected() returned: {is_connected_native}")
                    return bool(is_connected_native)
                else:
                    if hasattr(NetworkMonitor, 'isConnected'):
                        is_connected_native = NetworkMonitor.isConnected
                        print(f"DEBUG: NetworkMonitor.isConnected (direct access) returned: {is_connected_native}")
                        return bool(is_connected_native)
                    else:
                        print("DEBUG: NetworkMonitor has neither 'getIsConnected' nor 'isConnected'. Assuming not connected.")
                        return False
            else:
                print("DEBUG: NetworkMonitor is None. Assuming not connected.")
                return False
        except Exception as e:
            print(f"DEBUG: Exception in _is_connected: {e}")
            return False

    @mainthread
    def _update_connectivity_ui(self, *args):
        print("DEBUG: Entering _update_connectivity_ui method.")
        connected = self._is_connected()
        print(f"NET_CALLBACK: connectivity check result = {connected}")

        self.start_button.disabled = not connected
        self.stop_button.disabled = not connected
        self.photo_button.disabled = not connected
        self.floating_button.disabled = not connected

        if connected:
            self.status_label.text = ftext("آماده")
            print("DEBUG: UI updated to 'آماده'.")
        else:
            self.status_label.text = ftext("اتصال اینترنت برقرار نیست")
            print("DEBUG: UI updated to 'اتصال اینترنت برقرار نیست'.")

        return connected

    def _request_runtime_permissions(self):
        try:
            from android.permissions import request_permissions, Permission
            perms = [Permission.FOREGROUND_SERVICE, Permission.RECORD_AUDIO]
            if BuildVersion is not None and BuildVersion.SDK_INT >= 33:
                perms.append(Permission.POST_NOTIFICATIONS)
            request_permissions(perms)
            print("Requested runtime permissions.")
        except Exception as e:
            print(f"permission request failed: {e}")

    def _request_capture(self, action, request_code):
        if not self._is_connected():
            self.status_label.text = ftext("برای شروع، اینترنت لازم است.")
            print("ACTION_DENIED: Internet not connected, cannot start capture.")
            return

        if platform != "android" or PythonActivity is None or autoclass is None:
            self.status_label.text = ftext("این قابلیت فقط روی اندروید کار می‌کند.")
            print("ACTION_DENIED: Not on Android or Android components missing.")
            return

        try:
            print(f"DEBUG: Requesting capture for action: {action}, requestCode: {request_code}")
            self.pending_action = action
            activity = PythonActivity.mActivity
            MediaProjectionManager = autoclass("android.media.projection.MediaProjectionManager")
            projection_service = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            mgr = cast(MediaProjectionManager, projection_service)
            intent = mgr.createScreenCaptureIntent()
            activity.startActivityForResult(intent, request_code)
            self.status_label.text = ftext("منتظر تأیید مجوز...")
            print("DEBUG: Screen capture intent started.")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در درخواست مجوز: {e}")
            print(f"ERROR: Exception in _request_capture: {e}")

    def start_recording(self, instance):
        print("BUTTON_PRESS: start_recording called.")
        self._request_capture("record", REQUEST_RECORD)

    def take_screenshot(self, instance):
        print("BUTTON_PRESS: take_screenshot called.")
        self._request_capture("screenshot", REQUEST_SCREENSHOT)

    def on_activity_result(self, request_code, result_code, data):
        print(f"DEBUG: on_activity_result called with requestCode: {request_code}, resultCode: {result_code}")
        if request_code not in (REQUEST_RECORD, REQUEST_SCREENSHOT):
            print("DEBUG: Ignored - unknown request code.")
            return

        if result_code != -1:
            self.status_label.text = ftext("مجوز رد شد")
            self.pending_action = None
            print("ACTION_DENIED: User denied permission.")
            return

        action = ACTION_START if request_code == REQUEST_RECORD else ACTION_SCREENSHOT
        self.status_label.text = ftext("مجوز گرفته شد...")
        print("DEBUG: Permission granted, proceeding to _start_service.")
        self._start_service(action, result_code, data)

    def _start_service(self, action, result_code, data):
        print(f"DEBUG: Entering _start_service for action: {action}")
        if PythonActivity is None or autoclass is None or cast is None:
            self.status_label.text = ftext("Android init failed")
            print("ERROR: Android components missing in _start_service.")
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
            else:
                print("WARNING: 'data' is None in _start_service.")

            service_intent.putExtras(extras)

            if BuildVersion is not None and BuildVersion.SDK_INT >= 26:
                activity.startForegroundService(service_intent)
                print("DEBUG: Called startForegroundService.")
            else:
                activity.startService(service_intent)
                print("DEBUG: Called startService.")

            self.status_label.text = ftext("در حال ضبط..." if action == ACTION_START else "در حال گرفتن عکس...")
            print(f"DEBUG: Service started for action: {action}.")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در شروع سرویس: {e}")
            print(f"ERROR: Exception in _start_service: {e}")

    def stop_recording(self, instance):
        print("BUTTON_PRESS: stop_recording called.")
        if platform != "android" or PythonActivity is None or Intent is None or autoclass is None:
            print("ACTION_DENIED: Not on Android or Android components missing for stop_recording.")
            return
        try:
            activity = PythonActivity.mActivity
            service_intent = Intent(activity, autoclass(SERVICE_CLASS))
            service_intent.setAction(ACTION_STOP)
            activity.startService(service_intent)
            self.status_label.text = ftext("ضبط متوقف شد")
            print("DEBUG: Stop service intent sent.")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در توقف سرویس: {e}")
            print(f"ERROR: Exception in stop_recording: {e}")

    def open_floating_widget(self, instance):
        print("BUTTON_PRESS: open_floating_widget called.")
        if platform != "android" or PythonActivity is None or autoclass is None:
            self.status_label.text = ftext("این قابلیت فقط روی اندروید کار می‌کند.")
            print("ACTION_DENIED: Not on Android or Android components missing for floating widget.")
            return
        try:
            Settings = autoclass('android.provider.Settings')
            activity = PythonActivity.mActivity

            has_permission = True
            if BuildVersion is not None and BuildVersion.SDK_INT >= 23:
                has_permission = Settings.canDrawOverlays(activity)
                print(f"DEBUG: canDrawOverlays permission status: {has_permission}")

            if not has_permission:
                Uri = autoclass('android.net.Uri')
                package_name = activity.getPackageName()
                intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + package_name)
                )
                activity.startActivity(intent)
                self.status_label.text = ftext("لطفاً اجازه نمایش روی برنامه‌های دیگر را فعال کنید")
                print("DEBUG: Navigated to overlay permission settings.")
                return

            floating_intent = Intent(activity, autoclass(FLOATING_SERVICE_CLASS))
            if BuildVersion is not None and BuildVersion.SDK_INT >= 26:
                activity.startForegroundService(floating_intent)
                print("DEBUG: Called startForegroundService for floating widget.")
            else:
                activity.startService(floating_intent)
                print("DEBUG: Called startService for floating widget.")

            self.status_label.text = ftext("دکمه شناور فعال شد")
        except Exception as e:
            self.status_label.text = ftext(f"خطا در باز کردن دکمه شناور: {e}")
            print(f"ERROR: Exception in open_floating_widget: {e}")


def run_app():
    print("Starting ScreenRecorderApp...")
    ScreenRecorderApp().run()
    print("ScreenRecorderApp finished.")
