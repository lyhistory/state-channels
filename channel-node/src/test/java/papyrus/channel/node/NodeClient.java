package papyrus.channel.node;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.protocol.ChannelPeerGrpc;

@Lazy
@Service
@EnableConfigurationProperties(ChannelServerProperties.class)
public class NodeClient {
    private final ManagedChannel channel;
    private final ChannelAdminGrpc.ChannelAdminBlockingStub clientAdmin;
    private final ChannelClientGrpc.ChannelClientBlockingStub channelClient;
    private final ChannelPeerGrpc.ChannelPeerBlockingStub channelPeer;

    public NodeClient(ChannelServerProperties serverProperties) {
        channel = ManagedChannelBuilder.forAddress("localhost", serverProperties.getPort()).usePlaintext(true).build();
        clientAdmin = ChannelAdminGrpc.newBlockingStub(channel);
        channelClient = ChannelClientGrpc.newBlockingStub(channel);
        channelPeer = ChannelPeerGrpc.newBlockingStub(channel);
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    @Bean
    public ChannelAdminGrpc.ChannelAdminBlockingStub getClientAdmin() {
        return clientAdmin;
    }

    @Bean
    public ChannelClientGrpc.ChannelClientBlockingStub getChannelClient() {
        return channelClient;
    }

    @Bean
    public ChannelPeerGrpc.ChannelPeerBlockingStub getChannelPeer() {
        return channelPeer;
    }
}
