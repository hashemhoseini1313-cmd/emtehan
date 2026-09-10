package org.example.screenrecorder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log; // برای لاگ کردن

public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor"; // برای لاگ کردن

    // volatile ضروری است تا تغییرات آن در ترد های مختلف بلافاصله دیده شود
    public static volatile boolean isConnected = false; // بهتر است با false شروع شود و وضعیت اولیه چک شود

    // برای نگهداری آخرین وضعیت تشخیص داده شده
    private static volatile boolean lastKnownConnectionState = false;

    public static void startMonitoring(Context context) {
        Log.d(TAG, "Starting network monitoring...");
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                Log.e(TAG, "ConnectivityManager is null. Cannot start monitoring.");
                return;
            }

            // چک وضعیت اولیه در لحظه راه‌اندازی
            checkCurrentNetwork(cm);
            lastKnownConnectionState = isConnected; // ذخیره وضعیت اولیه
            Log.d(TAG, "Initial connection state: " + isConnected);


            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    // .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) // این را در اینجا اضافه نکنید، در onCapabilitiesChanged چک می شود
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "Network available: " + network);
                    // onAvailable فقط نشان می دهد شبکه ای پیدا شده، باید قابلیت هایش را هم چک کنیم
                    // برای اطمینان بیشتر، اینجا وضعیت را true نمی گذاریم و منتظر onCapabilitiesChanged می مانیم
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    Log.d(TAG, "Network lost: " + network);
                    isConnected = false;
                    lastKnownConnectionState = false;
                    // اینجا باید حتماً وضعیت را false بگذاریم
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    super.onCapabilitiesChanged(network, capabilities);
                    Log.d(TAG, "Network capabilities changed for network " + network);

                    if (capabilities != null) {
                        // کلیدی ترین بخش: چک کردن هم NET_CAPABILITY_INTERNET و هم NET_CAPABILITY_VALIDATED
                        // NET_CAPABILITY_VALIDATED یعنی شبکه واقعا به اینترنت دسترسی دارد
                        boolean hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                        boolean isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                        boolean currentConnectionState = hasInternetCapability && isValidated;

                        Log.d(TAG, "Network state: hasInternet=" + hasInternetCapability + ", isValidated=" + isValidated + ", currentConnectionState=" + currentConnectionState);

                        // فقط اگر وضعیت تغییر کرده باشد، متغیر را بروز رسانی کن
                        if (currentConnectionState != lastKnownConnectionState) {
                            isConnected = currentConnectionState;
                            lastKnownConnectionState = currentConnectionState;
                            Log.d(TAG, "Connection status updated to: " + isConnected);
                            // اینجا می توانید یک Event bus یا broadcast sender راه اندازی کنید
                            // تا بخش پایتون (main.py) از تغییر وضعیت مطلع شود
                            // در حال حاضر، main.py هر ثانیه isConnected را چک می کند
                        } else {
                            Log.d(TAG, "Connection status unchanged. Keeping: " + isConnected);
                        }
                    } else {
                        Log.w(TAG, "NetworkCapabilities is null.");
                        // اگر capabilities null بود، فرض می کنیم اتصال اینترنت معتبر نیست
                        if (lastKnownConnectionState) { // فقط اگر قبلا وصل بودیم
                            isConnected = false;
                            lastKnownConnectionState = false;
                            Log.d(TAG, "Connection status updated to false due to null capabilities.");
                        }
                    }
                }

                @Override
                public void onLinkPropertiesChanged(Network network, android.net.LinkProperties linkProperties) {
                    super.onLinkPropertiesChanged(network, linkProperties);
                    Log.d(TAG, "Network link properties changed for network " + network);
                    // می توانید اینجا نیز وضعیت اتصال را چک کنید اگرچه معمولا onCapabilitiesChanged کافی است
                }
            });
            Log.d(TAG, "NetworkCallback registered successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error during network monitoring setup: ", e);
            isConnected = false; // در صورت خطا، اتصال را قطع فرض کن
            lastKnownConnectionState = false;
        }
    }

    public static void checkCurrentNetwork(ConnectivityManager cm) {
        Log.d(TAG, "Checking current network state...");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                    boolean hasInternet = false;
                    if (caps != null) {
                        boolean hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                        boolean isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                        hasInternet = hasInternetCapability && isValidated;
                        Log.d(TAG, "Current network (API M+): hasInternet=" + hasInternetCapability + ", isValidated=" + isValidated + ", Final=" + hasInternet);
                    } else {
                        Log.d(TAG, "Current network (API M+): NetworkCapabilities is null.");
                    }
                    isConnected = hasInternet;
                } else {
                    Log.d(TAG, "Current network (API M+): No active network found.");
                    isConnected = false;
                }
            } else {
                // برای API های قدیمی تر از M
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                isConnected = (netInfo != null && netInfo.isConnected());
                Log.d(TAG, "Current network (Pre-M): isConnected=" + isConnected + " from " + netInfo);
            }
            Log.d(TAG, "Initial state set by checkCurrentNetwork: " + isConnected);
            lastKnownConnectionState = isConnected; // وضعیت اولیه را هم اینجا ثبت کن
        } catch (Exception e) {
            Log.e(TAG, "Error checking current network: ", e);
            isConnected = false; // در صورت خطا، اتصال را قطع فرض کن
            lastKnownConnectionState = false;
        }
    }
}
