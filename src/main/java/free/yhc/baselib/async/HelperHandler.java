package free.yhc.baselib.async;

import org.jetbrains.annotations.NotNull;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;

public class HelperHandler {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(HelperHandler.class, Logger.LOGLV_DEFAULT);

    private static HandlerAdapter sHelperOwner = null;

    public static void
    init(@NotNull HandlerAdapter defaultOwner) {
        sHelperOwner = defaultOwner;
    }

    @NotNull
    public static HandlerAdapter
    get() {
        P.bug(null != sHelperOwner);
        assert null != sHelperOwner;
        return sHelperOwner;
    }
}
