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

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;

public class TmTaskGroup extends TaskGroup {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TmTaskGroup.class, Logger.LOGLV_DEFAULT);

    // Special member only for 'TaskManager(Base)' module.
    // Used on in 'Package Private' scope.
    protected final TaskManagerBase mTm;

    ///////////////////////////////////////////////////////////////////////////
    //
    // Override
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    protected boolean
    startTask(Task task) {
        return mTm.addTask((TmTask)task);
    }

    @Override
    protected boolean
    cancelTask(Task task, Object param) {
        return mTm.cancelTask((TmTask)task, param);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    public TmTaskGroup(
            @NotNull String name,
            @NotNull HandlerAdapter owner,
            @NotNull TaskManagerBase tm,
            @NotNull TmTask[] tasks,
            boolean stopOnExcep,
            PrepareDoAsync prepare,
            FinalizeDoAsync finalize) {
        super(name, owner, tasks, stopOnExcep, prepare, finalize);
        mTm = tm;
    }

    public static class Builder<B extends Builder>
            extends TaskGroup.Builder<B> {
        private final TaskManagerBase mTm;

        public Builder(@NotNull TaskManagerBase tm) {
            mName = TmTaskGroup.class.getSimpleName();
            mTm = tm;
        }

        @NotNull
        public B
        setTasks(@NotNull TmTask[] tasks) {
            mTasks = tasks;
            //noinspection unchecked
            return (B)this;
        }

        @Override
        @NotNull
        public B
        setTasks(@NotNull Task[] tasks) {
            throw new AssertionError();
        }

        @Override
        @NotNull
        public TmTaskGroup
        create() {
            return new TmTaskGroup(mName,
                                   mOwner,
                                   mTm,
                                   (TmTask[])mTasks,
                                   mStopOnException,
                                   mPrepareDoAsync,
                                   mFinalizeDoAsync);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public TaskManagerBase
    getTaskManager() {
        return mTm;
    }

}
