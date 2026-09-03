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

    _FONT_NAME = "Roboto"

    if platform == "android":
        candidate = "fonts/Vazirmatn-Light.ttf"
        if os.path.exists(candidate):
            try:
                LabelBase.register(name="PersianFont", fn_regular=candidate)
                _FONT_NAME = "PersianFont"
            except Exception as e:
                print(f"font registration failed: {e}")
                _FONT_NAME = "Roboto"

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
            layout = BoxLayout(orientation="vertical", padding=30, spacing=15)

            title = PersianLabel(text="ضبط صفحه گوشی (اندروید 15)", font_size="24sp")
            start_button = PersianButton(text="شروع ضبط صفحه", font_size="18sp", size_hint_y=None, height=65)
            stop_button = PersianButton(text="توقف ضبط", font_size="18sp", size_hint_y=None, height=65)
            photo_button = PersianButton(text="عکس از صفحه", font_size="18sp", size_hint_y=None, height=65)

            start_button.bind(on_press=lambda instance: print("start pressed"))
            stop_button.bind(on_press=lambda instance: print("stop pressed"))
            photo_button.bind(on_press=lambda instance: print("photo pressed"))

            layout.add_widget(title)
            layout.add_widget(start_button)
            layout.add_widget(stop_button)
            layout.add_widget(photo_button)

            return layout

    if __name__ == "__main__":
        ScreenRecorderApp().run()

except Exception:
    error_msg = traceback.format_exc()
    try:
        with open("error_log.txt", "w") as f:
            f.write(error_msg)
    except:
        pass
    print(error_msg)
