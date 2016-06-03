package free.yhc.baselib.adapter;

import org.jetbrains.annotations.NotNull;

import free.yhc.baselib.Logger;

public interface LoggerAdapter {
    void write(@NotNull Logger.LogLv lv, @NotNull String m);
}
