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

package free.yhc.baselib.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;

import free.yhc.baselib.Logger;

/*
 * General utilities that doesn't have any dependencies on Android Framework.
 */
public class Util {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(Util.class, Logger.LOGLV_DEFAULT);

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    @NotNull
    public static String
    toString(Object o) {
        return null == o? "<null>": o.toString();
    }

    public static <K, V> void
    getEntries(@NotNull HashMap<K, V> map, @NotNull K[] ks, @NotNull V[] vs) {
        P.bug(ks.length == vs.length);
        K[] newKs = map.keySet().toArray(ks);
        // ks should be large enough to contain all keys in the map
        P.bug(newKs == ks);
        int i = 0;
        for (K k : ks)
            vs[i++] = map.get(k);
    }


    // ========================================================================
    // To handle generic array
    // ========================================================================
    @NotNull
    public static <T> T[]
    toArray(@NotNull List<T> list, @NotNull T[] a) {
        if (a.length < list.size())
            //noinspection unchecked
            a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), list.size());
        return list.toArray(a);
    }

    @NotNull
    public static <T> T[]
    toArray(@NotNull List<T> list, Class<T> k) {
        //noinspection unchecked
        return list.toArray((T[])java.lang.reflect.Array.newInstance(k, list.size()));
    }

    @NotNull
    public static <T> T[]
    newArray(@NotNull Class<T> k, int size) {
        //noinspection unchecked
        return (T[])java.lang.reflect.Array.newInstance(k, size);
    }

    @NotNull
    public static <T> T[] concatArry (T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        //noinspection unchecked
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }
    // ========================================================================
    // Bit operation
    // ========================================================================
    public static long
    bitClear(long flag, long mask) {
        return flag & ~mask;
    }

    public static long
    bitSet(long flag, long value, long mask) {
        flag = bitClear(flag, mask);
        return flag | (value & mask);
    }

    public static boolean
    bitCompare(long v0, long v1, long mask) {
        return (v0 & mask) == (v1 & mask);
    }

    public static boolean
    bitIsSet(long flag, long mask) {
        return bitCompare(flag, mask, mask);
    }

    public static boolean
    bitIsClear(long flag, long mask) {
        return bitCompare(0, flag, mask);
    }

    // For integer
    // ------------------------------------------------------------------------
    public static int
    bitClear(int flag, int mask) {
        return flag & ~mask;
    }

    public static int
    bitSet(int flag, int value, int mask) {
        flag = bitClear(flag, mask);
        return flag | (value & mask);
    }

    public static boolean
    bitCompare(int v0, int v1, int mask) {
        return (v0 & mask) == (v1 & mask);
    }

    public static boolean
    bitIsSet(int flag, int mask) {
        return bitCompare(flag, mask, mask);
    }

    public static boolean
    bitIsClear(int flag, int mask) {
        return bitCompare(0, flag, mask);
    }

    // ========================================================================
    // Array casting for primitive types
    // ========================================================================
    @NotNull
    public static long[]
    convertArrayLongTolong(@NotNull Long[] L) {
        long[] l = new long[L.length];
        for (int i = 0; i < L.length; i++)
            l[i] = L[i];
        return l;
    }

    @NotNull
    public static Long[]
    convertArraylongToLong(@NotNull long[] l) {
        Long[] L = new Long[l.length];
        for (int i = 0; i < l.length; i++)
            L[i] = l[i];
        return L;
    }

    @NotNull
    public static int[]
    convertArrayIntegerToint(@NotNull Integer[] I) {
        int[] i = new int[I.length];
        for (int j = 0; j < I.length; j++)
            i[j] = I[j];
        return i;
    }

    @NotNull
    public static Integer[]
    convertArrayintToInteger(@NotNull int[] i) {
        Integer[] I = new Integer[i.length];
        for (int j = 0; j < i.length; j++)
            I[j] = i[j];
        return I;
    }

    // ========================================================================
    // Date and time
    // ========================================================================
    public static long
    hourToMs(long hour) {
        return hour * 60 * 60 * 1000;
    }

    public static long
    secToMs(long sec) {
        return sec * 1000;
    }
}
