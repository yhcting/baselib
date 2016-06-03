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

import java.util.HashMap;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;

public abstract class Task<ResultT> extends TaskBase<ResultT> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(Task.class, Logger.LOGLV_DEFAULT);

    private static final int PUBLISH_PROGRESS_MIN_INTERVAL = 500; // 500ms

    public static final long INVALID_PROGRESS = -987654321987654321L; // Arbitrary small number.

    private final HashMap<Object, Object> mTagMap = new HashMap<>();

    private long mPublishProgressMinInterval = PUBLISH_PROGRESS_MIN_INTERVAL ;
    private long mLastPublishProgressTm = 0;
    private long mMaxProgress = INVALID_PROGRESS; // can be minus or 0.
    private long mLastPublishProgress = INVALID_PROGRESS;

    ///////////////////////////////////////////////////////////////////////////
    //
    // Templates
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Protected and override
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    public Task(@NotNull String name,
                @NotNull HandlerAdapter owner,
                int priority,
                boolean interruptOnCancel) {
        super(name, owner, priority, interruptOnCancel);
    }

    public Task() {
        super(Task.class.getSimpleName(),
              HelperHandler.get(),
              ThreadEx.TASK_PRIORITY_NORM,
              true);
    }

    public static abstract class Builder<B extends Builder, T extends Task>
            extends TaskBase.Builder<B, T> {
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void
    publishProgressInit(long maxProg) {
        if (DBG) P.v(getLogPrefix() + "MaxProg: " + maxProg);
        mLastPublishProgressTm = System.currentTimeMillis();
        mLastPublishProgress = 0;
        mMaxProgress = maxProg;
        super.publishProgressInit(maxProg);
    }

    @Override
    public void
    publishProgress(long prog) {
        if (prog > mMaxProgress) {
            if (DBG) P.i("Progress(" + prog + ") is larger than max progress(" + mMaxProgress + ")");
            prog = mMaxProgress;
        }
        long now = System.currentTimeMillis();
        if (now - mLastPublishProgressTm < mPublishProgressMinInterval
                || prog == mLastPublishProgress)
            return; // Progress already published just right before! Or there is no progress.
        mLastPublishProgressTm = now;
        mLastPublishProgress = prog;
        super.publishProgress(prog);
    }

    // ========================================================================
    //
    // ========================================================================
    public final long
    getMaxProgress() {
        return mMaxProgress;
    }

    public final long
    getCurrentProgress() {
        return mLastPublishProgress;
    }

    public Object
    setTag(Object k, Object v) {
        synchronized (mTagMap) {
            return mTagMap.put(k, v);
        }
    }

    public Object
    getTag(Object k) {
        synchronized (mTagMap) {
            return mTagMap.get(k);
        }
    }

    public Object
    removeTag(Object k) {
        synchronized (mTagMap) {
            return mTagMap.remove(k);
        }
    }

    public boolean
    addEventListener(
            @NotNull HandlerAdapter listenerOwner,
            @NotNull final EventListener<? extends TmTask, ? super ResultT> listener,
            final boolean withProgressNotice) {
        if (!addEventListener(listenerOwner, listener))
            return false;
        if (!(isProgressStarted() && withProgressNotice))
            return true;
        // Notify current progress info to new listener.
        if (!listenerOwner.post(new Runnable() {
            @Override
            public void
            run() {
                //noinspection unchecked
                ((EventListener)listener).onProgressInit(Task.this, getMaxProgress());
                //noinspection unchecked
                ((EventListener)listener).onProgress(Task.this, getCurrentProgress());
            }
        })) { P.bug(); }
        return true;
    }
}
