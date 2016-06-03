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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;

import free.yhc.baselib.Logger;

public class NetConnHttp extends NetConn {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(NetConnHttp.class, Logger.LOGLV_DEFAULT);

    ///////////////////////////////////////////////////////////////////////////
    //
    // builder
    //
    ///////////////////////////////////////////////////////////////////////////
    public NetConnHttp(
            int netType,
            @NotNull HttpURLConnection httpConn)
            throws IOException {
        super(netType, httpConn);
    }

    public static class Builder extends NetConn.Builder {
        protected boolean mFollowingRedirect = true;
        protected String mUastring = null;

        @NotNull
        @Override
        protected HttpURLConnection
        createUrlConnection(@NotNull Proxy.Type proxyType)
                throws IOException {
            HttpURLConnection http = (HttpURLConnection)super.createUrlConnection(Proxy.Type.HTTP);
            http.setInstanceFollowRedirects(mFollowingRedirect);
            if (null != mUastring)
                http.setRequestProperty("User-Agent", mUastring);
            return http;
        }

        @Override
        protected boolean
        verify() {
            return super.verify();
        }

        private Builder(@NotNull URL url) {
            super(url);
        }
        // ========================================================================
        //
        // ========================================================================

        @NotNull
        @Override
        public Builder
        setNetType(int netType) {
            return (Builder)super.setNetType(netType);
        }

        @NotNull
        @Override
        public Builder
        setProxy(@NotNull String host, int port) {
            return (Builder)super.setProxy(host, port);
        }

        @NotNull
        public Builder
        setFollowingRedirect(boolean followingRedirect) {
            mFollowingRedirect = followingRedirect;
            return this;
        }

        @NotNull
        public Builder
        setUastring(String uastring) {
            mUastring = uastring;
            return this;
        }

        @Override
        @NotNull
        public NetConnHttp
        create() throws IOException {
            HttpURLConnection http = createUrlConnection(Proxy.Type.HTTP);
            return new NetConnHttp(mNetType, http);
        }

        // ========================================================================
        //
        // ========================================================================

        /**
         * "Following redirect" is true by default.
         */
        @NotNull
        public static Builder
        newBuilder(@NotNull URL url) throws MalformedURLException {
            String proto = url.getProtocol().toLowerCase();
            if ("http".equals(proto) || "https".equals(proto))
                return new Builder(url);
            else
                throw new MalformedURLException("Invalid protocol: " + url.getProtocol());
        }

        @NotNull
        public static Builder
        newBuilder(@NotNull String url) throws MalformedURLException {
            return newBuilder(new URL(url));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Package private
    //
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void
    disconnect() {
        ((HttpURLConnection)mUrlConn).disconnect();
        super.disconnect();
    }
}
