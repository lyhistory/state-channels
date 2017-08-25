package papyrus.channel.node.server.channel;

import java.security.SignatureException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import papyrus.channel.node.server.channel.incoming.IncomingChannelManager;
import papyrus.channel.protocol.ChannelOpenedRequest;
import papyrus.channel.protocol.ChannelOpenedResponse;
import papyrus.channel.protocol.ChannelPeerGrpc;
import papyrus.channel.protocol.ChannelUpdateRequest;
import papyrus.channel.protocol.ChannelUpdateResponse;

@Component
public class ChannelPeerImpl extends ChannelPeerGrpc.ChannelPeerImplBase {
    private static final Logger log = LoggerFactory.getLogger(ChannelPeerImpl.class);
    private IncomingChannelManager incomingChannelManager;

    public ChannelPeerImpl(IncomingChannelManager incomingChannelManager) {
        this.incomingChannelManager = incomingChannelManager;
    }

    @Override
    public void opened(ChannelOpenedRequest request, StreamObserver<ChannelOpenedResponse> responseObserver) {
        incomingChannelManager.register(new Address(request.getChannelId()));
    }

    @Override
    public void update(ChannelUpdateRequest request, StreamObserver<ChannelUpdateResponse> responseObserver) {
        try {
            incomingChannelManager.updateSenderState(new SignedChannelState(request.getState()));
        } catch (SignatureException e) {
            log.warn("Invalid signature", e);
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid signature").asException());
        }
    }
}
