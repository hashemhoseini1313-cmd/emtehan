# -*- coding: utf-8 -*-

import traceback
from kivy.utils import platform

try:
    import app_logic
    app_logic.run_app()

except Exception:
    error_msg = traceback.format_exc()
    print(f"FATAL ERROR: Uncaught exception: {error_msg}")
    try:
        with open("error_log.txt", "w", encoding="utf-8") as f:
            f.write(error_msg)
    except Exception:
        pass

    try:
        if platform == "android":
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Toast = autoclass('android.widget.Toast')
            activity = PythonActivity.mActivity
            short_error_msg = error_msg[:200] + "..." if len(error_msg) > 200 else error_msg
            Toast.makeText(activity, f"Error:\n{short_error_msg}", Toast.LENGTH_LONG).show()
    except Exception:
        pass
