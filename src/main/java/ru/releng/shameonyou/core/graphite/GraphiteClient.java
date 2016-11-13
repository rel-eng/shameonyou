/*
 * Copyright (c) 2016 rel-eng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.releng.shameonyou.core.graphite;

import com.github.racc.tscg.TypesafeConfig;
import com.google.common.base.Charsets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.releng.shameonyou.core.LifeCycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class GraphiteClient implements LifeCycle {

    private static final Logger LOG = LogManager.getLogger(GraphiteClient.class);

    private final String graphiteHost;
    private final int graphitePort;
    private final Duration graphiteReconnectDelay;
    private final EventLoopGroup workerGroup;
    private final GraphiteClientHandler handler;
    private final StringDecoder decoder;
    private final StringEncoder encoder;
    private final LinkedBlockingQueue<QueuedWrite> writeQueue;
    private final AtomicReference<State> state = new AtomicReference<>(LifeCycle.State.INITIALIZED);

    private volatile Channel channel;

    @Inject
    public GraphiteClient(@TypesafeConfig("graphite.host") String graphiteHost,
                          @TypesafeConfig("graphite.port") int graphitePort,
                          @TypesafeConfig("graphite.reconnect.delay") Duration graphiteReconnectDelay)
    {
        this.graphiteHost = graphiteHost;
        this.graphitePort = graphitePort;
        this.graphiteReconnectDelay = graphiteReconnectDelay;
        this.workerGroup = new NioEventLoopGroup();
        this.handler = new GraphiteClientHandler();
        this.decoder = new StringDecoder(Charsets.UTF_8);
        this.encoder = new StringEncoder(Charsets.UTF_8);
        this.writeQueue = new LinkedBlockingQueue<>(600);
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INITIALIZED, State.STARTING)) {
            return;
        }
        LOG.info("Starting graphite client for {}:{}...", graphiteHost, graphitePort);
        try {
            connect(configureBootstrap(new Bootstrap(), workerGroup));
            state.set(State.STARTED);
            LOG.info("Graphite client for {}:{} started", graphiteHost, graphitePort);
        } finally {
            state.compareAndSet(State.STARTING, State.STOPPED);
        }
    }

    @Override
    public void stop() {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }
        try {
            LOG.info("Stopping graphite client for {}:{}...", graphiteHost, graphitePort);
            try {
                if (channel != null && channel.isActive()) {
                    drainQueue();
                    channel.writeAndFlush("").awaitUninterruptibly(250, TimeUnit.MILLISECONDS);
                    channel.closeFuture().awaitUninterruptibly(250, TimeUnit.MILLISECONDS);
                }
            } finally {
                workerGroup.shutdownGracefully(750, 1000, TimeUnit.MILLISECONDS);
            }
            LOG.info("Graphite client for {}:{} stopped", graphiteHost, graphitePort);
        } finally {
            state.set(State.STOPPED);
        }
    }

    public void write(String metrics) {
        if (state.get() != State.STARTED) {
            return;
        }
        QueuedWrite queuedWrite = new QueuedWrite(metrics);
        if (!writeQueue.offer(queuedWrite)) {
            LOG.warn("Write queue is full for graphite client {}:{}", graphiteHost, graphitePort);
        } else {
            if (state.get() != State.STARTED) {
                writeQueue.remove(queuedWrite);
            }
        }
        scheduleDrainQueue();
    }

    private void scheduleDrainQueue() {
        if (channel != null) {
            channel.eventLoop().execute(this::drainQueue);
        }
    }

    private void drainQueue() {
        if (channel == null) {
            return;
        }
        while (channel.isActive() && channel.isWritable()) {
            QueuedWrite next = writeQueue.poll();
            if (next == null) {
                break;
            }
            channel.write(next.getWrite());
        }
        channel.flush();
    }

    @Override
    public String toString() {
        return "GraphiteClient{" +
                "graphiteHost='" + graphiteHost + '\'' +
                ", graphitePort=" + graphitePort +
                ", graphiteReconnectDelay=" + graphiteReconnectDelay +
                ", state=" + state +
                '}';
    }

    private Bootstrap configureBootstrap(Bootstrap bootstrap, EventLoopGroup eventLoopGroup) {
        return bootstrap
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .remoteAddress(graphiteHost, graphitePort)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 1024 * 1024)
                .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 128 * 1024)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("decoder", decoder);
                        ch.pipeline().addLast("encoder", encoder);
                        ch.pipeline().addLast("writeTimeoutHandler", new WriteTimeoutHandler(1));
                        ch.pipeline().addLast("handler", handler);
                    }
                });
    }

    private void connect(Bootstrap bootstrap) {
        channel = bootstrap.connect().addListener((ChannelFutureListener) future -> {
            if (future.cause() != null) {
                LOG.error("Failed to connect to graphite {}:{}", graphiteHost, graphitePort, future.cause());
            }
        }).channel();
    }

    @ChannelHandler.Sharable
    private class GraphiteClientHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            LOG.info("Connected to graphite at {}", ctx.channel().remoteAddress());
            drainQueue();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            LOG.info("Disconnected from graphite at {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            if (state.get() != State.STARTED) {
                return;
            }
            LOG.info("Waiting {} ms before reconnecting to graphite {}:{}", graphiteReconnectDelay.toMillis(),
                    graphiteHost, graphitePort);
            EventLoop eventLoop = ctx.channel().eventLoop();
            eventLoop.schedule(() -> {
                LOG.info("Reconnecting to graphite {}:{}", graphiteHost, graphitePort);
                connect(configureBootstrap(new Bootstrap(), eventLoop));
            }, graphiteReconnectDelay.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOG.error("Error with graphite {}:{}", graphiteHost, graphitePort, cause);
            ctx.close();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            drainQueue();
        }

    }

    private static class QueuedWrite {

        private final String write;

        private QueuedWrite(String write) {
            this.write = write;
        }

        public String getWrite() {
            return write;
        }

        @Override
        public String toString() {
            return "QueuedWrite{" +
                    "write='" + write + '\'' +
                    '}';
        }

    }

}
