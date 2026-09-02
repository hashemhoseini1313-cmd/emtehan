# -*- coding: utf-8 -*-

import traceback

try:
    import os
    import re
    from kivy.app import App
    from kivy.uix.boxlayout import BoxLayout
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

    class ScreenRecorderApp(App):
        def build(self):
            layout = BoxLayout(orientation="vertical", padding=30, spacing=15)
            title = Label(text=ftext("ضبط صفحه گوشی (اندروید 15)"), font_name=_FONT_NAME, font_size="24sp")
            btn1 = Label(text=ftext("شروع ضبط صفحه"), font_name=_FONT_NAME, font_size="18sp")
            btn2 = Label(text=ftext("توقف ضبط"), font_name=_FONT_NAME, font_size="18sp")
            btn3 = Label(text=ftext("عکس از صفحه"), font_name=_FONT_NAME, font_size="18sp")
            layout.add_widget(title)
            layout.add_widget(btn1)
            layout.add_widget(btn2)
            layout.add_widget(btn3)
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
