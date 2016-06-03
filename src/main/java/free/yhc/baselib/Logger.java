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

package free.yhc.baselib;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import free.yhc.baselib.adapter.LoggerAdapter;

public class Logger {
    public static final boolean DBG_DEFAULT = true;
    public static final LogLv LOGLV_DEFAULT = LogLv.V;

    private static final boolean DBG = DBG_DEFAULT;
    private static Logger P = null;

    private static LoggerAdapter sLoggerAdapter = null;

    private final Class<?> mCls;
    private final LogLv mLv;

    public enum LogLv{
        V ("[V]", 6),
        D ("[D]", 5),
        I ("[I]", 4),
        W ("[W]", 3),
        E ("[E]", 2),
        F ("[F]", 1);

        public final String prefstr; // prefix string
        public final int val;  // priority
        LogLv(String prefstr, int val) {
            this.prefstr = prefstr;
            this.val = val;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    private static void
    log(@NotNull Class<?> cls,
        @NotNull LogLv clslv,
        @NotNull LogLv lv,
        @NotNull String msg) {
        if (clslv.val < lv.val)
            return;

        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        msg = ste.getClassName() + "/" + ste.getMethodName() + "(" + ste.getLineNumber() + ") : " + msg;
        sLoggerAdapter.write(lv, msg);
    }

    static void
    init(@NotNull LoggerAdapter lgwr) {
        sLoggerAdapter = lgwr;
        P = create(Logger.class, LOGLV_DEFAULT);
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    private Logger(@NotNull Class<?> cls, @NotNull LogLv lv) {
        Baselib.verifyLibReady();
        mCls = cls;
        mLv = lv;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public static Logger
    create(@NotNull Class<?> cls, @NotNull LogLv lv) {
        return new Logger(cls, lv);
    }

    public String
    stackTrace(Exception e) {
        if (null == e)
            return "<null>";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintStream prs = new PrintStream(baos)) {
            e.printStackTrace(prs);
            return baos.toString();
        } catch (IOException ex) {
            throw new AssertionError(); // Unexpected!
        }
    }

    // For logging
    public void v() { log(mCls, mLv, LogLv.V, ""); }
    public void v(@NotNull String msg) { log(mCls, mLv, LogLv.V, msg); }
    public void d() { log(mCls, mLv, LogLv.D, ""); }
    public void d(@NotNull String msg) { log(mCls, mLv, LogLv.D, msg); }
    public void i() { log(mCls, mLv, LogLv.I, ""); }
    public void i(@NotNull String msg) { log(mCls, mLv, LogLv.I, msg); }
    public void w() { log(mCls, mLv, LogLv.W, ""); }
    public void w(@NotNull String msg) { log(mCls, mLv, LogLv.W, msg); }
    public void e() { log(mCls, mLv, LogLv.E, ""); }
    public void e(@NotNull String msg) { log(mCls, mLv, LogLv.E, msg); }
    public void f() { log(mCls, mLv, LogLv.F, ""); }
    public void f(@NotNull String msg) { log(mCls, mLv, LogLv.F, msg); }
    public void bug(boolean cond, @NotNull String message) {
        if (!cond)
            throw new AssertionError(message);
    }
    public void bug(boolean cond) {
        bug(cond, "");
    }
    public void bug() { bug(false); }
    public void bug(Throwable e) { throw new AssertionError(e); }
}
