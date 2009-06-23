/**
 *  Copyright 2008 ThimbleWare Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.thimbleware.jmemcached;

import com.thimbleware.jmemcached.protocol.text.MemcachedPipelineFactory;
import com.thimbleware.jmemcached.protocol.binary.MemcachedBinaryPipelineFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;

/**
 * The actual daemon - responsible for the binding and configuration of the network configuration.
 */
public class MemCacheDaemon {

    final Logger logger = LoggerFactory.getLogger(MemCacheDaemon.class);

    public static String memcachedVersion = "0.9";

    private int receiveBufferSize = 32768 * 1024;
    private int sendBufferSize = 32768;

    private boolean binary = false;
    private boolean verbose;
    private int idleTime;
    private InetSocketAddress addr;
    private Cache cache;

    private boolean running = false;
    private NioServerSocketChannelFactory channelFactory;
    private DefaultChannelGroup allChannels;


    public MemCacheDaemon() {
    }

    public MemCacheDaemon(Cache cache) {
        this.cache = cache;
    }

    /**
     * Bind the network connection and start the network processing threads.
     *
     * @throws IOException
     */
    public void start() throws IOException {
        channelFactory =
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());

        allChannels = new DefaultChannelGroup("jmemcachedChannelGroup");

        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
      
        ChannelPipelineFactory pipelineFactory;
        if (binary)
            pipelineFactory = new MemcachedBinaryPipelineFactory(cache, memcachedVersion, verbose, idleTime, allChannels);
        else
            pipelineFactory = new MemcachedPipelineFactory(cache, memcachedVersion, verbose, idleTime, receiveBufferSize, allChannels);

        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setPipelineFactory(pipelineFactory);

        Channel serverChannel = bootstrap.bind(addr);
        allChannels.add(serverChannel);

        logger.info("Listening on " + String.valueOf(addr.getHostName()) + ":" + addr.getPort());
        running = true;
    }

    public void stop() {
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();
        if (!future.isCompleteSuccess()) {
            System.err.println("shit");
        }
        channelFactory.releaseExternalResources();
        running = false;
    }

    public static void setMemcachedVersion(String memcachedVersion) {
        MemCacheDaemon.memcachedVersion = memcachedVersion;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;
    }

    public void setAddr(InetSocketAddress addr) {
        this.addr = addr;
    }


    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }
}
