package papyrus.channel.node.server.channel.incoming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import io.grpc.stub.StreamObserver;
import papyrus.channel.node.ChannelStatusMessage;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.ChannelStatusResponse;
import papyrus.channel.node.IncomingChannelClientGrpc;
import papyrus.channel.node.RegisterTransfersRequest;
import papyrus.channel.node.RegisterTransfersResponse;
import papyrus.channel.node.TransferMessage;
import papyrus.channel.node.server.channel.SignedTransfer;

@Component
public class IncomingChannelClientImpl extends IncomingChannelClientGrpc.IncomingChannelClientImplBase {
    private static final Logger log = LoggerFactory.getLogger(IncomingChannelClientImpl.class);
    private IncomingChannelManagers manager;

    public IncomingChannelClientImpl(IncomingChannelManagers manager) {
        this.manager = manager;
    }

    @Override
    public void getChannels(ChannelStatusRequest request, StreamObserver<ChannelStatusResponse> responseObserver) {
        ChannelStatusResponse.Builder builder = ChannelStatusResponse.newBuilder();
        for (IncomingChannelState state : manager.getManager(new Address(request.getReceiverAddress())).getChannels(new Address(request.getSenderAddress()))) {
            builder.addChannel(ChannelStatusMessage.newBuilder()
                .setActive(true)
                .setChannelAddress(state.getChannelAddress().toString())
                .build()
            );
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void registerTransfers(RegisterTransfersRequest request, StreamObserver<RegisterTransfersResponse> responseObserver) {
        for (TransferMessage transferMessage : request.getTransferList()) {
            try {
                manager.registerTransfer(new SignedTransfer(transferMessage));
            } catch (Exception e) {
                log.warn("Invalid transfer", e);
            }
        }
        responseObserver.onNext(RegisterTransfersResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
