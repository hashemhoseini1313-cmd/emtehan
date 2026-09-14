package org.example.screenrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class CaptureRequestActivity extends Activity {

    private static final String TAG = "CaptureRequestActivity";
    public static final String EXTRA_ACTION = "capture_action";
    private static final int REQUEST_CODE = 5001;

    private boolean hasActivityResult = false;
    private String actionToPerform; // اکشن اصلی (START/SCREENSHOT)

    private int tempResultCode = Activity.RESULT_CANCELED;
    private Intent tempIntentData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        if (savedInstanceState != null) {
            Log.d(TAG, "Restoring state from savedInstanceState");
            hasActivityResult = savedInstanceState.getBoolean("hasActivityResult", false);
            actionToPerform = savedInstanceState.getString("actionToPerform");
            tempResultCode = savedInstanceState.getInt("tempResultCode", Activity.RESULT_CANCELED);
            tempIntentData = savedInstanceState.getParcelable("tempIntentData");

            if (hasActivityResult) {
                // اگر نتیجه قبلا دریافت شده، پردازش را به onResume موکول کن
            } else {
                Intent intent = getIntent();
                if (intent != null) {
                    startCaptureRequest(intent);
                } else {
                    Log.e(TAG, "onCreate: Received null intent and no saved state to restore.");
                    Toast.makeText(this, "Error: Cannot start capture.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                startCaptureRequest(intent);
            } else {
                Log.e(TAG, "onCreate: Received null intent.");
                Toast.makeText(this, "Error: Cannot start capture.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCaptureRequest(Intent intent) {
        String currentAction = intent.getStringExtra(EXTRA_ACTION);
        Log.d(TAG, "Starting capture request for action: " + currentAction);

        // اطمینان حاصل کن که actionToPerform مقدار معتبری دارد
        if (currentAction == null
                || (!ScreenCaptureService.ACTION_START.equals(currentAction)
                && !ScreenCaptureService.ACTION_SCREENSHOT.equals(currentAction))) {

            Log.e(TAG, "Invalid or missing action in intent: " + currentAction);
            Toast.makeText(this, "Error: Invalid capture action.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // جلوگیری از شروع چندباره درخواست اگر نتیجه قبلا دریافت شده و پردازش نشده
        if (hasActivityResult && tempResultCode != Activity.RESULT_CANCELED) {
            Log.w(TAG, "onActivityResult already received but not processed. Ignoring new request.");
            finish();
            return;
        }

        this.actionToPerform = currentAction;

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (mgr == null) {
            Log.e(TAG, "MediaProjectionManager is null!");
            Toast.makeText(this, "Error initializing capture.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent captureIntent = mgr.createScreenCaptureIntent();

        Log.d(TAG, "Launched screen capture intent");
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult called: requestCode="
                + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_CODE) {

            // نتیجه را ذخیره کن
            tempResultCode = resultCode;
            tempIntentData = data;
            hasActivityResult = true;

            Log.d(TAG, "Stored result: resultCode="
                    + tempResultCode
                    + ", data=" + (data != null)
                    + ", actionToPerform=" + actionToPerform);

        } else {
            Log.w(TAG, "onActivityResult: Unhandled request code: " + requestCode);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume called. hasActivityResult="
                + hasActivityResult
                + ", tempResultCode="
                + tempResultCode);

        /*
         * مهم:
         * تا وقتی onActivityResult اجرا نشده، Activity را نبند.
         *
         * قبلاً اینجا tempResultCode را بررسی می‌کردیم.
         * مقدار اولیه tempResultCode برابر RESULT_CANCELED بود
         * و همین باعث می‌شد Activity قبل از برگشت نتیجه مجوز بسته شود.
         */
        if (hasActivityResult) {
            processAndFinish();
        }
    }

    private void processAndFinish() {
        Log.d(TAG, "Processing result and finishing...");

        // 1. بررسی صحت ورودی‌ها
        if (actionToPerform == null) {
            Log.e(TAG, "Cannot process: actionToPerform is null.");
            Toast.makeText(this, "Internal error: Capture action not specified.", Toast.LENGTH_SHORT).show();

            if (!isFinishing()) {
                finish();
            }
            return;
        }

        if (tempResultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Result code is not OK: " + tempResultCode);
            Toast.makeText(this, "Capture permission denied or cancelled.", Toast.LENGTH_SHORT).show();

            if (!isFinishing()) {
                finish();
            }
            return;
        }

        if (tempIntentData == null) {
            Log.e(TAG, "Cannot process: tempIntentData is null.");
            Toast.makeText(this, "Error: Capture data is missing.", Toast.LENGTH_SHORT).show();

            if (!isFinishing()) {
                finish();
            }
            return;
        }

        // 2. تعیین اکشن سرویس
        String serviceAction;

        if (ScreenCaptureService.ACTION_SCREENSHOT.equals(actionToPerform)) {

            serviceAction = ScreenCaptureService.ACTION_SCREENSHOT;
            Log.d(TAG, "Determined service action: ACTION_SCREENSHOT");

        } else if (ScreenCaptureService.ACTION_START.equals(actionToPerform)) {

            serviceAction = ScreenCaptureService.ACTION_START;
            Log.d(TAG, "Determined service action: ACTION_START");

        } else {

            Log.e(TAG, "Unknown actionToPerform: " + actionToPerform);
            Toast.makeText(this, "Error: Unknown action.", Toast.LENGTH_SHORT).show();

            if (!isFinishing()) {
                finish();
            }

            return;
        }

        // 3. ساخت Intent برای سرویس و ارسال آن
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);

        serviceIntent.setAction(serviceAction);

        serviceIntent.putExtra("resultCode", tempResultCode);
        serviceIntent.putExtra("data", tempIntentData);
        serviceIntent.putExtra("calling_action", actionToPerform);

        Log.d(TAG, "Attempting to start service with action: " + serviceAction);

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(serviceIntent);
                Log.d(TAG, "Started foreground service successfully.");

            } else {

                startService(serviceIntent);
                Log.d(TAG, "Started service successfully.");
            }

            // پردازش موفقیت‌آمیز بود، اکتیویتی را ببند
            if (!isFinishing()) {
                finish();
            }

        } catch (Exception e) {

            Log.e(TAG, "Error starting service: " + e.getMessage(), e);

            Toast.makeText(this, "Error starting capture service.", Toast.LENGTH_SHORT).show();

            if (!isFinishing()) {
                finish();
            }
        }
    }

    // متدی برای فراخوانی finish() و انجام کارهای تمیزکاری نهایی
    private void finishAndCleanUp() {
        Log.d(TAG, "Finish and clean up called.");

        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.d(TAG, "onSaveInstanceState called");

        outState.putBoolean("hasActivityResult", hasActivityResult);
        outState.putString("actionToPerform", actionToPerform);
        outState.putInt("tempResultCode", tempResultCode);
        outState.putParcelable("tempIntentData", tempIntentData);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy called");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Log.d(TAG, "onNewIntent called");

        setIntent(intent);

        // ریست کردن وضعیت برای پردازش Intent جدید
        hasActivityResult = false;
        tempResultCode = Activity.RESULT_CANCELED;
        tempIntentData = null;
        actionToPerform = null;

        startCaptureRequest(intent);
    }
}

تنها تغییر عملکردی مهم این است که در "onResume()" دیگر این بخش وجود ندارد:

else {
    if (actionToPerform == null && tempResultCode == Activity.RESULT_CANCELED) {
        finish();
    } else if (tempResultCode != Activity.RESULT_OK) {
        finish();
    }
}

بنابراین تا وقتی "onActivityResult()" واقعاً اجرا نشده، Activity بسته نمی‌شود.

بعد از Build و نصب، تست کن و این دستور را بزن:

adb logcat -c

مجوز را تأیید کن، سپس:

adb logcat -d | findstr /i "CaptureRequestActivity"

مهم‌ترین چیزی که باید این بار ببینیم:

onActivityResult called: requestCode=5001, resultCode=-1

اگر این خط ظاهر شد، یعنی مجوز واقعاً تأیید شده و نتیجه به برنامه برگشته.
