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

package free.yhc.baselib.net;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import org.jetbrains.annotations.NotNull;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;

public class NetDownloadTask extends NetReadTask {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(NetDownloadTask.class, Logger.LOGLV_DEFAULT);

    private static final int DEFAULT_DOWNLOAD_BUFFER_SIZE = 16 * 1024;

    private final File mTmpFile;
    private final File mOutFile;

    public static class Result extends NetReadTask.Result {
        public final File outFile;
        Result(NetReadTask.Result r, File outFile) {
            super(r);
            this.outFile = outFile;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    protected void
    onEarlyCancelled(Exception ex, Object param) {
        super.onEarlyCancelled(ex, param);
        // return value is ignored intentionally.
        // We already tried our best.
        //noinspection ResultOfMethodCallIgnored
        getTmpFile().delete();
        //noinspection ResultOfMethodCallIgnored
        getOutFile().delete(); // Task may be cancelled after renaming.
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * {@code tmpFile} SHOULD BE DIFFERENT from {@code outFile}
     */
    public NetDownloadTask(
            @NotNull String name,
            @NotNull HandlerAdapter owner,
            @NotNull NetConn netConn,
            @NotNull File tmpFile,
            @NotNull File outFile,
            int netReadBufferSize,
            int priority,
            boolean interruptOnCancel)
            throws IOException {
        super(name,
              owner,
              netConn,
              new BufferedOutputStream(new FileOutputStream(tmpFile)),
              netReadBufferSize,
              priority,
              interruptOnCancel);
        P.bug(netReadBufferSize > 0);
        mTmpFile = tmpFile;
        mOutFile = outFile;
    }

    public static class Builder<B extends Builder>
            extends NetReadTask.Builder<B> {
        protected static final OutputStream mDummyOstream = new OutputStream() {
            @Override
            public void write(int oneByte){ }
        };

        protected final File mOutfile;
        protected File mTmpFile;

        public Builder(
                @NotNull NetConn netConn,
                @NotNull File outFile) throws IOException {
            super(netConn, mDummyOstream);
            mName = NetDownloadTask.class.getSimpleName();
            mOutfile = outFile;
            mTmpFile = outFile;
            mBufferSize = NetDownloadTask.DEFAULT_DOWNLOAD_BUFFER_SIZE;
        }

        @NotNull
        public B
        setTmpFile(@NotNull File tmpFile) {
            mTmpFile = tmpFile;
            //noinspection unchecked
            return (B)this;
        }

        @Override
        @NotNull
        public NetDownloadTask
        create() {
            try {
                return new NetDownloadTask(mName,
                                           mOwner,
                                           mNetConn,
                                           mTmpFile,
                                           mOutfile,
                                           mBufferSize,
                                           mPriority,
                                           mInterruptOnCancel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @NotNull
    public final File
    getTmpFile() {
        return mTmpFile;
    }

    @NotNull
    public final File
    getOutFile() {
        return mOutFile;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @Override
    protected Result
    doAsync() throws IOException, InterruptedException {
        try {
            NetReadTask.Result rr = super.doAsync();
            // Check again. Anyway it's no harmful :)
            if (isCancel())
                throw new InterruptedIOException("Task is cancelled.");
            else {
                boolean tmpOutEq = false;
                try {
                    tmpOutEq = mTmpFile.getCanonicalPath().equals(mOutFile.getCanonicalPath());
                } catch (IOException ignored) {}
                if (!tmpOutEq
                    && !mTmpFile.renameTo(mOutFile)) {
                    // return value is ignored intentionally.
                    // We already tried our best.
                    //noinspection ResultOfMethodCallIgnored
                    // This is a kind of fail.
                    throw new IOException("Fail renaming.");
                }
                return new Result(rr, mOutFile);
            }
        } catch (IOException | InterruptedException e) {
            //noinspection ResultOfMethodCallIgnored
            mTmpFile.delete();
            throw e;
        }
    }
}
