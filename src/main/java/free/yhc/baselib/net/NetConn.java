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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.NetConnAdapter;

public class NetConn {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(NetConn.class, Logger.LOGLV_DEFAULT);

    private static NetConnAdapter sAdapter = null;

    private static final int RETRY_COUNT= 2;
    private static final int INTERVAL_BETWEEN_RETRY = 500;
    private static final int CONN_TIMEOUT = 2500;

    private static NetConn sInvalidDummy = new NetConn();

    // Adapter for network type of ConnectivityManager.
    public static final int TYPE_ANY = 0xffffffff; // 0 means anything available.
    public static final int TYPE_MOBILE = 1 << 1;
    public static final int TYPE_WIFI = 1 << 2;
    public static final int TYPE_WIMAX = 1 << 3;
    public static final int TYPE_ETHERNET = 1 << 4;
    public static final int TYPE_BLUETOOTH = 1 << 5;
    public static final int TYPE_UNKNOWN = 1 << 31;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);
    private final int mNetType;
    // This should be initialized at constructor of sub-class.
    protected final URLConnection mUrlConn;

    public interface Adapter {
        boolean isCancel();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public static void
    init(@NotNull NetConnAdapter adapter) {
        sAdapter = adapter;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    private NetConn() {
        mUrlConn = null;
        mNetType = TYPE_ANY;
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    public NetConn(
            int netType,
            @NotNull URLConnection urlConn)
            throws IOException {
        mUrlConn = urlConn;
        mNetType = netType;
        if (!isNetConnected()) {
            if (DBG) P.w("Network is not available");
            throw new ConnectException("Network is unavailable");
        }
    }

    public static class Builder {
        protected final URL mUrl;

        protected int mNetType = NetConn.TYPE_ANY;
        protected InetSocketAddress mIa = null;

        @NotNull
        protected URLConnection
        createUrlConnection(@NotNull Proxy.Type proxyType)
                throws IOException {
            P.bug(verify());
            if (null == mIa)
                return mUrl.openConnection();
            else
                return mUrl.openConnection(new Proxy(proxyType, mIa));
        }

        protected boolean
        verify() {
            return true;
        }

        protected Builder(@NotNull URL url) {
            mUrl = url;
        }

        // ========================================================================
        //
        // ========================================================================
        @NotNull
        public Builder
        setNetType(int netType) {
            P.bug(NetConn.TYPE_ANY == netType
                           || NetConn.TYPE_WIFI == netType);
            mNetType = netType;
            return this;
        }

        @NotNull
        public Builder
        setProxy(@NotNull String host, int port) {
            P.bug(0 <= port && port <= 65535);
            mIa = new InetSocketAddress(host, port);
            return this;
        }

        @NotNull
        public NetConn
        create() throws IOException  {
            P.bug(verify());
            // By default SOCKS proxy is used.
            URLConnection urlConn = createUrlConnection(Proxy.Type.SOCKS);
            return new NetConn(mNetType, urlConn);
        }

        // ========================================================================
        //
        // ========================================================================
        @NotNull
        public static Builder
        newBuilder(@NotNull URL url) throws MalformedURLException {
            switch (url.getProtocol().toLowerCase()) {
            case "http":
            case "https":
                return NetConnHttp.Builder.newBuilder(url);
            case "file":
            case "ftp":
                return new Builder(url);
            default:
                throw new MalformedURLException("Unsupport protocol: " + url.getProtocol());
            }
        }

        @NotNull
        public static Builder
        newBuilder(@NotNull String url) throws MalformedURLException {
            return newBuilder(new URL(url));
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
    @NotNull
    public static NetConn
    getInvalidDummy() {
        return sInvalidDummy;
    }

    /**
     * @param netType NetConn.TYPE_(MOBILE/WIFI/...)
     */
    public static boolean
    isNetConnected(int netType) {
        int connectedNetType = sAdapter.getConnectedNetworks();
        return 0 != (connectedNetType & netType);
    }

    @NotNull
    public URLConnection
    getUrlConnection() {
        return mUrlConn;
    }

    public int
    getNetType() {
        return mNetType;
    }

    public boolean
    isNetConnected() {
        return isNetConnected(getNetType());
    }

    public void
    connect(Adapter adapter) throws IOException, InterruptedException {
        int retry = RETRY_COUNT;
        P.bug(!mConnected.get());
        URLConnection net = getUrlConnection();
        net.setConnectTimeout(CONN_TIMEOUT);
        while (0 < retry--) {
            try {
                if (!isNetConnected())
                    throw new ConnectException("Network is unavailable");
                net.connect();
                mConnected.set(true);
                break; // done
            } catch (IOException e) {
                // SocketTimeoutException
                // IOException
                if ((null != adapter && adapter.isCancel())
                    || 0 >= retry)
                    throw e;
                try {
                    Thread.sleep(INTERVAL_BETWEEN_RETRY);
                } catch (InterruptedException ie) {
                    throw new InterruptedException("Waiting for retry is interrupted");
                }
            }
        }
        P.bug(retry >= 0);
    }

    public void
    connect() throws IOException, InterruptedException {
        connect(null);
    }

    public void
    disconnect() {
        //P.bug(mConnected.get());
        mConnected.set(false);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
}
