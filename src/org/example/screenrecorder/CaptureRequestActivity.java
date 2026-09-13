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
    private boolean resultReceived = false;
    private int pendingResultCode;
    private Intent pendingData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startCaptureRequest(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // چون این Activity از نوع singleInstance است، اگر نمونه‌ی قبلی هنوز زنده باشد
        // سیستم به‌جای onCreate، این متد را صدا می‌زند. باید درخواست جدید را
        // از نو پردازش کنیم، نه اینکه فقط finish() کنیم (که باعث عدم پاسخ اپ می‌شد).
        resultReceived = false;
        startCaptureRequest(intent);
    }

    private void startCaptureRequest(Intent intent) {
        pendingAction = intent.getStringExtra(EXTRA_ACTION);

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mgr.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE) {
            resultReceived = true;
            pendingResultCode = resultCode;
            pendingData = data;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (resultReceived) {
            resultReceived = false;

            if (pendingResultCode == Activity.RESULT_OK && pendingData != null) {
                String action = ScreenCaptureService.ACTION_SCREENSHOT.equals(pendingAction)
                        ? ScreenCaptureService.ACTION_SCREENSHOT
                        : ScreenCaptureService.ACTION_START;

                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(action);

                Bundle extras = new Bundle();
                extras.putInt("resultCode", pendingResultCode);
                extras.putParcelable("data", pendingData);
                serviceIntent.putExtras(extras);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }

            // مهم: finish() باید مستقیم و بدون تأخیر از داخل onResume() صدا زده شود.
            finish();
        }
    }
}
