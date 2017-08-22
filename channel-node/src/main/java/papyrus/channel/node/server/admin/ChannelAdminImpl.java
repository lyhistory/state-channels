package papyrus.channel.node.server.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import papyrus.channel.node.AddParticipantRequest;
import papyrus.channel.node.AddParticipantResponse;
import papyrus.channel.node.ChannelAdminGrpc;
import papyrus.channel.node.entity.ChannelBlockchainProperties;
import papyrus.channel.node.server.outgoing.OutgoingChannelPoolManager;
import papyrus.channel.node.server.outgoing.OutgoingChannelProperties;

//TODO need to authenticate client
@Component
public class ChannelAdminImpl extends ChannelAdminGrpc.ChannelAdminImplBase {
    private static final Logger log = LoggerFactory.getLogger(ChannelAdminImpl.class);
    public static final int MAX_CHANNELS_PER_ADDRESS = 100;
    private OutgoingChannelPoolManager outgoingChannelPoolManager;

    public ChannelAdminImpl(OutgoingChannelPoolManager outgoingChannelPoolManager) {
        this.outgoingChannelPoolManager = outgoingChannelPoolManager;
    }

    @Override
    public void addParticipant(AddParticipantRequest request, StreamObserver<AddParticipantResponse> responseObserver) {
        log.info("Add participant {} active: {}", request.getParticipantAddress(), request.getActiveChannels());
        if (request.getActiveChannels() <= 0 || request.getActiveChannels() > MAX_CHANNELS_PER_ADDRESS) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(String.format("Illegal active channels: %d", request.getActiveChannels())).asException());
            return;
        }
        outgoingChannelPoolManager.addParticipant(
            new Address(request.getParticipantAddress()),
            new OutgoingChannelProperties(request.getActiveChannels(), new ChannelBlockchainProperties(10))
        );
        responseObserver.onNext(AddParticipantResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
