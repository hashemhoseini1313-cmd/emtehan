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

    CAPTURE_ACTIVITY_CLASS = "org.example.screenrecorder.CaptureRequestActivity"
    SERVICE_CLASS = "org.example.screenrecorder.ScreenCaptureService"
    FLOATING_SERVICE_CLASS = "org.example.screenrecorder.FloatingWidgetService"
    
    EXTRA_ACTION = "capture_action"
    ACTION_START = "org.example.screenrecorder.START"
    ACTION_SCREENSHOT = "org.example.screenrecorder.SCREENSHOT"
    ACTION_STOP = "org.example.screenrecorder.STOP"

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
            self.status_label = PersianLabel(text="آماده", font_size="16sp")

            layout = BoxLayout(orientation="vertical", padding=30, spacing=15)

            title = PersianLabel(text="ضبط صفحه گوشی (اندروید 15)", font_size="24sp")
            start_button = PersianButton(text="شروع ضبط صفحه", font_size="18sp", size_hint_y=None, height=65)
            stop_button = PersianButton(text="توقف ضبط", font_size="18sp", size_hint_y=None, height=65)
            photo_button = PersianButton(text="عکس از صفحه", font_size="18sp", size_hint_y=None, height=65)
            floating_button = PersianButton(text="باز کردن دکمه شناور", font_size="18sp", size_hint_y=None, height=65)

            start_button.bind(on_press=self.start_recording)
            stop_button.bind(on_press=self.stop_recording)
            photo_button.bind(on_press=self.take_screenshot)
            floating_button.bind(on_press=self.open_floating_widget)

            layout.add_widget(title)
            layout.add_widget(self.status_label)
            layout.add_widget(start_button)
            layout.add_widget(stop_button)
            layout.add_widget(photo_button)
            layout.add_widget(floating_button)

            if platform == "android":
                self._request_runtime_permissions()

            return layout

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

        # ---------- درخواست مجوز از طریق CaptureRequestActivity اختصاصی ----------
        def _request_capture(self, action_type):
            if platform != "android" or PythonActivity is None or autoclass is None:
                self.status_label.text = ftext("فقط روی اندروید")
                return
            try:
                activity = PythonActivity.mActivity
                intent = Intent(activity, autoclass(CAPTURE_ACTIVITY_CLASS))
                intent.putExtra(EXTRA_ACTION, action_type)
                
                # تنظیم Flagهای لازم برای باز شدن اکتیویتی جدید
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)

                self.status_label.text = ftext("در حال درخواست مجوز ضبط...")
            except Exception as e:
                self.status_label.text = ftext(f"خطا در اجرای CaptureRequestActivity: {e}")

        def start_recording(self, instance):
            self._request_capture(ACTION_START)

        def take_screenshot(self, instance):
            self._request_capture(ACTION_SCREENSHOT)

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

        # ---------- باز کردن دکمه شناور ----------
        def open_floating_widget(self, instance):
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
