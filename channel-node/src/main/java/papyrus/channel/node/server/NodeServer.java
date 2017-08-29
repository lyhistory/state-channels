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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.Credentials;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.server.admin.ChannelAdminImpl;
import papyrus.channel.node.server.channel.ChannelPeerImpl;
import papyrus.channel.node.server.channel.incoming.IncomingChannelClientImpl;
import papyrus.channel.node.server.channel.outgoing.OutgoingChannelClientImpl;
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.peer.EndpointRegistry;

@Service
@EnableConfigurationProperties(ChannelServerProperties.class)
public class NodeServer {
    private static final Logger log = LoggerFactory.getLogger(NodeServer.class);
    private final List<BindableService> bindableServices;

    private Server grpcServer;

    private ChannelServerProperties properties;
    private final EthereumConfig ethereumConfig;

    public NodeServer(List<BindableService> bindableServices, ChannelServerProperties properties, EthereumConfig ethereumConfig) {
        this.bindableServices = bindableServices;
        this.properties = properties;
        this.ethereumConfig = ethereumConfig;
    }

    @EventListener(ContextStartedEvent.class)
    public void start() {
        int port = properties.getPort();
        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        bindableServices.forEach(builder::addService);
        grpcServer = builder.build();

        try {
            grpcServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Server started, GRPC API listening on " + grpcServer.getPort());

        String endpointUrl = properties.getEndpointUrl();
        if (endpointUrl == null) {
            log.warn("No endpoint url provided");
        } else {
            for (Address address : ethereumConfig.getAddresses()) {
                ContractsManager contractManager = ethereumConfig.getContractManager(address);
                EndpointRegistry registry = new EndpointRegistry(contractManager.endpointRegistry());
                registry.registerEndpoint(address, endpointUrl);
            }
        }
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
