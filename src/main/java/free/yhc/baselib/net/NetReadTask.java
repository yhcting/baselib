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

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URLConnection;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.HandlerAdapter;
import free.yhc.baselib.async.TmTask;

public class NetReadTask extends TmTask<NetReadTask.Result> {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(NetReadTask.class, Logger.LOGLV_DEFAULT);

    private static final int DEFAULT_NET_BUFFER_SIZE = 16 * 1024;

    private final NetConn mNetConn;
    private final int mBufferSize;
    private final OutputStream mOStream;

    public static class Result {
        public final long bytes;
        public Result(Result r) {
            this.bytes = r.bytes;
        }
        public Result(long bytes) {
            this.bytes = bytes;
        }
    }

    /**
     * If content length < 0, then -bytes(< 0) read from network are reported as progress.
     * And progress for bytes read more than value-content-length(>= 0), is NOT reported.
     *
     * Exceptions :
     *   IOException, InterruptedException, InterruptedIOException
     *   ConnectException
     */
    @NotNull
    private Result
    doAsync_() throws InterruptedException, IOException {
        mNetConn.connect(new NetConn.Adapter() {
            @Override
            public boolean isCancel() {
                return NetReadTask.this.isCancel();
            }
        });

        URLConnection net = mNetConn.getUrlConnection();
        long contentLength = net.getContentLength();
        if (DBG) P.v("Length of http content is: " + contentLength);
        publishProgressInit(contentLength);
        publishProgress(0);

        BufferedInputStream bis = new BufferedInputStream(net.getInputStream());
        byte data[] = new byte[mBufferSize];
        long total = 0;
        int  count;
        while (true) {
            // Check network state as often as possible to confirm that
            //   network what user want to use is available.
            if (!mNetConn.isNetConnected())
                throw new ConnectException("Network is unavailable");
            if (isCancel())
                throw new InterruptedException("Task is cancelled");
            if (-1 == (count = bis.read(data)))
                break; // end of stream
            P.bug(count >= 0);
            mOStream.write(data, 0, count);
            total += count;
            if (contentLength < 0)
                publishProgress(-total);
            else if (total <= contentLength)
                publishProgress(total);
        }
        mOStream.flush();
        return new Result(total);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public NetReadTask(
            @NotNull String name,
            @NotNull HandlerAdapter owner,
            @NotNull NetConn netConn,
            @NotNull OutputStream ostream,
            int bufferSize,
            int priority,
            boolean interruptOnCancel) {
        super(name, owner, priority, interruptOnCancel);
        P.bug(bufferSize > 0);
        mNetConn = netConn;
        mBufferSize = bufferSize;
        mOStream = ostream;
    }

    public static class Builder<B extends Builder>
            extends TmTask.Builder<B, NetReadTask> {
        protected final NetConn mNetConn;
        protected final OutputStream mOstream;
        protected int mBufferSize = NetReadTask.DEFAULT_NET_BUFFER_SIZE;

        public Builder(
                @NotNull NetConn netConn,
                @NotNull OutputStream ostream) {
            super();
            mName = NetReadTask.class.getSimpleName();
            mNetConn = netConn;
            mOstream = ostream;
        }

        @NotNull
        public B
        setBufferSize(int size) {
            mBufferSize = size;
            //noinspection unchecked
            return (B)this;
        }

        @Override
        @NotNull
        public NetReadTask
        create() {
            return new NetReadTask(mName,
                                   mOwner,
                                   mNetConn,
                                   mOstream,
                                   mBufferSize,
                                   mPriority,
                                   mInterruptOnCancel);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @NotNull
    public final NetConn
    getNetConn() {
        return mNetConn;
    }

    public final int
    getBufferSize() {
        return mBufferSize;
    }

    @NotNull
    public final OutputStream
    getOutputStream() {
        return mOStream;
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
            return doAsync_();
        } finally {
            mNetConn.disconnect();
            mOStream.close();
        }
    }
}
