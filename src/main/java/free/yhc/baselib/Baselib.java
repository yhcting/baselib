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

import free.yhc.baselib.adapter.LoggerAdapter;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.adapter.NetConnAdapter;
import free.yhc.baselib.async.HelperHandler;
import free.yhc.baselib.net.NetConn;

/**
 * Class for main & top-most level interface.
 */
public class Baselib {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    // Logger is available after library initalization.
    private static Logger P = null;

    private static boolean sInitialized = false;

    /*
     * Function to verify that library is initialized.
     *
     * Most modules(actually all modules) create Logger module for logging as class static.
     * So, the best place in where this function is called, is constructure of 'Logger' class.
     */
    static void
    verifyLibReady() {
        if (!sInitialized)
            throw new AssertionError("Baselib is NOT initialized");
    }

    /**
     * Initialize library.
     * This should be called before using any other modules in this library.
     */
    public static void
    initLibrary(@NotNull HandlerAdapter defaultOwner,
                @NotNull LoggerAdapter loggerAdapter,
                @NotNull NetConnAdapter netConnAdapter) {
        // initLibrary is called. and library will be initialized anyway.
        // (Initialized or assert!)
        // And we know what we are doing in this library.
        // So, for convenience - especially to use Logger - let's say 'initialized' at early stage.
        if (sInitialized)
            throw new AssertionError();
        sInitialized = true;
        // Logger SHOULD be initialized at the first!
        Logger.init(loggerAdapter);

        HelperHandler.init(defaultOwner);
        NetConn.init(netConnAdapter);

        P = Logger.create(Baselib.class, Logger.LOGLV_DEFAULT);
        if (DBG) P.v("initLibrary is done");
    }
}
