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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.util.Util;


// This should be THREAD-SAFE
public class TaskManagerBase {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TaskManagerBase.class, Logger.LOGLV_DEFAULT);

    private static final int UNLIMITED_JOB_NUMBER = 9999999;

    private final HandlerAdapter mOwner;
    private final int mMaxJobs;

    private final TaskEventListener mTaskEventListener = new TaskEventListener();
    private final LinkedHashMap<TaskQEventListener, HandlerAdapter> mListenerMap
            = new LinkedHashMap<>();
    private final HashMap<Object, Object> mTagMap = new HashMap<>();

    // ------------------------------------------------------------------------
    // Members  synchronization is required.
    // ------------------------------------------------------------------------
    private final Object mQLock = new Object();
    /*
     * TaskQ and task state(ThreadEx.State) map.
     * - readyQ : READY
     * - mRunQ : STARTED,
     * - mCancelQ : CANCELLING,
     * Tasks are removed from Q when it reaches to following states
     * : DONE, CANCELLED, TERMINATED or TERMINATED_CANCELLED
     *
     * See ThreadEx.java for task state transition.
     *
     * readyQ manages tasked based on it's task priority.
     * Task having high priority will be started(moved to runQ) with priority.
     */
    private final TaskReadyQ mReadyQ = new TaskReadyQ();
    private final LinkedHashSet<TmTask> mRunQ = new LinkedHashSet<>();

    protected enum TaskQType {
        READY, // readyQ
        RUN, // runQ
    }

    public interface TaskQEventListener {
        void onEvent(
                @NotNull TaskManagerBase tm,
                @NotNull TaskQEvent ev,
                int szReady, int szRun,
                @NotNull TmTask task);
    }

    public enum TaskQEvent {
        ADDED_TO_READY,

        /* Task is removed from ReadyQ before added to RunQ
         * (Usually, task is cancelled before it is started.)
         * This is sent after task event(onCancelled) is sent to listeners.
         */
        REMOVED_FROM_READY,

        /* Task is moved from ReadyQ to RunQ
         * Task may not be started yet.
         * Task is started after moved to RunQ
         */
        MOVED_TO_RUN,

        /* Task is moved from RunQ to CancelQ.
         * This is sent after task event(onCancelled or onPostRun)
         *   is sent to listeners.
         */
        REMOVED_FROM_RUN,
    }

    // ========================================================================
    //
    // ========================================================================
    private class TaskEventListener extends TaskBase.EventListener<TmTask, Object> {
        @Override
        public void
        onCancelled(@NotNull TmTask task,
                    Object param) {
            if (DBG) P.v("onCancelled : " + task.getUniqueName()
                         + ", Param: " + Util.toString(param));
            P.bug(isOwnerThread());
            removeTaskFromRunQ(task);
        }

        @Override
        public void
        onPostRun(@NotNull TmTask task,
                  Object result,
                  Exception ex) {
            if (DBG) P.v("onPostRun : " + task.getUniqueName()
                         + ", Result: " + Util.toString(result)
                         + ", Except: " + Util.toString(ex));
            P.bug(isOwnerThread());
            removeTaskFromRunQ(task);
        }
    }

    private static class TaskReadyQ {
        @SuppressWarnings("unchecked")
        private final LinkedHashSet<TmTask>[] mQ = new LinkedHashSet[ThreadEx.TASK_PRIORITY_MAX];

        @NotNull
        private TmTask
        deQFromLinkedHash(LinkedHashSet<TmTask> hs) {
            Iterator<TmTask> itr = hs.iterator();
            TmTask t = itr.next();
            itr.remove();
            return t;
        }

        private int
        p2i(int pri) {
            // index : 0(max pri) <-----> 9 (min pri)
            return ThreadEx.TASK_PRIORITY_MAX - pri;
        }

        private int
        i2p(int i) {
            return ThreadEx.TASK_PRIORITY_MAX - i;
        }

        TaskReadyQ(){
            for (int i = 0; i < mQ.length; i++)
                mQ[i] = new LinkedHashSet<>();
        }

        int
        size() {
            int n = 0;
            for (LinkedHashSet<TmTask> s : mQ)
                n += s.size();
            return n;
        }

        boolean
        contains(@NotNull TmTask t) {
            for (LinkedHashSet<TmTask> s : mQ) {
                if (s.contains(t))
                    return true;
            }
            return false;
        }

        boolean
        enQ(@NotNull TmTask t) {
            P.bug(ThreadEx.TASK_PRIORITY_MIN <= t.getPriority()
                                   && ThreadEx.TASK_PRIORITY_MAX >= t.getPriority());
            return mQ[p2i(t.getPriority())].add(t);
        }

        boolean
        remove(@NotNull TmTask t) {
            P.bug(ThreadEx.TASK_PRIORITY_MIN <= t.getPriority()
                           && ThreadEx.TASK_PRIORITY_MAX >= t.getPriority());
            return mQ[p2i(t.getPriority())].remove(t);
        }

        // get Next task having highest priority.
        TmTask
        deQ() {
            // index 0 is hightest priority task
            for (LinkedHashSet<TmTask> s : mQ) {
                if (s.size() > 0)
                    return deQFromLinkedHash(s);
            }
            return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Private
    //
    ///////////////////////////////////////////////////////////////////////////

    // ========================================================================
    // Notify functions
    // ========================================================================
    private void
    notifyTaskQEventQLocked(@NotNull final TaskQEvent ev,
                            @NotNull final TmTask task) {
        final int szReady = mReadyQ.size();
        final int szRun = mRunQ.size();

        synchronized (mListenerMap) {
            for (final TaskQEventListener l : mListenerMap.keySet()) {
                HandlerAdapter h = mListenerMap.get(l);
                if (!h.post(new Runnable() {
                    @Override
                    public void run() {
                        //noinspection unchecked
                        l.onEvent(TaskManagerBase.this, ev, szReady, szRun, task);
                    }
                })) { P.bug(); }
            }
        }
    }

    // ========================================================================
    //
    // ========================================================================
    private void
    balanceTaskQ() {
        final TmTask task;
        synchronized (mQLock) {
            /* There are two jobs.
             * - Updating taskQ
             * - Notifying to listeners
             *
             * Important point is that these two jobs should be protected in the same block,
             *   because order of above two jobs should be same!
             * If above two jobs are protected by different lock, following issue can be seen.
             * +------------------------------------------------
             * |   < Thread1 >              < Thread2 >
             * |   addToReady
             * |                             moveToRun
             * |                             notifyMoveToRun
             * |  notifyAddToReady
             * |
             * v
             */
            if (mReadyQ.size() > 0
                    && mRunQ.size() < mMaxJobs) {
                task = mReadyQ.deQ();
                if (DBG) {
                    P.bug(!mReadyQ.contains(task));
                    if (ThreadEx.State.READY != task.getState()) {
                        P.e(task.getUniqueName() + ": " + task.getState().name());
                        P.bug(false);
                    }
                }
                //noinspection unchecked
                task.addEventListener(getOwner(), mTaskEventListener);
                mRunQ.add(task); // EnQ(append) to runQ
                notifyTaskQEventQLocked(TaskQEvent.MOVED_TO_RUN, task);
            } else
                task = null;
        }

        if (null != task) {
            if (!task.start()) {
                P.bug(false); // Something unexpected!
            }
        }
    }

    private void
    removeTaskFromRunQ(@NotNull final TmTask task) {
        P.bug(isOwnerThread());
        //noinspection unchecked
        task.removeEventListener(mTaskEventListener);
        synchronized (mQLock) {
            if (mRunQ.remove(task)) {
                // if task is done or cancelled at runQ
                notifyTaskQEventQLocked(TaskQEvent.REMOVED_FROM_RUN, task);
            } else P.bug(false); // This is totally unexpected!
        }
        balanceTaskQ();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // protected
    //
    ///////////////////////////////////////////////////////////////////////////
    /* TaskGroup has lot's of tasks to run. And it waits until all sub-tasks are done.
     * So, if TaskGroup is added to RunQ, it occupies one slot and just wating.
     * If TaskManager has only one slot, TaskGroup never finished (a kind of DeadLock!)
     * That's why TaskGroup doens't added to 'ReadyQ or RunQ' and just started.
     */
    protected boolean
    addTaskGroup(@NotNull TaskGroup tg) {
        // tg.getTmTag != null means, it's already added to
        if (this != tg.getTmTag()) {
            P.bug();
            return false;
        }
        // Just start it, and we don't need to care anymore!
        return tg.start();
    }

    protected boolean
    cancelTaskGroup(TaskGroup tg, Object param, boolean interrupt) {
        P.bug(this == tg.getTmTag());
        return tg.cancel(param, interrupt);
    }

    protected boolean
    addTask_(@NotNull final TmTask task) {
        /* This class module is designed as MT(multi-thread)-safe module.
         * But, this is NOT tested enough for MT cases yet.
         */
        if (DBG) {
            P.bug(ThreadEx.State.READY == task.getState());
            P.v("Add task: <" + task.getName() + ">");
        }

        /* Why below code is written? I think this is useless... I am missing something??
        if (task.getOwner() != mOwner) {
            if (DBG) P.w("Thread owner of task is different!");
            return false;
        }
        */

        synchronized (mQLock) {
            if (!mReadyQ.enQ(task)) {
                if (DBG) P.w("Adding duplicated task");
                return false;
            }
            notifyTaskQEventQLocked(TaskQEvent.ADDED_TO_READY, task);
        }
        balanceTaskQ();

        return true;
    }

    protected boolean
    cancelTask_(@NotNull final TmTask task, Object cancelParam, boolean interrupt) {
        if (DBG) P.v("Cancel task: <" + task.getName() + ">");
        if (null == contains(task)) {
            if (DBG) P.w("Cancel non-existing task");
            return false;
        }
        synchronized (mQLock) {
            if (mReadyQ.remove(task)) {
                // Before added to runQ, task event listener is NOT added yet.
                // So, we should handle post-run process here!
                notifyTaskQEventQLocked(TaskQEvent.REMOVED_FROM_READY, task);
            }
        }
        //noinspection unchecked
        task.cancel(cancelParam, interrupt);
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Protected
    //
    ///////////////////////////////////////////////////////////////////////////
    protected TaskManagerBase(@NotNull HandlerAdapter owner,
                              int maxjob) {
        if (0 > maxjob) {
            if (DBG) P.w("Minus maxjob value is handled as 'unlimited'");
            maxjob = UNLIMITED_JOB_NUMBER;
        }
        mOwner = owner;
        mMaxJobs = maxjob;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * @param owner Owner handler(thread) managing background tasks.
     *              All listeners registered at this manager and BGTasks are run on this handler's context.
     * @param maxjob Maximum number of jobs that can be run in parallel.
     */
    @NotNull
    public static TaskManagerBase
    create(@NotNull HandlerAdapter owner,
           int maxjob) {
        return new TaskManagerBase(owner, maxjob);
    }

    @NotNull
    public final HandlerAdapter
    getOwner() {
        return mOwner;
    }

    public final int
    getMaxJobs() {
        return mMaxJobs;
    }

    public final boolean
    isOwnerThread(@NotNull Thread thread) {
        return thread == mOwner.getThread();
    }

    public final boolean
    isOwnerThread() {
        return isOwnerThread(Thread.currentThread());
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

    /**
     * Requires QLock
     * @return null if task is NOT in this manager. Otherwise Q type in where task is.
     */
    public final TaskQType
    contains(@NotNull TmTask t) {
        synchronized (mQLock) {
            if (mReadyQ.contains(t))
                return TaskQType.READY;
            else if(mRunQ.contains(t))
                return TaskQType.RUN;
            return null;
        }
    }

    // ========================================================================
    //
    // ========================================================================
    /**
     * Requires QLock
     * @return Number of tasks.
     */
    public int
    size() {
        synchronized (mQLock) {
            return mReadyQ.size() + mRunQ.size();
        }
    }

    /**
     * Add task to ready Q.
     * [WARN] Adding same task more than once leads to undefined operation!
     * Task owner should be same with owner of this manager.
     * If there is available task slot, task will be started as soon as it is added.
     */
    public boolean
    addTask(@NotNull final TmTask task) {
        if (task instanceof TaskGroup)
            return addTaskGroup((TaskGroup)task);
        else
            return addTask_(task);
    }

    /**
     * Requires QLock
     *
     * Task should be belongs to this manager.
     */
    public boolean
    cancelTask(@NotNull final TmTask task, Object cancelParam, boolean interrupt) {
        if (task instanceof TaskGroup)
            return cancelTaskGroup((TaskGroup)task, cancelParam, interrupt);
        else
            return cancelTask_(task, cancelParam, interrupt);
    }

    public boolean
    cancelTask(@NotNull final TmTask task, Object param) {
        return cancelTask(task, param, task.getInterruptOnCancel());
    }

    public boolean
    cancelTask(@NotNull final TmTask task) {
        return cancelTask(task, null);
    }

    public boolean
    addTaskQEventListener(
            @NotNull HandlerAdapter listenerOwner,
            @NotNull TaskQEventListener listener) {
        synchronized (mListenerMap) {
            if (mListenerMap.containsKey(listener)) {
                if (DBG) P.w("Add duplicated listener: ignored");
                return false;
            } else {
                mListenerMap.put(listener, listenerOwner);
                return true;
            }
        }
    }

    public boolean
    addTaskQEventListener(@NotNull TaskQEventListener listener) {
        return addTaskQEventListener(getOwner(), listener);
    }

    public boolean
    removeTaskQEventListener(@NotNull TaskQEventListener listener) {
        synchronized (mListenerMap) {
            if (null == mListenerMap.remove(listener)) {
                if (DBG) P.w("Remove non-existing listener");
                return false;
            }
        }
        return true;
    }
}
