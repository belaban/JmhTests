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

import org.jgroups.blocks.cs.NioServer;
import org.jgroups.blocks.cs.TcpServer;
import org.jgroups.util.Bits;
import org.jgroups.util.Util;
import org.openjdk.jmh.annotations.*;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Benchmarks which test sending of data via Socket and SocketChannel
 */
@State(Scope.Benchmark)
@Fork(value=1, warmups=1)
@Warmup(iterations=1,time=5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations=5,time=10)
@Threads(100)
public class SocketBenchmark {
    protected TcpServer     tcp_srv;
    protected Socket        sock;
    protected DataOutputStream out;

    protected NioServer     nio_srv;
    protected SocketChannel sock_ch;
    protected byte[]        byte_array;
    protected ByteBuffer    buf;
    protected final Lock    lock=new ReentrantLock();
    protected static final String BYTE_ARRAY="byte_array", HEAP_BB="heap_bb", DIRECT_BB="direct_bb";
    protected static final int SND_BUF=5_000_000, RECV_BUF=5_000_000;

    @Param({BYTE_ARRAY, HEAP_BB, DIRECT_BB})
    public  String       buffer_type;

    @Param({"100", "1000", "10000", "40000"})
    protected int        size;

    @Setup
    public void setup() throws Exception {
        switch(buffer_type) {
            case BYTE_ARRAY:
                tcp_srv=new TcpServer(Util.getLoopback(), 7500);
                tcp_srv.receiveBufferSize(RECV_BUF).sendBufferSize(SND_BUF);
                tcp_srv.start();
                byte_array=new byte[size+4]; // buf is preceeded by length
                Bits.writeInt(byte_array.length, byte_array, 0);
                sock=new Socket(Util.getLoopback(), 7500);
                sock.setSendBufferSize(SND_BUF);
                out=new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 8192));
                break;
            case HEAP_BB:
            case DIRECT_BB:
                nio_srv=new NioServer(Util.getLoopback(), 7500);
                nio_srv.receiveBufferSize(RECV_BUF).sendBufferSize(SND_BUF);
                nio_srv.start();
                buf=buffer_type.equals(HEAP_BB)? ByteBuffer.allocate(size+4)
                  : ByteBuffer.allocateDirect(size+4);
                buf.putInt(size);
                sock_ch=SocketChannel.open(new InetSocketAddress(Util.getLoopback(), 7500));
                break;
        }
    }

    @TearDown
    public void tearDown() {
        Util.close(tcp_srv, nio_srv, sock, sock_ch, out);
    }

    @Benchmark
    public void testSocketWrite() throws Exception {
        switch(buffer_type) {
            case BYTE_ARRAY:
                out.write(byte_array, 0, byte_array.length);
                // out.flush();
                break;
            case HEAP_BB:
            case DIRECT_BB:
                lock.lock();
                try {
                    buf.clear();
                    sock_ch.write(buf);
                    // sock_ch.socket().getOutputStream().flush();
                }
                finally {
                    lock.unlock();
                }
                break;
        }
    }

}
