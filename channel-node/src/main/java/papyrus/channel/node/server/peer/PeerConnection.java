package papyrus.channel.node.server.peer;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import com.google.common.base.Preconditions;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import papyrus.channel.node.ChannelAdminGrpc;
import papyrus.channel.node.IncomingChannelClientGrpc;
import papyrus.channel.node.OutgoingChannelClientGrpc;
import papyrus.channel.protocol.ChannelPeerGrpc;

public class PeerConnection implements Closeable {
    private final ManagedChannel channel;
    private final ChannelAdminGrpc.ChannelAdminBlockingStub clientAdmin;
    private final OutgoingChannelClientGrpc.OutgoingChannelClientBlockingStub outgoingChannelClient;
    private final IncomingChannelClientGrpc.IncomingChannelClientBlockingStub incomingChannelClient;
    private final ChannelPeerGrpc.ChannelPeerBlockingStub channelPeer;

    public PeerConnection(URI nodeUrl) {
        Preconditions.checkArgument("grpc".equals(nodeUrl.getScheme()));
        channel = ManagedChannelBuilder.forAddress(nodeUrl.getHost(), nodeUrl.getPort()).usePlaintext(true).build();
        clientAdmin = ChannelAdminGrpc.newBlockingStub(channel);
        outgoingChannelClient = OutgoingChannelClientGrpc.newBlockingStub(channel);
        incomingChannelClient = IncomingChannelClientGrpc.newBlockingStub(channel);
        channelPeer = ChannelPeerGrpc.newBlockingStub(channel);
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public ChannelAdminGrpc.ChannelAdminBlockingStub getClientAdmin() {
        return clientAdmin;
    }

    public OutgoingChannelClientGrpc.OutgoingChannelClientBlockingStub getOutgoingChannelClient() {
        return outgoingChannelClient;
    }

    public IncomingChannelClientGrpc.IncomingChannelClientBlockingStub getIncomingChannelClient() {
        return incomingChannelClient;
    }

    public ChannelPeerGrpc.ChannelPeerBlockingStub getChannelPeer() {
        return channelPeer;
    }

    @Override
    public void close() throws IOException {
        channel.shutdown();
    }
}
