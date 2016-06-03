/******************************************************************************
 * Copyright (C) 2016
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of free.yhc.baselib
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.baselib.async;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.util.Util;

/*
 * TaskBase manages event listeners.
 * And it calls early-[Step] and late-[Step] callbacks.
 * Others not-related with event listeners and event-step-callbacks, SHOULD NOT be implemented here.
 * Use inheritance to expand class functions!
 */
public abstract class TaskBase<ResultT> extends ThreadEx<ResultT> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TaskBase.class, Logger.LOGLV_DEFAULT);

    private final Object mCancelLock = new Object();
    private Object mCancelParam = null;
    // default interrupt value of this task.
    private final boolean mInterruptOnCancel; // default(false)

    // Synchronization is required.
    private final LinkedHashMap<
            EventListener<
                    ? extends TaskBase,
                    ? super ResultT>,
            HandlerAdapter> mEventListenerMap = new LinkedHashMap<>();

    public static class EventListener<TaskT extends TaskBase, ResultT> {
        @SuppressWarnings("unused")
        public void onStarted(@NotNull TaskT task) { }
        @SuppressWarnings("unused")
        public void onPostRun(@NotNull TaskT task,
                              ResultT result,
                              Exception ex) { }
        @SuppressWarnings("unused")
        public void onCancel(@NotNull TaskT task, Object param) { }
        @SuppressWarnings("unused")
        public void onCancelled(@NotNull TaskT task, Object param) { }
        @SuppressWarnings("unused")
        public void onProgressInit(@NotNull TaskT task, long maxProgress) { }
        @SuppressWarnings("unused")
        public void onProgress(@NotNull TaskT task, long progress) { }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Privates
    //
    ///////////////////////////////////////////////////////////////////////////
    private void
    setCancelParamLocked(Object param) {
        mCancelParam = param;
    }

    private void
    setCancelParam(Object param) {
        synchronized (mCancelLock) {
            setCancelParamLocked(param);
        }
    }

    private Object
    getCancelParam() {
        synchronized (mCancelLock) {
            return mCancelParam;
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    // Templates
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * This will be called prior to all other registered listeners
     */
    protected void
    onEarlyStarted() {
        if (DBG) P.v(getLogPrefix() + "");
    }

    /**
     * This will be called after all other registered listeners
     */
    protected void
    onLateStarted() {
        if (DBG) P.v(getLogPrefix() + "");
    }

    /**
     * This will be called prior to all other registered listeners
     */
    protected void
    onEarlyPostRun (ResultT result, Exception ex) {
        if (DBG) P.v(getLogPrefix() + "Result: " + Util.toString(result));
    }

    /**
     * This will be called after all other registered listeners
     */
    protected void
    onLatePostRun (ResultT result, Exception ex) {
        if (DBG) P.v(getLogPrefix()
                     + "Result: " + Util.toString(result)
                     + ", Except: " + Util.toString(ex));
    }

    /**
     * This will be called prior to all other registered listeners
     */
    protected void
    onEarlyCancel(boolean started, Object param) {
        if (DBG) P.v(getLogPrefix()
                     + "Started: " + started
                     + ", CancelParam: " + Util.toString(param));
    }

    /**
     * This will be called after all other registered listeners
     */
    protected void
    onLateCancel(boolean started, Object param) {
        if (DBG) P.v(getLogPrefix()
                     + "Started: " + started
                     + ", CancelParam: " + Util.toString(param));
    }

    /**
     * This will be called prior to all other registered listeners
     */
    protected void
    onEarlyCancelled(Exception ex, Object param) {
        if (DBG) P.v(getLogPrefix()
                     + "Exception: " + Util.toString(ex)
                     + ", CancelParam: " + Util.toString(param));
    }

    /**
     * This will be called after all other registered listeners
     */
    protected void
    onLateCancelled(Exception ex, Object param) {
        if (DBG) P.v(getLogPrefix()
                     + "Exception: " + Util.toString(ex)
                     + ", CancelParam: " + Util.toString(param));
    }

    protected void
    onEarlyProgressInit(long maxProg) {
        if (DBG) P.v(getLogPrefix() + "maxProg: " + maxProg);
    }

    protected void
    onLateProgressInit(long maxProg) {
        if (DBG) P.v(getLogPrefix() + "maxProg: " + maxProg);
    }

    protected void
    onEarlyProgress(long prog) {
        if (DBG) P.v(getLogPrefix() + "prog: " + prog);
    }

    protected void
    onLateProgress(long prog) {
        if (DBG) P.v(getLogPrefix() + "prog: " + prog);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Override
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    final protected void
    onStarted() {
        super.onStarted();
        onEarlyStarted();
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        //noinspection unchecked
                        l.onStarted(TaskBase.this);
                    }
                })) { P.bug(); }
            }
        }
        onLateStarted();
    }

    @Override
    final protected void
    onPostRun(final ResultT result, final Exception ex) {
        onEarlyPostRun(result, ex);
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        //noinspection unchecked
                        l.onPostRun(TaskBase.this, result, ex);
                    }
                })) { P.bug(); }
            }
        }
        onLatePostRun(result, ex);
        super.onPostRun(result, ex);
    }

    @Override
    final protected void
    onCancel(boolean started) {
        super.onCancel(started);
        final Object cancelParam = getCancelParam();
        onEarlyCancel(started, cancelParam);
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        P.bug(cancelParam == TaskBase.this.getCancelParam());
                        //noinspection unchecked
                        l.onCancel(TaskBase.this, cancelParam);
                    }
                })) { P.bug(); }
            }
        }
        onLateCancel(started, cancelParam);
    }

    @Override
    final protected void
    onCancelled(Exception ex) {
        final Object cancelParam = getCancelParam();
        onEarlyCancelled(ex, cancelParam);
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        P.bug(cancelParam == TaskBase.this.getCancelParam());
                        //noinspection unchecked
                        l.onCancelled(TaskBase.this, cancelParam);
                    }
                })) { P.bug(); }
            }
        }
        onLateCancelled(ex, cancelParam);
        super.onCancelled(ex);
    }

    @Override
    final protected void
    onProgressInit(final long maxProg) {
        super.onProgressInit(maxProg);
        onEarlyProgressInit(maxProg);
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        //noinspection unchecked
                        l.onProgressInit(TaskBase.this, maxProg);
                    }
                })) { P.bug(); }
            }
        }
        onLateProgressInit(maxProg);
    }

    @Override
    final protected void
    onProgress(final long prog) {
        super.onProgress(prog);
        onEarlyProgress(prog);
        synchronized (mEventListenerMap) {
            for (final EventListener l : mEventListenerMap.keySet()) {
                HandlerAdapter h = mEventListenerMap.get(l);
                P.bug(null != h);
                assert h != null;
                if (!h.post(new Runnable() {
                    @Override
                    public void
                    run() {
                        //noinspection unchecked
                        l.onProgress(TaskBase.this, prog);
                    }
                })) { P.bug(); }
            }
        }
        onLateProgress(prog);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * DANGEROUS FUNCTION
     * DO NOT USE if you are not sure what your are doing!
     * Make event listener list empty.
     * (Same as unregistering all listeners.)
     */
    void
    clearEventListener() {
        synchronized (mEventListenerMap) {
            if (DBG) P.w(getLogPrefix() + "clear event listener");
            mEventListenerMap.clear();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Instantiation
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    public TaskBase(@NotNull String name,
                    @NotNull HandlerAdapter owner,
                    int priority,
                    boolean interruptOnCancel) {
        super(name, owner, priority);
        mInterruptOnCancel = interruptOnCancel;
    }

    public static abstract class Builder<B extends Builder, T extends TaskBase>
            extends ThreadEx.Builder<B, T> {
        protected boolean mInterruptOnCancel = true;

        @NotNull
        public B
        setInterruptOnCancel(boolean val) {
            mInterruptOnCancel = val;
            //noinspection unchecked
            return (B)this;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////
    public final boolean
    cancel(Object param, boolean interrupt) {
        synchronized (mCancelLock) {
            if (cancel(interrupt)) {
                setCancelParamLocked(param);
                return true;
            } else
                return false;
        }
    }

    public final boolean
    getInterruptOnCancel() {
        return mInterruptOnCancel;
    }

    public final boolean
    cancel(Object param) {
        return cancel(param, mInterruptOnCancel);
    }

    public final boolean
    cancel() {
        return cancel(null, mInterruptOnCancel);
    }


    /**
     * Register event listener with it's key value.
     * Newly added event listener will be added to the last of listener list.
     * (event listener will be notified in order of listener list.)
     *
     * Key value is used to find event listener (onEvent).
     * Several event listener may share one key value.
     * Event callback will be called on handler thread message loop.
     */
    public boolean
    addEventListener(
            @NotNull HandlerAdapter listenerOwner,
            @NotNull EventListener<? extends TaskBase, ? super ResultT> listener) {
        synchronized (mEventListenerMap) {
            if (mEventListenerMap.containsKey(listener)) {
                if (DBG) P.w(getLogPrefix() + "add duplicated listener: ignored");
                return false;
            } else {
                mEventListenerMap.put(listener, listenerOwner);
                return true;
            }
        }
    }

    /**
     *  Event callback will be called on task owner thread message loop.
     */
    public boolean
    addEventListener(
            @NotNull EventListener<? extends TaskBase, ? super ResultT> listener) {
        return addEventListener(getOwner(), listener);
    }

    /**
     * Unregister event listener whose key and listener match.
     * @param listener listener value
     */
    public boolean
    removeEventListener(
            @NotNull EventListener<? extends TaskBase, ? super ResultT> listener) {
        synchronized (mEventListenerMap) {
            if (null == mEventListenerMap.remove(listener)) {
                if (DBG) P.w(getLogPrefix() + "remove non-existing listener");
                return false;
            }
        }
        return true;
    }
}
