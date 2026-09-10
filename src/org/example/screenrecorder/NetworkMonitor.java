package org.example.screenrecorder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

public class NetworkMonitor {
    public static volatile boolean isConnected = true;
    private static ConnectivityManager connectivityManager;

    public static void startMonitoring(Context context) {
        try {
            if (context == null) return;
            connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;

            // چک وضعیت اولیه
            checkState(context);

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    isConnected = true;
                }

                @Override
                public void onLost(Network network) {
                    isConnected = false;
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    if (capabilities != null) {
                        isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean checkState(Context context) {
        try {
            if (connectivityManager == null && context != null) {
                connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            }
            if (connectivityManager == null) {
                return isConnected;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = connectivityManager.getActiveNetwork();
                if (activeNet == null) {
                    isConnected = false;
                    return false;
                }
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNet);
                isConnected = (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            } else {
                android.net.NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
                isConnected = (netInfo != null && netInfo.isConnected());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isConnected;
    }

    // متد اورلود شده برای فراخوانی بدون پارامتر از سمت پایتون
    public static boolean checkState() {
        return checkState(null);
    }

    public static void checkCurrentNetwork(ConnectivityManager cm) {
        connectivityManager = cm;
        checkState(null);
    }
}
