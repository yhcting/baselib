package free.yhc.baselib.adapter.android;

import android.os.Handler;

import org.jetbrains.annotations.NotNull;

import free.yhc.baselib.adapter.HandlerAdapter;

/**
 * MsgHandler for Android.
 */
public class AHandlerAdapter implements HandlerAdapter {
    private final Handler mHandler;
    public AHandlerAdapter(@NotNull Handler h) {
        mHandler = h;
    }

    public Handler
    getHandler() {
        return mHandler;
    }

    @Override
    @NotNull
    public Thread
    getThread() {
        return mHandler.getLooper().getThread();
    }

    @Override
    public boolean
    post(@NotNull Runnable r) {
        return mHandler.post(r);
    }
}
