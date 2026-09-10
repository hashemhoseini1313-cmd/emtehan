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
                NetworkMonitor = None # اطمینان از None بودن در صورت خطا
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
            self.start_button = PersianButton(text="شروع ضبط صفحه", font_size="18sp", size_hint_y=None, height=65)
            self.stop_button = PersianButton(text="توقف ضبط", font_size="18sp", size_hint_y=None, height=65)
            self.photo_button = PersianButton(text="عکس از صفحه", font_size="18sp", size_hint_y=None, height=65)
            self.floating_button = PersianButton(text="باز کردن دکمه شناور", font_size="18sp", size_hint_y=None, height=65)

            self.start_button.bind(on_press=self.start_recording)
            self.stop_button.bind(on_press=self.stop_recording)
            self.photo_button.bind(on_press=self.take_screenshot)
            self.floating_button.bind(on_press=self.open_floating_widget)

            layout.add_widget(title)
            layout.add_widget(self.status_label)
            layout.add_widget(self.start_button)
            layout.add_widget(self.stop_button)
            layout.add_widget(self.photo_button)
            layout.add_widget(self.floating_button)

            if platform == "android":
                try:
                    android_activity.bind(on_activity_result=self.on_activity_result)
                    print("Bound on_activity_result.")
                except Exception as e:
                    print(f"bind activity failed: {e}")

                self._request_runtime_permissions()
                self._register_network_callback()

            # بررسی اولیه + بررسی دوره‌ای هر ۱ ثانیه
            self._update_connectivity_ui()
            Clock.schedule_interval(self._update_connectivity_ui, 1)
            print("Kivy app build complete. Starting connectivity checks.")

            return layout

        # ---------- ثبت مانیتور نیتیو جاوا ----------
        def _register_network_callback(self):
            try:
                if NetworkMonitor is not None and PythonActivity is not None:
                    activity = PythonActivity.mActivity
                    # اطمینان از اینکه متد startMonitoring وجود دارد
                    if hasattr(NetworkMonitor, 'startMonitoring'):
                        NetworkMonitor.startMonitoring(activity)
                        print("Native NetworkMonitor registered successfully.")
                    else:
                        print("NetworkMonitor class exists but does not have 'startMonitoring' method.")
                else:
                    print("NetworkMonitor or PythonActivity is not available. Cannot register network callback.")
            except Exception as e:
                print(f"network callback registration failed: {e}")

        # ---------- بررسی اتصال اینترنت ----------
        def _is_connected(self):
            print("DEBUG: Entering _is_connected method.")
            if platform != "android":
                print("DEBUG: Not on Android platform, returning True.")
                return True
            try:
                if NetworkMonitor is not None:
                    # اینجا از متد getter جاوا استفاده می‌کنیم
                    if hasattr(NetworkMonitor, 'getIsConnected'):
                        is_connected_native = NetworkMonitor.getIsConnected()
                        print(f"DEBUG: NetworkMonitor.getIsConnected() returned: {is_connected_native}")
                        return bool(is_connected_native)
                    else:
                        # اگر متد getter وجود نداشت، سعی می‌کنیم از مقدار مستقیم استفاده کنیم
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
                print(f"DEBUG: Full traceback: {traceback.format_exc()}")
                return False # در صورت بروز خطا، فرض می‌کنیم متصل نیست

        # ---------- آپدیت UI مربوط به وضعیت اتصال ----------
        @mainthread # اطمینان از اجرا در ترد اصلی Kivy
        def _update_connectivity_ui(self, *args):
            print("DEBUG: Entering _update_connectivity_ui method.")
            connected = self._is_connected()
            print(f"NET_CALLBACK: connectivity check result = {connected}") # این پرینت حالا باید دیده شود

            # دکمه‌ها را فقط زمانی غیرفعال می‌کنیم که به طور واضح متصل نباشیم
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

        # ---------- مجوزهای زمان اجرا ----------
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

        # ---------- درخواست مجوز MediaProjection ----------
        def _request_capture(self, action, request_code):
            # ابتدا وضعیت اتصال را چک می‌کنیم
            if not self._is_connected(): # از _is_connected استفاده می‌کنیم که پرینت دارد
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
                print(f"ERROR: Full traceback: {traceback.format_exc()}")

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

            if result_code != -1: # -1 یعنی کاربر تأیید کرده است
                self.status_label.text = ftext("مجوز رد شد")
                self.pending_action = None
                print("ACTION_DENIED: User denied permission.")
                return

            # اگر مجوز گرفته شد
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
                # اطمینان از اینکه data قابل Parcelable است
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
                print(f"ERROR: Full traceback: {traceback.format_exc()}")

        def stop_recording(self, instance):
            print("BUTTON_PRESS: stop_recording called.")
            # اتصال اینترنت را چک نمی‌کنیم چون استاپ کردن سرویس نیازی به اینترنت ندارد
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
                print(f"ERROR: Full traceback: {traceback.format_exc()}")

        # ---------- باز کردن دکمه شناور ----------
        def open_floating_widget(self, instance):
            print("BUTTON_PRESS: open_floating_widget called.")
            # برای باز کردن دکمه شناور، وضعیت اتصال مهم نیست، مگر اینکه خود دکمه شناور نیاز به اینترنت داشته باشد
            # if not self._is_connected():
            #     self.status_label.text = ftext("برای استفاده از دکمه شناور، اینترنت لازم است.")
            #     print("ACTION_DENIED: Internet not connected, cannot open floating widget.")
            #     return

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
                print(f"ERROR: Full traceback: {traceback.format_exc()}")

    if __name__ == "__main__":
        print("Starting ScreenRecorderApp...")
        ScreenRecorderApp().run()
        print("ScreenRecorderApp finished.")

except Exception:
    error_msg = traceback.format_exc()
    print(f"FATAL ERROR: Uncaught exception: {error_msg}") # پرینت خطای اصلی
    try:
        # تلاش برای نوشتن در فایل لاگ
        with open("error_log.txt", "w", encoding="utf-8") as f:
            f.write(error_msg)
            print("Error details written to error_log.txt")
    except Exception as log_err:
        print(f"Failed to write error to file: {log_err}")

    try:
        # تلاش برای نمایش پیام خطا در اندروید
        if platform == "android":
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Toast = autoclass('android.widget.Toast')
            activity = PythonActivity.mActivity
            # کوتاه کردن پیام خطا برای نمایش در Toast
            short_error_msg = error_msg[:200] + "..." if len(error_msg) > 200 else error_msg
            Toast.makeText(activity, f"Error:\n{short_error_msg}", Toast.LENGTH_LONG).show()
            print("Displayed error message in Android Toast.")
    except Exception as toast_err:
        print(f"Failed to display error in Toast: {toast_err}")
