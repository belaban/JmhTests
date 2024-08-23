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
import org.jgroups.util.AverageMinMax;
import org.jgroups.util.Util;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@link AverageMinMax} against Histogram
 */
@State(Scope.Benchmark)
@Fork(value=1, warmups=1)
@Warmup(iterations=1,time=5)
// @Timeout(time=5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations=1,time=10)
@Threads(100)
public class MyBenchmark {

    protected final AverageMinMax avg=new AverageMinMax();
    protected final Histogram cch=new ConcurrentHistogram(1, 60_000, 3);
    protected final Histogram ath=new AtomicHistogram(1, 60_000, 3);
    protected final Histogram h=new Histogram(1, 60_000, 3);
    protected final Histogram sh=new SynchronizedHistogram(1, 60_000, 3);
    protected static final int CAPACITY=2048;

    @Benchmark
    public void testAverageMinMax() {
        long l=Util.random(1000);
        synchronized(avg) {
            avg.add(l);
        }
    }

    @Benchmark
    public void testAverageMinMaxMultipleNumbers() {
        long[] numbers={Util.random(1000), Util.random(1000),Util.random(1000),
          Util.random(1000),Util.random(1000),Util.random(1000)};
        synchronized(avg) {
            for(long n: numbers)
                if(n > 0)
                    avg.add(n);
        }
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

    /*@TearDown(Level.Iteration)
    public void showStats() {
        System.out.printf("avg: %s hist: %s, sh: %s, cch: %s, atomic: %s\n",
                          avg, h.getMean(), sh.getMean(), cch.getMean(), ath.getMean());
    }*/
}
