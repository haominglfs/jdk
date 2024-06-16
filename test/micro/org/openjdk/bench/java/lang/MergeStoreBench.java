/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.internal.misc.Unsafe;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"})
public class MergeStoreBench {
    private static final int HI_BYTE_SHIFT;
    private static final int LO_BYTE_SHIFT;
    static {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            HI_BYTE_SHIFT = 8;
            LO_BYTE_SHIFT = 0;
        } else {
            HI_BYTE_SHIFT = 0;
            LO_BYTE_SHIFT = 8;
        }
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final static VarHandle INT_L = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    final static VarHandle INT_B = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    final static VarHandle LONG_L = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    final static VarHandle LONG_B = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    final static int NUMBERS = 8192;
    final byte[] bytes4 = new byte[NUMBERS * 4];
    final byte[] bytes8 = new byte[NUMBERS * 8];
    final int[] ints = new int[NUMBERS];
    final long[] longs = new long[NUMBERS];

    @Setup
    public void setup() {
        Random r = new Random();
        for (int i = 0; i < ints.length; i++) {
            ints[i] = r.nextInt();
            INT_L.set(bytes4, i * 4, i);
        }

        for (int i = 0; i < longs.length; i++) {
            longs[i] = r.nextLong();
            LONG_L.set(bytes8, i * 8, i);
        }
    }

    /*
     * The names of these cases have the following `B/L/V/U` suffixes, which are:
     * ```
     * B BigEndian
     * L LittleEndian
     * V VarHandle
     * U Unsafe
     * R ReverseBytes
     * ```
     */

