/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
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
 */

package papyrus.channel.node.server;

import java.io.IOException;
import java.util.logging.Logger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.node.server.admin.ChannelAdminImpl;

@Service
@EnableConfigurationProperties(ChannelServerProperties.class)
public class NodeServer {
    private static final Logger logger = Logger.getLogger(NodeServer.class.getName());

    private Server grpcServer;
    
    private ChannelClientImpl channelClient;
    private ChannelPeerImpl channelPeer;
    private ChannelAdminImpl channelAdmin;

    private ChannelServerProperties properties;

    public NodeServer(ChannelClientImpl channelClient, ChannelPeerImpl channelPeer, ChannelAdminImpl channelAdmin, ChannelServerProperties properties) {
        this.channelClient = channelClient;
        this.channelPeer = channelPeer;
        this.channelAdmin = channelAdmin;
        this.properties = properties;
    }

    @EventListener(ContextStartedEvent.class)
    public void start() {
        int port = properties.getPort();
        grpcServer = ServerBuilder.forPort(port)
            .addService(channelClient)
            .addService(channelPeer)
            .addService(channelAdmin)
            .build();

        try {
            grpcServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Server started, listening on " + grpcServer.getPort());
    }

    @EventListener(ContextStoppedEvent.class)
    public void stop() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }
    
    public void awaitTermination() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }
}
