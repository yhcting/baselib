package free.yhc.baselib.adapter.android;

import org.jetbrains.annotations.NotNull;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.LoggerAdapter;

public class ALoggerAdapter implements LoggerAdapter {
    private static final String TAG = "Baselib";

    @Override
    public void
    write(@NotNull Logger.LogLv lv, @NotNull String m) {
        switch (lv) {
        case V: android.util.Log.v(TAG, m); break;
        case D: android.util.Log.d(TAG, m); break;
        case I: android.util.Log.i(TAG, m); break;
        case W: android.util.Log.w(TAG, m); break;
        case E: android.util.Log.e(TAG, m); break;
        case F: android.util.Log.wtf(TAG, m); break;
        default:
            throw new AssertionError();
        }
    }
}
