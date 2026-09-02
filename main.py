# -*- coding: utf-8 -*-

import traceback

try:
    import os
    from kivy.app import App
    from kivy.uix.boxlayout import BoxLayout
    from kivy.uix.label import Label

    class ScreenRecorderApp(App):
        def build(self):
            layout = BoxLayout(orientation="vertical", padding=30, spacing=15)
            title = Label(text="Screen Recorder Test", font_size="24sp")
            btn1 = Label(text="Start Recording", font_size="18sp")
            btn2 = Label(text="Stop Recording", font_size="18sp")
            btn3 = Label(text="Take Screenshot", font_size="18sp")
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