    @Benchmark
    public void getIntB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntB(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntBU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntBU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntBV(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (int) INT_B.get(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntL(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntL(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntLU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntLU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntLV(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (int) INT_L.get(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRB(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRBU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRBU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRL(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRL(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRLU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRLU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += Integer.reverseBytes(
                    UNSAFE.getInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4));
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += UNSAFE.getInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntB(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntBU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntBU(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntBV(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            INT_B.set(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntL(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntL(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntLU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntLU(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntLV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            INT_L.set(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntRB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntRB(bytes4, i * 4, ints[i]);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntRBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntRBU(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntRL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntRL(bytes4, i * 4, ints[i]);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntRLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntRLU(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntRU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            v = Integer.reverseBytes(v);
            UNSAFE.putInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            UNSAFE.putInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongB(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongBU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongBV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (long) LONG_B.get(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongL(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongLU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongLV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (long) LONG_L.get(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRB(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRBU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRL(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRLU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += Long.reverseBytes(
                    UNSAFE.getLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8));
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += UNSAFE.getLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongB(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongBU(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongBV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            LONG_B.set(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongL(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongLU(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongLV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            LONG_L.set(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongRB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongRB(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongRBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongRBU(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongRL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongRL(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongRLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            setLongRLU(bytes8, i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongRU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            v = Long.reverseBytes(v);
            UNSAFE.putLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setLongU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            UNSAFE.putLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void putChars4(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            putChars4(bytes8, i * 4, 'n', 'u', 'l', 'l');
            sum += longs[i];
        }
        BH.consume(sum);
    }

    @Benchmark
    public void putChars4UB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            putChars4UB(bytes8, i * 4, 'n', 'u', 'l', 'l');
            sum += longs[i];
        }
        BH.consume(sum);
    }

    @Benchmark
    public void putChars4UC(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            putChars4UC(bytes8, i * 4, 'n', 'u', 'l', 'l');
            sum += longs[i];
        }
        BH.consume(sum);
    }

    static int getIntB(byte[] array, int offset) {
        return ((array[offset    ] & 0xff)  << 24)
                | ((array[offset + 1] & 0xff) << 16)
                | ((array[offset + 2] & 0xff) << 8)
                |  (array[offset + 3] & 0xff);
    }

    static int getIntBU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return ((UNSAFE.getByte(array,           address) & 0xff) << 24)
                | ((UNSAFE.getByte(array, address + 1) & 0xff) << 16)
                | ((UNSAFE.getByte(array, address + 2) & 0xff) <<  8)
                | ((UNSAFE.getByte(array, address + 3) & 0xff)      );
    }

    static int getIntL(byte[] array, int offset) {
        return ((array[offset    ] & 0xff)      )
                | ((array[offset + 1] & 0xff) <<  8)
                | ((array[offset + 2] & 0xff) << 16)
                | ((array[offset + 3] & 0xff) << 24);
    }

    static int getIntRB(byte[] array, int offset) {
        return Integer.reverseBytes(getIntB(array, offset));
    }

    static int getIntRBU(byte[] array, int offset) {
        return Integer.reverseBytes(getIntBU(array, offset));
    }

    static int getIntRL(byte[] array, int offset) {
        return Integer.reverseBytes(getIntL(array, offset));
    }

    static int getIntRLU(byte[] array, int offset) {
        return Integer.reverseBytes(getIntLU(array, offset));
    }

    static void setIntB(byte[] array, int offset, int value) {
        array[offset    ] = (byte) (value >> 24);
        array[offset + 1] = (byte) (value >> 16);
        array[offset + 2] = (byte) (value >>  8);
        array[offset + 3] = (byte) (value      );
    }

    static void setIntBU(byte[] array, int offset, int value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address          , (byte) (value >> 24));
        UNSAFE.putByte(array, address + 1, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 3, (byte) (value      ));
    }

    public static void setIntL(byte[] array, int offset, int value) {
        array[offset    ] = (byte)  value;
        array[offset + 1] = (byte) (value >> 8);
        array[offset + 2] = (byte) (value >> 16);
        array[offset + 3] = (byte) (value >> 24);
    }

    public static void setIntLU(byte[] array, int offset, int value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address          , (byte)  value        );
        UNSAFE.putByte(array, address + 1, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 24));
    }

    public static void setIntRL(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntL(array, offset, value);
    }

    public static void setIntRLU(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntLU(array, offset, value);
    }

    public static void setIntRB(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntB(array, offset, value);
    }

    public static void setIntRBU(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntBU(array, offset, value);
    }

    static long getLongB(byte[] array, int offset) {
        return (((long) array[offset] & 0xff) << 56)
                | (((long) array[offset + 1] & 0xff) << 48)
                | (((long) array[offset + 2] & 0xff) << 40)
                | (((long) array[offset + 3] & 0xff) << 32)
                | (((long) array[offset + 4] & 0xff) << 24)
                | (((long) array[offset + 5] & 0xff) << 16)
                | (((long) array[offset + 6] & 0xff) << 8)
                | ((long) array[offset + 7] & 0xff);
    }

    static long getLongBU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return (((long)(UNSAFE.getByte(array,           address) & 0xff)) << 56)
                | (((long)(UNSAFE.getByte(array, address + 1) & 0xff)) << 48)
                | (((long)(UNSAFE.getByte(array, address + 2) & 0xff)) << 40)
                | (((long)(UNSAFE.getByte(array, address + 3) & 0xff)) << 32)
                | (((long)(UNSAFE.getByte(array, address + 4) & 0xff)) << 24)
                | (((long)(UNSAFE.getByte(array, address + 5) & 0xff)) << 16)
                | (((long)(UNSAFE.getByte(array, address + 6) & 0xff)) <<  8)
                | (((long)(UNSAFE.getByte(array, address + 7) & 0xff))      );
    }

    public static long getLongL(byte[] array, int offset) {
        return (((long) array[offset       ] & 0xff)      )
                | (((long) array[offset + 1] & 0xff) <<  8)
                | (((long) array[offset + 2] & 0xff) << 16)
                | (((long) array[offset + 3] & 0xff) << 24)
                | (((long) array[offset + 4] & 0xff) << 32)
                | (((long) array[offset + 5] & 0xff) << 40)
                | (((long) array[offset + 6] & 0xff) << 48)
                | (((long) array[offset + 7] & 0xff) << 56);
    }

    static long getLongLU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return (((long)(UNSAFE.getByte(array,           address) & 0xff))  )
                | (((long)(UNSAFE.getByte(array, address + 1) & 0xff)) <<  8)
                | (((long)(UNSAFE.getByte(array, address + 2) & 0xff)) << 16)
                | (((long)(UNSAFE.getByte(array, address + 3) & 0xff)) << 24)
                | (((long)(UNSAFE.getByte(array, address + 4) & 0xff)) << 32)
                | (((long)(UNSAFE.getByte(array, address + 5) & 0xff)) << 40)
                | (((long)(UNSAFE.getByte(array, address + 6) & 0xff)) << 48)
                | (((long)(UNSAFE.getByte(array, address + 7) & 0xff)) << 56);
    }

    static long getLongRB(byte[] array, int offset) {
        return getLongB(array, offset);
    }

    static long getLongRBU(byte[] array, int offset) {
        return getLongBU(array, offset);
    }

    static long getLongRL(byte[] array, int offset) {
        return getLongL(array, offset);
    }

    static long getLongRLU(byte[] array, int offset) {
        return getLongLU(array, offset);
    }

    static void setLongB(byte[] array, int offset, long value) {
        array[offset]     = (byte) (value >> 56);
        array[offset + 1] = (byte) (value >> 48);
        array[offset + 2] = (byte) (value >> 40);
        array[offset + 3] = (byte) (value >> 32);
        array[offset + 4] = (byte) (value >> 24);
        array[offset + 5] = (byte) (value >> 16);
        array[offset + 6] = (byte) (value >>  8);
        array[offset + 7] = (byte) (value      );
    }

    public static void setLongL(byte[] array, int offset, long value) {
        array[offset]     = (byte)  value;
        array[offset + 1] = (byte) (value >> 8);
        array[offset + 2] = (byte) (value >> 16);
        array[offset + 3] = (byte) (value >> 24);
        array[offset + 4] = (byte) (value >> 32);
        array[offset + 5] = (byte) (value >> 40);
        array[offset + 6] = (byte) (value >> 48);
        array[offset + 7] = (byte) (value >> 56);
    }

    public static void setLongRL(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongL(array, offset, value);
    }

    public static void setLongRLU(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongLU(array, offset, value);
    }

    public static void setLongRB(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongB(array, offset, value);
    }

    public static void setLongRBU(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongBU(array, offset, value);
    }

    public static void setLongBU(byte[] array, int offset, long value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address          , (byte)  (value >> 56));
        UNSAFE.putByte(array, address + 1, (byte) (value >> 48));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 40));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 32));
        UNSAFE.putByte(array, address + 4, (byte) (value >> 24));
        UNSAFE.putByte(array, address + 5, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 6, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 7, (byte) (value      ));
    }

    public static void setLongLU(byte[] array, int offset, long value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address          , (byte)  value        );
        UNSAFE.putByte(array, address + 1, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 24));
        UNSAFE.putByte(array, address + 4, (byte) (value >> 32));
        UNSAFE.putByte(array, address + 5, (byte) (value >> 40));
        UNSAFE.putByte(array, address + 6, (byte) (value >> 48));
        UNSAFE.putByte(array, address + 7, (byte) (value >> 56));
    }

    public static int getIntLU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return ((UNSAFE.getByte(array,           address) & 0xff)      )
                | ((UNSAFE.getByte(array, address + 1) & 0xff) <<  8)
                | ((UNSAFE.getByte(array, address + 2) & 0xff) << 16)
                | ((UNSAFE.getByte(array, address + 3) & 0xff) << 24);
    }

    public static void putChars4(byte[] array, int offset, char c0, char c1, char c2, char c3) {
        putChar(array, offset, c0);
        putChar(array, offset + 1, c1);
        putChar(array, offset + 2, c2);
        putChar(array, offset + 3, c3);
    }

    public static void putChars4UC(byte[] array, int offset, char c0, char c1, char c2, char c3) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putChar(array, address, c0);
        UNSAFE.putChar(array, address + 2, c1);
        UNSAFE.putChar(array, address + 4, c2);
        UNSAFE.putChar(array, address + 6, c3);
    }

    public static void putChars4UB(byte[] array, int offset, char c0, char c1, char c2, char c3) {
        putCharUB(array, offset, c0);
        putCharUB(array, offset + 1, c1);
        putCharUB(array, offset + 2, c2);
        putCharUB(array, offset + 3, c3);
    }

    private static void putChar(byte[] val, int index, char c) {
        index <<= 1;
        val[index    ] = (byte)(c >> HI_BYTE_SHIFT);
        val[index + 1] = (byte)(c >> LO_BYTE_SHIFT);
    }

    public static void putCharUB(byte[] array, int offset, char c) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + (offset << 1);
        UNSAFE.putByte(array, address    , (byte) (c >>  8));
        UNSAFE.putByte(array, address + 1, (byte) (c      ));
    }
}
