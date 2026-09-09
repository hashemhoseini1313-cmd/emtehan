package org.example.screenrecorder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

public class NetworkMonitor {
    public static volatile boolean isConnected = true;

    public static void startMonitoring(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            // چک وضعیت اولیه
            checkCurrentNetwork(cm);

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
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
                        boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                              capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                        isConnected = hasInternet;
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void checkCurrentNetwork(ConnectivityManager cm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                    isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                } else {
                    isConnected = false;
                }
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                isConnected = netInfo != null && netInfo.isConnected();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
