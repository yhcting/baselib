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

import java.util.concurrent.atomic.AtomicReference;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;

public abstract class TmTask<ResultT> extends Task<ResultT> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(TmTask.class, Logger.LOGLV_DEFAULT);

    // Special member only for 'TaskManager(Base)' module.
    // Used on in 'Package Private' scope.
    private final AtomicReference<Object> mTmTag = new AtomicReference<>(null);

    ///////////////////////////////////////////////////////////////////////////
    //
    // Templates
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Override
    //
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////
    final void
    setTmTag(Object tag) {
        mTmTag.set(tag);
    }

    final Object
    getTmTag() {
        return mTmTag.get();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Builder
    //
    ///////////////////////////////////////////////////////////////////////////
    public TmTask(@NotNull String name,
                  @NotNull HandlerAdapter owner,
                  int priority,
                  boolean interruptOnCancel) {
        super(name, owner, priority, interruptOnCancel);
    }

    public TmTask() {
        super();
    }

    public static abstract class Builder<B extends Builder, T extends TmTask>
            extends Task.Builder<B, T> {
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Public interfaces
    //
    ///////////////////////////////////////////////////////////////////////////
}
