package papyrus.channel.node.server;

import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import io.grpc.stub.StreamObserver;
import papyrus.channel.node.ChannelClientGrpc;
import papyrus.channel.node.ChannelStatus;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.ChannelStatusResponse;
import papyrus.channel.node.server.outgoing.OutgoingChannelPoolManager;
import papyrus.channel.node.server.outgoing.OutgoingChannelState;

@Component
public class ChannelClientImpl extends ChannelClientGrpc.ChannelClientImplBase {
    
    private OutgoingChannelPoolManager manager;

    public ChannelClientImpl(OutgoingChannelPoolManager manager) {
        this.manager = manager;
    }

    @Override
    public void outgoingChannelState(ChannelStatusRequest request, StreamObserver<ChannelStatusResponse> responseObserver) {
        ChannelStatusResponse.Builder builder = ChannelStatusResponse.newBuilder();
        for (OutgoingChannelState state : manager.getChannelsState(new Address(request.getParticipantAddress()))) {
            builder.addChannels(ChannelStatus.newBuilder()
                .setActive(true)
                .setChannelAddress(state.getChannelAddress().toString())
                .build()
            );
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
