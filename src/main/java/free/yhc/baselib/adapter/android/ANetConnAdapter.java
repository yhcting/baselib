package free.yhc.baselib.adapter.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;

import free.yhc.baselib.adapter.NetConnAdapter;
import free.yhc.baselib.net.NetConn;

public class ANetConnAdapter implements NetConnAdapter {
    private final Context mAppContext;

    private static int
    mapCm(int cmtype) {
        switch (cmtype) {
        case ConnectivityManager.TYPE_MOBILE:
            return NetConn.TYPE_MOBILE;
        case ConnectivityManager.TYPE_WIFI:
            return NetConn.TYPE_WIFI;
        case ConnectivityManager.TYPE_BLUETOOTH:
            return NetConn.TYPE_BLUETOOTH;
        case ConnectivityManager.TYPE_ETHERNET:
            return NetConn.TYPE_ETHERNET;
        case ConnectivityManager.TYPE_WIMAX:
            return NetConn.TYPE_WIMAX;
        case ConnectivityManager.TYPE_DUMMY:
        case ConnectivityManager.TYPE_MOBILE_DUN:
        case ConnectivityManager.TYPE_VPN:
        default:
            return NetConn.TYPE_UNKNOWN;
        }
    }

    public ANetConnAdapter(@NonNull Context appContext) {
        mAppContext = appContext;
    }

    public int
    getConnectedNetworks() {
        ConnectivityManager cm = (ConnectivityManager)mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        int ret = 0;
        /* - This is for API level 21 or later
        for (Network n : cm.getAllNetworks()) {
            NetworkInfo ni = cm.getNetworkInfo(n);
        */
        for (NetworkInfo ni : cm.getAllNetworkInfo()) {
            assert ni != null;
            if (ni.isConnected()) {
                ret |= mapCm(ni.getType());
            }
        }
        return ret;
    }
}
