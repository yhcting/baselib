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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.util.Util;

/**
 * Task id and task instance should be one-to-one map in this manager.
 * That is, more than one-task instance that having same task id MUST NOT exist in the
 *   manager.
 * {@code equal()} is used to compare Task id.
 */
public class TaskManager extends TaskManagerBase {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TaskManager.class, Logger.LOGLV_DEFAULT);

    private static final Object sUnspecifiedTaskType = new Object();
    private static final AtomicLong sTmpId = new AtomicLong(0);

    private final Object mTmLock = new Object();
    /* After task is done, task info. are not managed anymore.
     * But, filtered tasked by this functions are preserved
     *   until removing is requested explicitly or # of watched tasks are over maximum.
     */

    /* Tasks that are done(NOT cancelled) and watched. This SHOULD be subset of mTiMap.
     * Tasks having unique id(TidT) are in mWatchedTaskSet.
     * This 'mWathcedTaskSet' is just helper data structure to know order of task added.
     * And removed oldest one first when it reaches to maximum size allowed.
     */
    private final LinkedHashSet<TmTask> mWatchedTaskSet = new LinkedHashSet<>();
    // Map having all - ready/running/watching - tasks handled by this manager
    private final LinkedHashMap<Object, TmTask> mTaskMap = new LinkedHashMap<>();

    private final int mMaxWatchedTask; // maximum number of recorded task.
    private final TaskWatchFilter mWatchFilter;

    private final TaskEventHandler mTaskEventHandler = new TaskEventHandler();

    private class TaskEventHandler extends TaskBase.EventListener<TmTask, Object> {
        @Override
        public void
        onCancelled(@NotNull TmTask task,
                    Object param) {
            //noinspection unchecked
            task.removeEventListener(mTaskEventHandler);
            if (DBG) P.v("onCancelled : " + task.getUniqueName()
                         + ", Param: " + Util.toString(param));
            P.bug(isOwnerThread());
        }

        @Override
        public void
        onPostRun(@NotNull TmTask task,
                  Object result,
                  Exception ex) {
            //noinspection unchecked
            task.removeEventListener(mTaskEventHandler);
            if (DBG) P.v("onPostRun : " + task.getUniqueName()
                         + ", Result: " + Util.toString(result)
                         + ", Except: " + Util.toString(ex));
            P.bug(isOwnerThread());
            handleDoneTask(task, result, ex);
        }
    }

    public interface TaskWatchFilter {
        /**
         * @return True to watching this task. Otherwise false.
         */
        boolean filter(TaskManager tm,
                       TmTask tmTask, Object result, Exception ex);
    }

    public class TaskInfo {
        public final Object tid;
        public final Object ttype;
        public final TmTask task;
        public final Object ttag;

        public TaskInfo(@NotNull TmTask task,
                        @NotNull Object tid,
                        @NotNull Object ttype,
                        Object ttag) {
            this.task = task;
            this.tid = tid;
            this.ttype = ttype;
            this.ttag = ttag;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Private
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Once number of watched task reaches to this value, it will be shrinked to mMaxWatchedTask.
     */
    private int
    limitWatchedTask() {
        return mMaxWatchedTask + mMaxWatchedTask / 2;
    }

    private void
    shrinkWatchedTaskTiLocked() {
        int shrinksz = mWatchedTaskSet.size() - mMaxWatchedTask;
        if (shrinksz <= 0)
            return; // nothing to do

        /* Removing old items until map-size reaches to max allowed.
         * Note that removing from keySet measn removing from hashMap.
         */
        Iterator<TmTask> it = mWatchedTaskSet.iterator();
        while (shrinksz-- > 0) {
            it.next();
            it.remove();
        }
        P.bug(mWatchedTaskSet.size() == mMaxWatchedTask);
    }

    /**
     * @return true if it is watched. Otherwise(removed from this manager) false.
     */
    private boolean
    handleDoneTask(@NotNull TmTask tmTask, Object result, Exception ex) {
        //noinspection unchecked
        TaskInfo ti = getTaskInfo(tmTask);
        P.bug(ti.task == tmTask);
        // Keep watching
        synchronized (mTmLock) {
            if (mTaskMap.get(ti.tid) != tmTask)
                // This task is already replaced with new one, or removed.
                // So, we don't need to manage this one.
                return false;

            P.bug(!mWatchedTaskSet.contains(tmTask));
            if (mMaxWatchedTask > 0
                    && null != mWatchFilter
                    && mWatchFilter.filter(this, tmTask, result, ex)) {
                if (mWatchedTaskSet.size() >= limitWatchedTask())
                    shrinkWatchedTaskTiLocked();
                mWatchedTaskSet.add(tmTask);
                if (DBG) {
                    if (0 == (mWatchedTaskSet.size() % 10))
                        P.v("watchedTaskSet-size: " + mWatchedTaskSet.size());
                }
                return true;
            } else {
                // We don't care return value intentionally
                mTaskMap.remove(ti.tid);
                return false;
            }
        }
    }

    /**
     * [ NOTE - Policy ]
     * It leads to remove information about old task, failing to replace done-task by adding new
     *   one.
     */
    private boolean
    addTask_(@NotNull final TmTask task, @NotNull final TaskInfo ti) {
        P.bug(task == ti.task && task.isReady());
        TmTask ot;
        synchronized (mTmLock) {
            if (DBG) P.v("addTask: " + taskDbgName(task, ti.tid));
            ot = mTaskMap.get(ti.tid);
            if (null != ot
                    && !ot.isDone()) {
                if (DBG) P.w("Running task already exists: " + taskDbgName(task, ti.tid));
                return false;
            } else {
                // Replace old task with newly added task.
                mTaskMap.put(ti.tid, task);
                mWatchedTaskSet.remove(ot);
            }
        }
        task.setTmTag(ti);
        //noinspection unchecked
        task.addEventListener(getOwner(), mTaskEventHandler);
        if (!super.addTask(task)) {
            if (DBG) P.w("Adding task failed: " + taskDbgName(task, ti.tid));
            //noinspection unchecked
            task.removeEventListener(mTaskEventHandler);
            // [ NOTE ] We don't restore mTaskMap and mWatchedTaskSet.
            // This is a kind of policy!!! See comment of this function.
            synchronized (mTmLock) {
                mTaskMap.remove(ti.tid);
            }
            return false;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Protected
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    protected TaskManager(
            @NotNull HandlerAdapter owner,
            int maxJob,
            int maxWatched,
            TaskWatchFilter watchFilter) {
        super(owner, maxJob);
        mMaxWatchedTask = maxWatched;
        mWatchFilter = watchFilter;
    }

    @NotNull
    public static TaskManager
    create(@NotNull HandlerAdapter owner,
           int maxJob,
           int maxWatched,
           TaskWatchFilter watchFilter) {
        return new TaskManager(owner, maxJob, maxWatched, watchFilter);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////

    // ========================================================================
    // Tasks
    // ========================================================================
    public String
    taskDbgName(TmTask tmTask, Object tid) {
        return tmTask.getUniqueName() + "<" + Util.toString(tid) + ">";
    }

    public String
    taskDbgName(TmTask tmTask) {
        //noinspection unchecked
        TaskInfo ti = getTaskInfo(tmTask);
        return taskDbgName(tmTask, ti.tid);
    }

    @NotNull
    public TmTask[]
    getTasks(@NotNull Object typ) {
        LinkedList<TmTask> l = new LinkedList<>();
        synchronized (mTmLock) {
            for(TmTask t : mTaskMap.values()) {
                //noinspection unchecked
                TaskInfo ti = getTaskInfo(t);
                if (ti.ttype.equals(typ))
                    l.add(t);
            }
        }
        return l.toArray(new TmTask[l.size()]);
    }

    public TaskInfo
    getTaskInfo(@NotNull TmTask tmTask) {
        //noinspection unchecked
        return (TaskInfo)tmTask.getTmTag();
    }

    public TmTask
    getTask(@NotNull Object tid) {
        synchronized (mTmLock) {
            return mTaskMap.get(tid);
        }
    }

    public boolean
    addTask(@NotNull final TmTask task, @NotNull Object id, @NotNull Object typ, Object tag) {
        return addTask_(task, new TaskInfo(task, id, typ, tag));
    }

    public boolean
    addTask(@NotNull final TmTask task) {
        return addTask(task, task, sUnspecifiedTaskType, null);
    }

    public boolean
    removeWatchedTask(@NotNull TmTask task) {
        TaskInfo ti = getTaskInfo(task);
        P.bug(task.isDone());
        synchronized (mTmLock) {
            if (DBG) P.v("removeWatchedTask: " + task.getUniqueName());
            if (mTaskMap.get(ti.tid) != task) {
                if (DBG) P.w("Try to removed unknown watched task: " + task.getUniqueName());
                return false;
            }
            // return value ignored intentionally.
            mTaskMap.remove(ti.tid);
            mWatchedTaskSet.remove(task);
            return true;
        }
    }

}
