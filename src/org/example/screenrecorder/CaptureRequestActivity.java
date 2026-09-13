package org.example.screenrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log; // برای لاگ‌گیری بهتر

public class CaptureRequestActivity extends Activity {

    private static final String TAG = "CaptureRequestActivity"; // برای لاگ‌گیری
    public static final String EXTRA_ACTION = "capture_action";
    private static final int REQUEST_CODE = 5001;

    // استفاده از مقادیر ثابت برای ذخیره وضعیت در Bundle
    private static final String STATE_PENDING_ACTION = "pending_action";
    private static final String STATE_RESULT_RECEIVED = "result_received";
    private static final String STATE_PENDING_RESULT_CODE = "pending_result_code";
    private static final String STATE_PENDING_DATA = "pending_data";

    private String pendingAction;
    private boolean resultReceived = false;
    private int pendingResultCode = Activity.RESULT_CANCELED; // مقدار پیش‌فرض
    private Intent pendingData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        if (savedInstanceState != null) {
            // بازیابی وضعیت در صورت بازسازی اکتیویتی
            Log.d(TAG, "Restoring state from savedInstanceState");
            pendingAction = savedInstanceState.getString(STATE_PENDING_ACTION);
            resultReceived = savedInstanceState.getBoolean(STATE_RESULT_RECEIVED);
            pendingResultCode = savedInstanceState.getInt(STATE_PENDING_RESULT_CODE);
            pendingData = savedInstanceState.getParcelable(STATE_PENDING_DATA);

            // اگر نتیجه قبلا دریافت شده و پردازش نشده، دوباره پردازش کن
            if (resultReceived) {
                processActivityResult();
            } else {
                // اگر نتیجه دریافت نشده بود، ممکن است نیاز باشد دوباره درخواست را شروع کنیم
                // اما بهتر است که این را در onResume مدیریت کنیم تا مطمئن شویم اکتیویتی کاملا ready است.
            }
        } else {
            // اگر وضعیت جدید است، Intent دریافتی را پردازش کن
            Intent intent = getIntent();
            if (intent != null) {
                startCaptureRequest(intent);
            } else {
                Log.e(TAG, "onCreate: Received null intent");
                finish(); // اگر Intent نداریم، کاری نمی‌توان کرد
            }
        }
    }

    private void startCaptureRequest(Intent intent) {
        pendingAction = intent.getStringExtra(EXTRA_ACTION);
        Log.d(TAG, "Starting capture request for action: " + pendingAction);

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            Log.e(TAG, "MediaProjectionManager is null!");
            finish();
            return;
        }
        Intent captureIntent = mgr.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE);
        Log.d(TAG, "Launched screen capture intent");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent); // مهم: Intent جدید را تنظیم کن
        // در حالت singleInstance، onNewIntent فراخوانی می‌شود.
        // باید پردازش را دوباره شروع کنیم، اما قبل از آن وضعیت قبلی را ذخیره و ریست کنیم.
        // اما چون این Activity فقط برای گرفتن یک نتیجه است، بهتر است آن را ببندیم و دوباره باز کنیم.
        // اما برای جلوگیری از خطای finish() قبل از نابودی، ابتدا وضعیت را ذخیره می‌کنیم.

        // ریست کردن وضعیت برای پردازش Intent جدید
        resultReceived = false;
        pendingResultCode = Activity.RESULT_CANCELED;
        pendingData = null;

        // درخواست جدید را شروع کن
        startCaptureRequest(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_CODE) {
            resultReceived = true;
            pendingResultCode = resultCode;
            pendingData = data;

            // پردازش را بلافاصله انجام نده، اجازه بده onResume فراخوانی شود
            // این کمک می‌کند تا از مشکلات چرخه حیات جلوگیری شود.
        } else {
            Log.w(TAG, "onActivityResult: Unhandled request code: " + requestCode);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called. resultReceived=" + resultReceived + ", pendingResultCode=" + pendingResultCode);

        if (resultReceived) {
            processActivityResult();
        }
    }

    private void processActivityResult() {
        Log.d(TAG, "Processing activity result...");
        if (pendingResultCode == Activity.RESULT_OK && pendingData != null) {
            Log.d(TAG, "Result OK, starting ScreenCaptureService.");
            String action = ScreenCaptureService.ACTION_SCREENSHOT.equals(pendingAction)
                    ? ScreenCaptureService.ACTION_SCREENSHOT
                    : ScreenCaptureService.ACTION_START;

            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.setAction(action);

            // استفاده از putExtra به جای putExtras برای سادگی
            serviceIntent.putExtra("resultCode", pendingResultCode);
            serviceIntent.putExtra("data", pendingData);
            serviceIntent.putExtra("calling_action", pendingAction); // ارسال اکشن اصلی که درخواست را شروع کرده

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                    Log.d(TAG, "Started foreground service.");
                } else {
                    startService(serviceIntent);
                    Log.d(TAG, "Started service.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting service: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "Result not OK or data is null. Result code: " + pendingResultCode);
            // اگر کاربر لغو کرده یا خطایی رخ داده، می‌توان اینجا کاری انجام داد
            // مثلاً نمایش پیام به کاربر یا بستن اکتیویتی
        }

        // در نهایت، اکتیویتی را ببند. این کار باید بعد از اتمام پردازش انجام شود.
        Log.d(TAG, "Finishing CaptureRequestActivity.");
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState called");
        // ذخیره وضعیت فعلی برای بازیابی در صورت نیاز
        outState.putString(STATE_PENDING_ACTION, pendingAction);
        outState.putBoolean(STATE_RESULT_RECEIVED, resultReceived);
        outState.putInt(STATE_PENDING_RESULT_CODE, pendingResultCode);
        outState.putParcelable(STATE_PENDING_DATA, pendingData);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        // در اینجا نیازی به فراخوانی finish() نیست، چون سیستم این کار را هنگام نابود کردن انجام می‌دهد.
        // اگر مشکلی وجود داشت که نیاز به بستن اجباری اکتیویتی بود، شاید لازم باشد آن را اینجا بررسی کرد.
    }
}
