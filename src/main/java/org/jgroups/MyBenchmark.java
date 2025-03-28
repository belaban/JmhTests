/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jgroups;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SynchronizedHistogram;
import org.jgroups.protocols.TpHeader;
import org.jgroups.util.AverageMinMax;
import org.jgroups.util.FastArray;
import org.jgroups.util.Util;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmarks {@link AverageMinMax} against Histogram
 */
@State(Scope.Benchmark)
@Fork(value=1, warmups=1)
@Warmup(iterations=1,time=5)
// @Timeout(time=5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations=5,time=10)
@Threads(100)
public class MyBenchmark {

    protected final AverageMinMax avg=new AverageMinMax(1000);
    protected final Histogram cch=new ConcurrentHistogram(1, 60_000, 3);
    protected final Histogram ath=new AtomicHistogram(1, 60_000, 3);
    protected final Histogram h=new Histogram(1, 60_000, 2);
    protected final Histogram sh=new SynchronizedHistogram(1, 60_000, 3);
    protected static final int CAPACITY=2048;
    protected final Lock lock=new ReentrantLock();
    protected final LongAdder la=new LongAdder();
    protected final Map<Integer,Integer> map=new ConcurrentHashMap<>();
    protected final Supplier<Header> supplier=TpHeader::new;
    protected final Constructor<TpHeader> ctor=getCtor();
    protected final List<Integer> array_list=new ArrayList<>(IntStream.range(1, 1000).boxed().collect(Collectors.toList()));
    protected final List<Integer> fast_array=new FastArray<>(create(1000), 1000);
    protected final SplittableRandom random=new SplittableRandom();
    protected final AtomicInteger acc=new AtomicInteger();
    protected static final int MAX=100;
    protected static final Address A=Util.createRandomAddress("A"), B=A, C=Util.createRandomAddress("C");
    protected int num=0;
    protected final IntBinaryOperator OP=(l, __) -> {
        if(l+1 >= MAX)
            return 0;
        else
            return l+1;
    };

    private Constructor<TpHeader> getCtor() {
        try {
            return TpHeader.class.getDeclaredConstructor();
        }
        catch(NoSuchMethodException e) {
            return null;
        }
    }

    private static Integer[] create(int num) {
        List<Integer> list=new ArrayList<>();
        for(int i=1; i <= num; i++)
            list.add(i);
        return list.toArray(new Integer[0]);
    }

    @Benchmark
    public void testAverageMinMax() {
        long l=Util.random(1000);
        avg.add(l);
    }

    @Benchmark
    public void testConcurrentHistogram() {
        long l=Util.random(1000);
        cch.recordValue(l);
    }

    @Benchmark
    public void testAtomicHistogram() {
        long l=Util.random(1000);
        ath.recordValue(l);
    }

    @Benchmark
    public void testRegularHistogram() {
        long l=Util.random(1000);
        synchronized(h) {
            h.recordValue(l);
        }
    }

    @Benchmark
    public void testSynchronizedHistogram() {
        long l=Util.random(1000);
        sh.recordValue(l);
    }

    @Benchmark
    public static int testModFunction() {
        long seqno=Util.random(10000);
        return (int)((seqno-1) % CAPACITY);
    }

    @Benchmark
    public static int testModN2Function() {
        long seqno=Util.random(10000);
        int offset=0;
        return (int)((seqno - offset - 1) & CAPACITY - 1);
    }

    @Benchmark
    public void testLock() {
        lock.lock();
        try {
            ;
        }
        finally {
            lock.unlock();
        }
    }

    @Benchmark
    public void testLongAdder() {
        long num=Util.random(1000);
        la.add(num);
    }

    // This is MUCH faster then using the constructor (via reflection) below! Ca 10x faster! 6ns vs 60ns
    @Benchmark
    public void testSupplier() {
        Header hdr=supplier.get();
        if(hdr == null)
            System.out.printf("header is null: %s\n", hdr);
    }

    @Benchmark
    public void testCtor() {
        try {
            Header hdr=ctor.newInstance();
            if(hdr == null)
                System.out.printf("header is null: %s\n", hdr);
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public static int testArrayListWithResize() {
        List<Integer> list=new ArrayList<>(128);
        return testArray(list);
    }

    @Benchmark
    public static int testArrayListNoResize() {
        List<Integer> list=new ArrayList<>(1024);
        return testArray(list);
    }

    @Benchmark
    public static int testFastArrayWithResize() {
        List<Integer> list=new FastArray<>(128); // 512).increment(512);
        return testArray(list);
    }

    @Benchmark
    public static int testFastArrayNoResize() {
        List<Integer> list=new FastArray<>(1024);
        return testArray(list);
    }

    @Benchmark
    public int testExistingArrayListSize() {
        return array_list.size();
    }

    @Benchmark
    public int testExistingFastArraySize() {
        return fast_array.size();
    }

    @Benchmark
    public int testExistingArrayListIteration() {
        int count=0;
        for(Integer __: array_list)
            count++;
        return count;
    }

    @Benchmark
    public int testExistingFastArrayIteration() {
        int count=0;
        for(Integer __: fast_array)
            count++;
        return count;
    }

    @Benchmark
    public static int testThreadLocalRandom() {
        return ThreadLocalRandom.current().nextInt(100) + 1;
    }

    @Benchmark
    public int testSplittableRandom() {
        return random.nextInt(1, 100);
    }

    @Benchmark
    public int testAccumulateAndGet() {
        return acc.accumulateAndGet(1, OP);
    }

    @Benchmark
    public int testAccumulateAndGetViaLock() {
        lock.lock();
        try {
            if(num >= MAX)
                return num=0;
            return num++;
        }
        finally {
            lock.unlock();
        }
    }

    @Benchmark
    public boolean testAddressComparisonSame() {
        return Objects.equals(A, B);
    }

    @Benchmark
    public boolean testAddressComparisonEquals() {
        return Objects.equals(A,C);
    }

    protected static int testArray(List<Integer> list) {
        for(int i=1; i <= 1000; i++)
            list.add(i);
        int size=list.size();
        list.clear();
        return size;
    }
}
