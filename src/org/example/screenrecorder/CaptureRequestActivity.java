package org.example.screenrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast; // برای نمایش پیام به کاربر در صورت بروز مشکل

public class CaptureRequestActivity extends Activity {

    private static final String TAG = "CaptureRequestActivity";
    public static final String EXTRA_ACTION = "capture_action";
    private static final int REQUEST_CODE = 5001;

    // برای ذخیره وضعیت و جلوگیری از اجرای چندباره
    private boolean processingResult = false;
    private String actionToPerform; // اکشنی که باید به سرویس ارسال شود (START/SCREENSHOT)

    // متغیرهایی برای نگهداری نتیجه موقت
    private int tempResultCode = Activity.RESULT_CANCELED;
    private Intent tempIntentData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        // اگر اکتیویتی در حال بازسازی است (مثلا بعد از تغییر جهت صفحه)، وضعیت را بازیابی کن
        if (savedInstanceState != null) {
            Log.d(TAG, "Restoring state from savedInstanceState");
            // وضعیت بازیابی شده به طور خودکار در متدهای بعدی استفاده خواهد شد
            // اما باید مطمئن شویم که پردازش نتیجه اگر لازم بود، دوباره انجام شود.
            processingResult = savedInstanceState.getBoolean("processingResult", false);
            actionToPerform = savedInstanceState.getString("actionToPerform");
            tempResultCode = savedInstanceState.getInt("tempResultCode", Activity.RESULT_CANCELED);
            tempIntentData = savedInstanceState.getParcelable("tempIntentData");

            if (processingResult) {
                // اگر پردازش قبلا شروع شده ولی تمام نشده، دوباره پردازش کن
                // این بخش باید با دقت مدیریت شود تا دوباره‌کاری رخ ندهد.
                // معمولا بهتر است که پردازش را در onResume انجام دهیم.
            } else {
                 // اگر پردازش انجام نشده، Intent فعلی را پردازش کن
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
            // اگر وضعیت جدید است، Intent دریافتی را پردازش کن
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

        // جلوگیری از شروع چندباره درخواست اگر قبلا در حال پردازش هستیم
        if (processingResult) {
             Log.w(TAG, "Already processing result, ignoring new request.");
             // اگر در حال پردازش بودیم و این اتفاق افتاد، یعنی یک مشکل در مدیریت وضعیت وجود دارد.
             // بهتر است اکتیویتی را ببندیم تا وضعیت به حالت اولیه برگردد.
             finish();
             return;
        }

        // ذخیره اکشن اصلی برای استفاده در سرویس
        this.actionToPerform = currentAction;

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
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
        Log.d(TAG, "onActivityResult called: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_CODE) {
            // نتیجه را ذخیره کن، اما پردازش را به onResume منتقل کن
            // این کار کمک می‌کند تا مطمئن شویم اکتیویتی در وضعیت مناسبی برای پردازش قرار دارد
            tempResultCode = resultCode;
            tempIntentData = data;
            processingResult = true; // علامت‌گذاری برای پردازش در onResume
        } else {
            Log.w(TAG, "onActivityResult: Unhandled request code: " + requestCode);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called. processingResult=" + processingResult + ", tempResultCode=" + tempResultCode);

        // اگر نتیجه‌ای دریافت شده بود و پردازش نشده بود، آن را پردازش کن
        if (processingResult) {
            // اطمینان حاصل کن که این پردازش فقط یک بار انجام شود
            // (اینجا processingResult را false می‌کنیم تا دوباره اجرا نشود)
            processingResult = false;
            performActionBasedOnResult(tempResultCode, tempIntentData);
        } else {
            // اگر نتیجه‌ای دریافت نشده بود، ممکن است کاربر صفحه را تغییر داده باشد
            // و اکتیویتی در وضعیت نامعلومی باشد. در این حالت، بهتر است آن را ببندیم.
            Log.d(TAG, "onResume: No result pending. Finishing activity.");
            // اگر در onCreate وضعیت بازیابی شد و processingResult هنوز false بود،
            // یعنی درخواست capture شروع نشده بود، پس نباید finish کنیم.
            // این بخش نیاز به دقت بیشتری دارد.
            // اگر اکتیویتی به اینجا رسید و processingResult false بود و هیچ درخواستی شروع نشده بود،
            // یعنی یا تازه شروع شده یا وضعیت نامشخص است.
            // اگر actionToPerform خالی است، یعنی درخواست capture اصلا شروع نشده.
             if (actionToPerform == null && tempResultCode == Activity.RESULT_CANCELED) {
                 // این حالت نباید رخ دهد مگر اینکه برنامه بسته شده باشد.
                 Log.w(TAG, "onResume: Activity is resuming without any pending action or result.");
             } else if (tempResultCode != Activity.RESULT_OK) {
                 // اگر نتیجه OK نبود و قبلا پردازش نشده، بهتر است ببندیم.
                 Log.d(TAG, "onResume: Previous result was not OK. Finishing activity.");
                 finish();
             }
        }
    }

    private void performActionBasedOnResult(int resultCode, Intent data) {
        Log.d(TAG, "Performing action based on result...");
        if (resultCode == Activity.RESULT_OK && data != null && actionToPerform != null) {
            String serviceAction = ScreenCaptureService.ACTION_SCREENSHOT.equals(actionToPerform)
                    ? ScreenCaptureService.ACTION_SCREENSHOT
                    : ScreenCaptureService.ACTION_START;

            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.setAction(serviceAction);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            serviceIntent.putExtra("calling_action", actionToPerform); // اکشن اصلی

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                    Log.d(TAG, "Started foreground service for action: " + serviceAction);
                } else {
                    startService(serviceIntent);
                    Log.d(TAG, "Started service for action: " + serviceAction);
                }
                // پردازش با موفقیت انجام شد، حالا اکتیویتی را ببند
                finishAndCleanUp();
            } catch (Exception e) {
                Log.e(TAG, "Error starting service: " + e.getMessage(), e);
                Toast.makeText(this, "Error starting capture service.", Toast.LENGTH_SHORT).show();
                // اگر شروع سرویس ناموفق بود، اکتیویتی را ببند
                finishAndCleanUp();
            }
        } else {
            Log.d(TAG, "Result not OK or data is null or actionToPerform is null. Finishing activity.");
            // اگر نتیجه OK نبود یا اطلاعات لازم را نداشتیم، اکتیویتی را ببند
            Toast.makeText(this, "Capture permission denied or cancelled.", Toast.LENGTH_SHORT).show();
            finishAndCleanUp();
        }
    }

