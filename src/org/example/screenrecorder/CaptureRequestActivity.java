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

        pendingAction = getIntent().getStringExtra(EXTRA_ACTION);

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mgr.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
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

        finish();
    }
}
