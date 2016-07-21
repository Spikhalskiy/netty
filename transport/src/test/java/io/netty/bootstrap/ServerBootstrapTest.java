/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.bootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerBootstrapTest {
    static final String PORT = System.getProperty("port", "test_port");

    @Test(timeout = 5000)
    public void testHandlerRegister() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        LocalEventLoopGroup group = new LocalEventLoopGroup(1);
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
              .group(group)
              .childHandler(new ChannelInboundHandlerAdapter())
              .handler(new ChannelHandlerAdapter() {
                  @Override
                  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                      try {
                          assertTrue(ctx.executor().inEventLoop());
                      } catch (Throwable cause) {
                          error.set(cause);
                      } finally {
                          latch.countDown();
                      }
                  }
              });
            sb.register().syncUninterruptibly();
            latch.await();
            assertNull(error.get());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testParentHandler() throws Exception {
        final LocalAddress addr = new LocalAddress(PORT);

        final CountDownLatch readLatch = new CountDownLatch(1);
        final CountDownLatch initLatch = new CountDownLatch(1);

        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
              .group(group)
              .childHandler(new ChannelInboundHandlerAdapter())
              .handler(new ChannelInboundHandlerAdapter() {
                  @Override
                  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                      initLatch.countDown();
                      super.handlerAdded(ctx);
                  }

                  @Override
                  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                      readLatch.countDown();
                      super.channelRead(ctx, msg);
                  }
              });

            Bootstrap cb = new Bootstrap();
            cb.group(group)
              .channel(LocalChannel.class)
              .handler(new ChannelInboundHandlerAdapter());

            // Start the server.
            sb.bind(addr).sync();

            // Start the client.
            Channel ch = cb.connect(addr).sync().channel();

            ChannelFuture writeFuture = ch.writeAndFlush(newOneMessage());
            writeFuture.await();

            assertTrue(initLatch.await(5, TimeUnit.SECONDS));
            assertTrue(readLatch.await(5, TimeUnit.SECONDS));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testParentHandlerViaChannelInitializer() throws Exception {
        final LocalAddress addr = new LocalAddress(PORT);

        final CountDownLatch initLatch = new CountDownLatch(1);
        final CountDownLatch readLatch = new CountDownLatch(1);
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
            .group(group)
            .childHandler(new ChannelInboundHandlerAdapter())
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                            initLatch.countDown();
                            super.handlerAdded(ctx);
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            readLatch.countDown();
                            super.channelRead(ctx, msg);
                        }
                    });
                }
            });

            Bootstrap cb = new Bootstrap();
            cb.group(group)
              .channel(LocalChannel.class)
              .handler(new ChannelInboundHandlerAdapter());

            // Start the server.
            sb.bind(addr).sync();

            // Start the client.
            Channel ch = cb.connect(addr).sync().channel();

            ChannelFuture writeFuture = ch.writeAndFlush(newOneMessage());
            writeFuture.await();

            assertTrue(initLatch.await(5, TimeUnit.SECONDS));
            assertTrue(readLatch.await(5, TimeUnit.SECONDS));
        } finally {
            group.shutdownGracefully();
        }
    }

    private static ByteBuf newOneMessage() {
        return Unpooled.wrappedBuffer(new byte[]{ 1 });
    }
}