    // متدی برای فراخوانی finish() و انجام کارهای تمیزکاری نهایی
    private void finishAndCleanUp() {
        Log.d(TAG, "Finishing activity and cleaning up.");
        // اطمینان حاصل کنید که finish() فقط یک بار فراخوانی می‌شود
        // (مثلا با بررسی اینکه آیا اکتیویتی از قبل تمام شده است یا نه)
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState called");
        outState.putBoolean("processingResult", processingResult);
        outState.putString("actionToPerform", actionToPerform);
        outState.putInt("tempResultCode", tempResultCode);
        outState.putParcelable("tempIntentData", tempIntentData);
    }

    // onDestroy نیازی به finish() ندارد
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    // مدیریت onNewIntent برای singleInstance
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent); // مهم: Intent جدید را تنظیم کن

        // اگر در حال پردازش نتیجه قبلی بودیم، ممکن است لازم باشد آن را لغو کنیم
        // و پردازش Intent جدید را شروع کنیم.
        // برای سادگی، اینجا فرض می‌کنیم که این اکتیویتی فقط یک بار باید اجرا شود.
        // اگر Intent جدیدی آمد، بهتر است که اکتیویتی فعلی را ببندیم و یک نمونه جدید ایجاد شود
        // یا حداقل پردازش قبلی را متوقف کنیم.

        // برای جلوگیری از پیچیدگی، فرض می‌کنیم که اگر onNewIntent فراخوانی شد،
        // یعنی کاربر دوباره درخواست را شروع کرده. پس وضعیت قبلی را ریست می‌کنیم
        // و پردازش Intent جدید را آغاز می‌کنیم.
        processingResult = false;
        tempResultCode = Activity.RESULT_CANCELED;
        tempIntentData = null;
        actionToPerform = null; // ریست کردن اکشن قبلی

        startCaptureRequest(intent);
    }
}
