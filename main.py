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
            except Exception as e_net:
                print(f"Failed to load NetworkMonitor autoclass: {e_net}")
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
            self._net_callback = None
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
                except Exception as e:
                    print(f"bind activity failed: {e}")

                self._request_runtime_permissions()
                self._register_network_callback()

            # بررسی اولیه + بررسی دوره‌ی پشتیبان (fallback) هر ۱ ثانیه
            self._update_connectivity_ui()
            Clock.schedule_interval(lambda dt: self._update_connectivity_ui(), 1)

            return layout

        # ---------- ثبت شنونده‌ی نیتیو جاوا برای تغییرات شبکه ----------
        def _register_network_callback(self):
            try:
                if NetworkMonitor is not None and PythonActivity is not None:
                    activity = PythonActivity.mActivity
                    NetworkMonitor.startMonitoring(activity)
                    print("Native NetworkMonitor registered successfully")
            except Exception as e:
                print(f"network callback registration failed: {e}")

        @mainthread
        def _on_network_changed(self):
            print("NET_CALLBACK: network changed event fired")
            self._update_connectivity_ui()

        # ---------- بررسی اتصال اینترنت ----------
        def _is_connected(self):
            if platform != "android":
                return True
            try:
                # اولویت اول: خواندن مستقیم پرچم زنده از کلاس NetworkMonitor جاوا
                if NetworkMonitor is not None:
                    return bool(NetworkMonitor.isConnected)

                if PythonActivity is None or autoclass is None:
                    return True

                activity = PythonActivity.mActivity
                ConnectivityManager = autoclass('android.net.ConnectivityManager')
                cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE)
                cm = cast(ConnectivityManager, cm)

                if BuildVersion is not None and BuildVersion.SDK_INT >= 23:
                    network = cm.getActiveNetwork()
                    if network is None:
                        return False
                    NetworkCapabilities = autoclass('android.net.NetworkCapabilities')
                    capabilities = cm.getNetworkCapabilities(network)
                    if capabilities is None:
                        return False
                    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                else:
                    network_info = cm.getActiveNetworkInfo()
                    return network_info is not None and network_info.isConnected()
            except Exception as e:
                print(f"connectivity check failed: {e}")
                return True

        def _update_connectivity_ui(self):
            connected = self._is_connected()
            print(f"NET_CALLBACK: connectivity check result = {connected}")

            self.start_button.disabled = not connected
            self.stop_button.disabled = not connected
            self.photo_button.disabled = not connected
            self.floating_button.disabled = not connected

            if not connected:
                self.status_label.text = ftext("اتصال اینترنت برقرار نیست")
            elif self.status_label.text == ftext("اتصال اینترنت برقرار نیست"):
                self.status_label.text = ftext("آماده")

            return connected

        # ---------- مجوزهای زمان اجرا ----------
        def _request_runtime_permissions(self):
            try:
                from android.permissions import request_permissions, Permission
                perms = [Permission.FOREGROUND_SERVICE, Permission.RECORD_AUDIO]
                if BuildVersion is not None and BuildVersion.SDK_INT >= 33:
                    perms.append(Permission.POST_NOTIFICATIONS)
                request_permissions(perms)
            except Exception as e:
                print(f"permission request failed: {e}")

        # ---------- درخواست مجوز MediaProjection ----------
        def _request_capture(self, action, request_code):
            if not self._update_connectivity_ui():
                return
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

                Bundle = autoclass('android.os.Bundle')
                extras = Bundle()
                extras.putInt("resultCode", result_code)
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
            if not self._update_connectivity_ui():
                return
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

        # ---------- باز کردن دکمه شناور ----------
        def open_floating_widget(self, instance):
            if not self._update_connectivity_ui():
                return
            if platform != "android" or PythonActivity is None or autoclass is None:
                self.status_label.text = ftext("فقط روی اندروید")
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

    if __name__ == "__main__":
        ScreenRecorderApp().run()

except Exception:
    error_msg = traceback.format_exc()
    try:
        with open("error_log.txt", "w") as f:
            f.write(error_msg)
    except:
        pass
    try:
        from jnius import autoclass
        PythonActivity = autoclass('org.kivy.android.PythonActivity')
        Toast = autoclass('android.widget.Toast')
        activity = PythonActivity.mActivity
        Toast.makeText(activity, "Error:\n" + error_msg[:200], Toast.LENGTH_LONG).show()
    except:
        pass
    print(error_msg)
