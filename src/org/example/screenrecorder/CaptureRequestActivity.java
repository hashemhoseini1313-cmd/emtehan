package org.example.screenrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

public class CaptureRequestActivity extends Activity {

    public static final String EXTRA_ACTION = "capture_action";
    private static final int REQUEST_CODE = 5001;

    private String pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // اگر اکتیویتی در حال بازسازی مجدد بود، دوباره درخواست ندهد
        if (savedInstanceState != null) {
            finish();
            return;
        }

        pendingAction = getIntent().getStringExtra(EXTRA_ACTION);

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr != null) {
            Intent captureIntent = mgr.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE);
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String action = ScreenCaptureService.ACTION_SCREENSHOT.equals(pendingAction)
                        ? ScreenCaptureService.ACTION_SCREENSHOT
                        : ScreenCaptureService.ACTION_START;

                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(action);

                Bundle extras = new Bundle();
                extras.putInt("resultCode", resultCode);
                extras.putParcelable("data", data);
                serviceIntent.putExtras(extras);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }

            // فراخوانی مستقیم و بدون تاخیر finish برای جلوگیری از اجرای onResume و کرش برنامه
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // اگر اکتیویتی در حال بسته‌شدن است، از اجرای کدهای احتمالی دیگر جلوگیری می‌شود
        if (isFinishing()) {
            return;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        finish();
    }
}
