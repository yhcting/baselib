package free.yhc.baselib.adapter;

import org.jetbrains.annotations.NotNull;

public interface HandlerAdapter {
    /**
     * Get thread on where this MsgHandler is working.
     * (Context in where this MsgHandler is running).
     */
    @NotNull Thread getThread();

    /**
     * Add the Runnable to message queue. The Runnable should be executed at MsgHandler's context
     *   at near future.
     * @return true if the Runnable is successfully placed in to message queue.
     *         false otherwise.
     */
    boolean post(@NotNull Runnable r);
}
