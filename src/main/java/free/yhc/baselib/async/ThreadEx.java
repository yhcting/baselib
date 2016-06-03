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

/*
 * [ History ]
 * Why this class is implemented even if AsyncTask is available at Android?
 * AsyncTask had critical issue at Gingerbread and below.
 * Issue is, canceling task by calling 'cancel(boolean xx)' stops background task
 *   in the middle of running. That is, sometimes 'doInBackground()' function doens't return
 *   when task is cancelled.
 * Even worse, the moment of 'onCancelled()' is called, is NOT predictable.
 * It may be called even if background task is still running (before terminating)
 *
 * All above issues seem to be fixed at ICS.
 *
 * Additional advantage is, I can control it even at Java thread level :).
 *
 * [ WARNING ]
 * -----------
 * DO NOT make this CLASS DIRTY!
 * That is, DO NOT ADD more FUNTIONALITIES.
 * Main features of this class are
 *   - thread state management
 *   - call-back on interesting moment. (preRun, postRun, onCancelled etc)"
 * If more complicated feature is required,
 *   INHERITE THIS CLASS AND MAKE NEW CLASS FOR IT!
 * -----------
 */

package free.yhc.baselib.async;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.exception.BadStateException;
import free.yhc.baselib.util.Util;

public abstract class ThreadEx<ResultT> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(ThreadEx.class, Logger.LOGLV_DEFAULT);

    private static final AtomicLong sId = new AtomicLong(0);

    @SuppressWarnings("unused")
    public static final int TASK_PRIORITY_MIN = Thread.MIN_PRIORITY;  // 1
    @SuppressWarnings("unused")
    public static final int TASK_PRIORITY_MAX = Thread.MAX_PRIORITY;  // 10
    public static final int TASK_PRIORITY_NORM = Thread.NORM_PRIORITY; // 5
    @SuppressWarnings("unused")
    public static final int TASK_PRIORITY_MIDLOW = 3;
    @SuppressWarnings("unused")
    public static final int TASK_PRIORITY_MIDHIGH = 7;

    private final long mId;
    private final String mLogPrefix;

    private final Thread mThread;
    private final HandlerAdapter mOwner;
    // In some cases, little bit complex operation may be required for state transition.
    // So, using only atomic-reference is not enough.
    private final Object mStateLock = new Object();
    private final AtomicBoolean mProgInit = new AtomicBoolean(false);
    // These two values are valid only after BG task is done.
    // See bgRun() for details.
    private final AtomicReference<ResultT> mResult = new AtomicReference<>(null);
    private final AtomicReference<Exception> mExcept = new AtomicReference<>(null);

    private State mState = State.READY;



    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////

    /*
     * Thread state transition
     * =======================
     *
     * Notation
     * --------
     * [] : state
     * () : action
     *
     *
     * --> [ READY ] -----------------------+(cancel)
     *         |                            |<onCancel>
     *         v                            |
     *     [ STARTED ]                      |
     *          | (onStarted)               |
     *        +-+-+                         |
     *        |   |(cancel)                 |
     *        |   +--> [ CANCELLING ]       |
     *        v               | <onCancel>  v
     *     [ DONE ]           +----->[ CANCELLED ]
     *        | <onPostRun>               | <onCancelled>
     *        v                           v
     *   [ TERMINATED ]        [ TERMINATED_CANCELLED ]
     *
     *
     * <onProgressInit> and <onProgress> MUST be called only at STARTED and CANCELLING state.
     *
     * NOTE:
     * - All callbacks are run on Owner's context.
     * - Order of callbacks are guaranteed.
     *   So, only following 3-cases are allowed.
     *     . <onCancel> -> <onCancelled>
     *     . <onStarted> -> <onCancel> -> <onCancelled>
     *     . <onStarted> -> <onPostRun>
     */
    public enum State {
        READY, // before background job is running
        STARTED, // background job is started but not running yet.
        CANCELLING, // job is cancelling.
        CANCELLED, // background job is done, but post processing - onCancelled() - is not done yet.
        DONE, // background job is done, but post processing - onPostRun() - is not done yet.
        TERMINATED, // background job and according post processing is completely finished.
        TERMINATED_CANCELLED
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Internal functions.
    //
    ///////////////////////////////////////////////////////////////////////////
    private void
    setStateLocked(State st) {
        if (DBG) P.v(getLogPrefix() + "State change: " + mState.name() + " -> " + st.name());
        mState = st;
    }

    private void
    setState(State st) {
        synchronized (mStateLock) {
            setStateLocked(st);
        }
    }

    @NotNull
    private State
    getStateLocked() {
        return mState;
    }

    private void
    postToOwner(Runnable r) {
        if (!getOwner().post(r))
            P.bug();
    }

    private void
    postOnCancelled(final Exception ex) {
        if (DBG) P.v(getLogPrefix() + "");
        postToOwner(new Runnable() {
            @Override
            public void
            run() {
                onCancelled(ex);
                setState(State.TERMINATED_CANCELLED);
            }
        });
    }

    private void
    postOnPostRun(final ResultT r,
                  final Exception ex) {
        if (DBG) P.v(getLogPrefix() + "");
        postToOwner(new Runnable() {
            @Override
            public void
            run() {
                onPostRun(r, ex);
                setState(State.TERMINATED);
            }
        });
    }

    private void
    bgRun() {
        ResultT r = null;
        Exception ex = null;
        try {
            synchronized (mStateLock) {
                if (State.CANCELLING == getStateLocked())
                    return;
                P.bug(State.STARTED == getStateLocked(), getLogPrefix());
            }
            r = doAsync();
        } catch (Exception e){
            ex = e;
        } finally {
            boolean cancel = false;
            mResult.set(r);
            mExcept.set(ex);
            synchronized (mStateLock) {
                if (State.CANCELLING == getStateLocked()) {
                    setStateLocked(State.CANCELLED);
                    cancel = true;
                } else {
                    P.bug(State.STARTED == getStateLocked(), getLogPrefix());
                    setStateLocked(State.DONE);
                }
            }
            if (cancel)
                postOnCancelled(ex);
            else
                postOnPostRun(r, ex);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Protected template functions.
    //
    ///////////////////////////////////////////////////////////////////////////
    protected final String
    getLogPrefix() {
        return mLogPrefix;
    }

    protected void
    onStarted() {
        if (DBG) P.v(getLogPrefix() + "");
    }

    protected void
    onPostRun(ResultT result,
              Exception ex) {
        if (DBG) P.v(getLogPrefix()
                     + "Result: " + Util.toString(result)
                     + ", Except: " + Util.toString(ex));
    }

    /**
     * Called to cancel background task.
     * @param started true if background task is started and then cancel is requested.
     */
    protected void
    onCancel(boolean started) {
        if (DBG) P.v(getLogPrefix() + "Started: " + started);
    }

    protected void
    onCancelled(Exception ex) {
        if (DBG) P.v(getLogPrefix() + "Except: " + Util.toString(ex));
    }

    // This is called before 'onProgress'
    protected void
    onProgressInit(long maxProg) {
        if (DBG) P.v(getLogPrefix() + "max Progress: " +  maxProg);
    }

    protected void
    onProgress(long prog) {
        if (DBG) P.v(getLogPrefix() + "Progress: " +  prog);
    }

    // ========================================================================
    // Abstracts
    // ========================================================================
    protected abstract ResultT
    doAsync() throws Exception;


    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * [NOTE] Constructor SHOULD NOT be public!
     *
     * @param name Thread name
     * @param owner Owner MsgHandler. All other action functions except for 'doAsync' will be run on
     *              given owner MsgHandler's context.
     * @param priority Java thread priority LOW[1, 10]High
     */
    public ThreadEx(@NotNull String name,
                    @NotNull HandlerAdapter owner,
                    int priority) {
        P.bug(TASK_PRIORITY_MIN <= priority && TASK_PRIORITY_MAX >= priority);
        mId = sId.getAndIncrement();
        mLogPrefix = name + "(" + mId + "): ";
        mThread = new Thread(name) {
            @Override
            public void
            run() {
                bgRun();
            }
        };
        mThread.setPriority(priority);
        mOwner = owner;
    }

    public static abstract class Builder<B extends Builder, T extends ThreadEx> {
        protected String mName = ThreadEx.class.getSimpleName();
        protected HandlerAdapter mOwner = HelperHandler.get();
        protected int mPriority = ThreadEx.TASK_PRIORITY_NORM;

        @NotNull
        public B
        setName(@NotNull String name) {
            mName = name;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public B
        setOwner(@NotNull HandlerAdapter owner) {
            mOwner = owner;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public B
        setPriority(int priority) {
            mPriority = priority;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public abstract T create();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * @return 'false' if task is already cancelling, cancelled or done.(That is nothing to cancel.)
     */
    public final boolean
    cancel(final boolean interrupt) {
        final boolean started;
        synchronized (mStateLock) {
            switch (getStateLocked()) {
            case READY:
                started = false;
                break;
            case STARTED:
                started = true;
                break;
            default:
                return false;
            }
            setStateLocked(State.CANCELLING);
            postToOwner(new Runnable() {
                @Override
                public void
                run() {
                    if (DBG) P.v(getLogPrefix() + "before onCancel()");
                    // NOTE
                    // onCancel() SHOULD be HERE!
                    // The reason is that state of "BG job" should be in 'CANCELLING' while onCancel is called.
                    // Putting onCancel() outside of this critical section, breaks above rule because
                    //   background thread may change state into CANCELLED.
                    //
                    // Issue is, onCancel() may take lots of time.
                    onCancel(started);
                    if (started) {
                        if (interrupt)
                            mThread.interrupt();
                        // State transition is handled after background task is finished.
                    } else {
                        // cancel is requested for not-started thread.
                        // It means, there is no chance to handle post-cancel process after background task is finished.
                        // Therefore, handle all post-cancel process here.
                        setState(State.CANCELLED);
                        postOnCancelled(null);
                    }
                }
            });
        }
        return true;
    }

    /**
     * @return 'false' if task is already started - run() cannot be called more than once!
     */
    public final boolean
    start() {
        synchronized (mStateLock) {
            if (State.READY != getStateLocked())
                return false;
            setStateLocked(State.STARTED);
            postToOwner(new Runnable() {
                @Override
                public void
                run() {
                    onStarted();
                }
            });
        }
        mThread.start();
        return true;
    }

    /**
     * NOTE: Function is returned after {@code DONE} or {@code CANCELLED} state.
     * NOT after {@code TERMINATED_XXXX} state!
     * Function returns after sending {@code DONE} or {@code CANCELLED} to owner.
     * Exceptions comes from {@code doAsync} are thrown, but all other exceptions are handled as
     *   {@code AssertionError}.
     */
    public final ResultT
    startSync() throws Exception {
        synchronized (mStateLock) {
            if (State.READY != getStateLocked()) {
                if (DBG) P.w("Task is NOT in ready state");
                throw new BadStateException();
            }
            setStateLocked(State.STARTED);
            postToOwner(new Runnable() {
                @Override
                public void run() {
                    onStarted();
                }
            });
        }
        bgRun();
        if (null != getException())
            throw getException();
        return getResult();
    }

    public final long
    getId() {
        return mId;
    }

    @NotNull
    public final String
    getUniqueName() {
        return getName() + "(" + getId() + ")";
    }

    public final ResultT
    getResult() {
        return mResult.get();
    }

    public final Exception
    getException() {
        return mExcept.get();
    }

    @NotNull
    public final String
    getName() {
        return mThread.getName();
    }

    public final void
    setName(@NotNull String name) {
        mThread.setName(name);
    }

    public int
    getPriority() {
        return mThread.getPriority();
    }

    @NotNull
    public final State
    getState() {
        synchronized (mStateLock) {
            return getStateLocked();
        }
    }

    /**
     * Can task be started?
     */
    public final boolean
    isReady() {
        return State.READY == getState();
    }

    /**
     * Is task running normally?
     * If task is in CANCELLING state, this returns false.
     */
    public final boolean
    isRunning() {
        return State.STARTED == getState();
    }

    /**
     * Is task is in processing state?(STARTED, CANCELLING)
     */
    public final boolean
    isActive() {
        synchronized (mStateLock) {
            return State.STARTED == getStateLocked()
                    || State.CANCELLING == getStateLocked();
        }
    }

    /**
     * Is background task is finished?
     */
    public final boolean
    isDone() {
        synchronized (mStateLock) {
            return State.DONE == getStateLocked()
                    || State.CANCELLED == getStateLocked()
                    || State.TERMINATED == getStateLocked()
                    || State.TERMINATED_CANCELLED == getStateLocked();
        }
    }

    /**
     * Is task cancelled by user? (by calling 'cancel()')
     * This function returns 'true' even if task is under CANCELLING state.
     */
    public final boolean
    isCancel() {
        synchronized (mStateLock) {
            return State.CANCELLING == getStateLocked()
                    || State.CANCELLED == getStateLocked()
                    || State.TERMINATED_CANCELLED == getStateLocked();
        }
    }

    public final boolean
    isProgressStarted() {
        return mProgInit.get();
    }

    public final boolean
    isTerminated() {
        synchronized (mStateLock) {
            return State.TERMINATED == getStateLocked()
                   || State.TERMINATED_CANCELLED == getStateLocked();
        }
    }

    @NotNull
    public final HandlerAdapter
    getOwner() {
        return mOwner;
    }

    public final boolean
    isOwnerThread(@NotNull Thread thread) {
        return thread == getOwner().getThread();
    }

    public final boolean
    isOwnerThread() {
        return isOwnerThread(Thread.currentThread());
    }

    public final boolean
    isInterrupted() {
        return mThread.isInterrupted();
    }

    // ========================================================================
    //
    // ========================================================================

    /**
     * Init progress should be published before publishing any progress.
     * @param maxProg max progress value.
     */
    public void
    publishProgressInit(final long maxProg) {
        // This function should be called only once!!
        P.bug(!mProgInit.getAndSet(true) && isActive(), getLogPrefix());
        postToOwner(new Runnable() {
            @Override
            public void
            run() {
                onProgressInit(maxProg);
            }
        });
    }

    public void
    publishProgress(final long prog) {
        P.bug(mProgInit.get() && isActive(), getLogPrefix());
        postToOwner(new Runnable() {
            @Override
            public void
            run() {
                onProgress(prog);
            }
        });
    }
}
