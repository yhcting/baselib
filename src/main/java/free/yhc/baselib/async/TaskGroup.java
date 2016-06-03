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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.util.Util;

public class TaskGroup extends TmTask<Task> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TaskGroup.class, Logger.LOGLV_DEFAULT);

    private final Object mTasksLock = new Object();
    private final AtomicReference<Task[]> mTasks = new AtomicReference<>(null);
    private final boolean mStopOnException;
    private boolean mTgDoneFlag = false;
    private final Object mTgDoneCond = new Object();
    private final TaskEventListener mTaskEventListener = new TaskEventListener();
    private final AtomicReference<Task> mResTask = new AtomicReference<>(null);

    private final PrepareDoAsync mPrepareDoAsync;
    private final FinalizeDoAsync mFinalizeDoAsync;

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public interface PrepareDoAsync {
        /**
         * Executed on async-thread context.
         * @return Additional task to run in this TaskGroup.
         */
        Task[] prepare(@NotNull TaskGroup tg);
    }

    public interface FinalizeDoAsync {
        /**
         * Executed on async-thread context.
         */
        void finalize(@NotNull TaskGroup tg, Task result, Exception ex) throws Exception;
    }

    private class TaskEventListener extends Task.EventListener<Task, Object> {
        /* All callbacks in this listener run on owner thread.
         * Therefore, we don't need to worry about synchronization!
         */
        private final AtomicInteger _mDoneTasks = new AtomicInteger(0);
        private final AtomicInteger _mStartedTasks = new AtomicInteger(0);

        TaskEventListener() { }

        void
        setStartedTasksCnt(int startedTasks) {
            // This function is called before running any callbacks of tasks.
            P.bug(Thread.currentThread() == TaskGroup.this.getOwner().getThread());
            P.bug(0 == _mDoneTasks.get());
            _mStartedTasks.set(startedTasks);
        }

        int
        getRemainsTaskCnt() {
            return _mStartedTasks.get() - _mDoneTasks.get();
        }

        private void
        handleTaskDone(@NotNull Task task) {
            P.bug(Thread.currentThread() == TaskGroup.this.getOwner().getThread());
            // This is last task of this task group
            int doneCnt = _mDoneTasks.incrementAndGet();
            TaskGroup.this.publishProgress(doneCnt);
            if (doneCnt >= _mStartedTasks.get()) {
                synchronized (mTgDoneCond) {
                    mTgDoneFlag = true;
                    mTgDoneCond.notifyAll();
                }
            }
        }

        @Override
        public void
        onPostRun(@NotNull Task task,
                  Object result,
                  Exception ex) {
            P.bug(Thread.currentThread() == TaskGroup.this.getOwner().getThread());
            if (null != ex
                    && mStopOnException
                    && null == mResTask.get()) {
                TaskGroup.this.cancel(null, true);
                mResTask.set(task);
            }
            handleTaskDone(task);
        }

        @Override
        public void
        onCancelled(@NotNull Task task, Object param) {
            handleTaskDone(task);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    private void
    startTasks(final Task[] tasks) {
        TaskGroup.this.publishProgressInit(tasks.length);
        TaskGroup.this.publishProgress(0);
        if (!getOwner().post(new Runnable() {
            @Override
            public void
            run() {
                if (TaskGroup.this.isCancel())
                    return; // already cancelled do nothing!
                /* cancelTask(called onEarlyCancel) and this is run on owner context.
                 * So, re-setting mTasks here is thread-safe.
                 */
                mTasks.set(tasks);
                int startedCnt = 0;
                for (Task t : tasks) {
                    //noinspection unchecked
                    t.addEventListener(getOwner(), mTaskEventListener);
                    if (startTask(t))
                        startedCnt++;
                }
                TaskGroup.this.publishProgress(tasks.length - startedCnt);
                /* startedCnt is set before any taskDone post callback because
                 *   they are run on same(owner) thread context.
                 */
                mTaskEventListener.setStartedTasksCnt(startedCnt);
            }
        })) { P.bug(); }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    protected void
    onEarlyCancel(boolean started, Object param) {
        super.onEarlyCancel(started, param);
        P.bug(Thread.currentThread() == TaskGroup.this.getOwner().getThread());
        for (Task t : mTasks.get())
            // return value is ignored intentionally.
            cancelTask(t, param);
    }

    /**
     * @return Task that caused stop execution. That is, task in which exception is raised
     *         and stopOnException == true.
     * @throws InterruptedException
     */
    @Override
    protected Task
    doAsync() throws Exception {
        /* NOTE: For canceling task, 'onEarlyCancel' and 'isCancel' guarantee that
         * "below 'isCancel' is NOT executed after 'onEarlyCancel'".
         */
        InterruptedException ex = null;
        try {
            Task[] moreTasks = null;
            if (null != mPrepareDoAsync)
                moreTasks = mPrepareDoAsync.prepare(this);
            Task[] ts = mTasks.get();
            if (null != moreTasks)
                ts = Util.concatArry(ts, moreTasks);
            if (isCancel())
                throw new InterruptedException();
            startTasks(ts);
            synchronized (mTgDoneCond) {
                while (!mTgDoneFlag)
                    mTgDoneCond.wait();
                P.bug(0 == mTaskEventListener.getRemainsTaskCnt());
            }
        } catch (InterruptedException e) {
            ex = e;
        }

        Task resultTask = mResTask.get();
        if (null != mFinalizeDoAsync)
            mFinalizeDoAsync.finalize(this, resultTask, ex);
        else if (null != ex)
            throw ex;

        return resultTask;
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    protected boolean
    startTask(Task task) {
        // start task MUST be success at any case!
        if (!task.start())
            P.bug(false);
        return true;
    }

    protected boolean
    cancelTask(Task task, Object param) {
        // cancel task may fail, but, it's not critical.
        return task.cancel(param, true);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * {@code tasks} and {@code tis} are one-to-one map. So, array length should be same.
     *
     * @param stopOnException
     *         In case a task finished with exception, remain tasks keep starting if false,
     *         otherwise, current running tasks are cancelled and remain tasks are not started.
     */
    public TaskGroup(
            @NotNull String name,
            @NotNull HandlerAdapter owner,
            @NotNull Task[] tasks,
            boolean stopOnException,
            PrepareDoAsync prepare,
            FinalizeDoAsync finalize) {
        /* [ IMPORTANT ]
         * Taskgroup should wait until all sub-tastks are done.
         * If interrupted on 'wait', TaskGroup may finished before sut-tasks are not done yet.
         * So, 'wait' SHOULD NOT be interrupted by default!
         */
        super(name, owner, ThreadEx.TASK_PRIORITY_NORM, false);
        mTasks.set(tasks);
        mStopOnException = stopOnException;
        mPrepareDoAsync = prepare;
        mFinalizeDoAsync = finalize;
    }

    public static class Builder<B extends Builder>
            extends TmTask.Builder<B, TaskGroup> {
        protected Task[] mTasks = new Task[0];
        protected boolean mStopOnException = false;
        protected PrepareDoAsync mPrepareDoAsync = null;
        protected FinalizeDoAsync mFinalizeDoAsync = null;

        public Builder() {
            super();
            mName = TaskGroup.class.getSimpleName();
        }

        @NotNull
        public B
        setTasks(@NotNull Task[] tasks) {
            mTasks = tasks;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public B
        setStopOnException(boolean val) {
            mStopOnException = val;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public B
        setPrepareDoAsync(PrepareDoAsync prepare) {
            mPrepareDoAsync = prepare;
            //noinspection unchecked
            return (B)this;
        }

        @NotNull
        public B
        setFinalizeDoAsync(FinalizeDoAsync finalize) {
            mFinalizeDoAsync = finalize;
            //noinspection unchecked
            return (B)this;
        }

        @Override
        @NotNull
        public TaskGroup
        create() {
            return new TaskGroup(mName,
                                 mOwner,
                                 mTasks,
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
    public boolean
    getStopOnException() {
        return mStopOnException;
    }

    public Task[]
    getTasks() {
        return mTasks.get();
    }
}
