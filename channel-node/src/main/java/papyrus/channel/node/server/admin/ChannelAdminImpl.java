package papyrus.channel.node.server.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.AddChannelPoolResponse;
import papyrus.channel.node.ChannelAdminGrpc;
import papyrus.channel.node.CloseChannelRequest;
import papyrus.channel.node.CloseChannelResponse;
import papyrus.channel.node.RemoveChannelPoolRequest;
import papyrus.channel.node.RemoveChannelPoolResponse;
import papyrus.channel.node.server.channel.incoming.IncomingChannelManagers;
import papyrus.channel.node.server.channel.outgoing.ChannelPoolProperties;
import papyrus.channel.node.server.channel.outgoing.OutgoingChannelPoolManager;

//TODO client authentication
@Component
public class ChannelAdminImpl extends ChannelAdminGrpc.ChannelAdminImplBase {
    private static final Logger log = LoggerFactory.getLogger(ChannelAdminImpl.class);
    public static final int MAX_CHANNELS_PER_ADDRESS = 100;
    
    private OutgoingChannelPoolManager outgoingChannelPoolManager;
    private IncomingChannelManagers incomingChannelManagers;

    public ChannelAdminImpl(OutgoingChannelPoolManager outgoingChannelPoolManager, IncomingChannelManagers incomingChannelManagers) {
        this.outgoingChannelPoolManager = outgoingChannelPoolManager;
        this.incomingChannelManagers = incomingChannelManagers;
    }

    @Override
    public void addChannelPool(AddChannelPoolRequest request, StreamObserver<AddChannelPoolResponse> responseObserver) {
        if (request.getMinActiveChannels() < 0 || request.getMinActiveChannels() > MAX_CHANNELS_PER_ADDRESS) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(String.format("Illegal min active channels: %d", request.getMinActiveChannels())).asException());
            return;
        }
        if (request.getMaxActiveChannels() < 0 || request.getMaxActiveChannels() > MAX_CHANNELS_PER_ADDRESS) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(String.format("Illegal min active channels: %d", request.getMaxActiveChannels())).asException());
            return;
        }
        
        ChannelPoolProperties properties = new ChannelPoolProperties(request);
        
        if (properties.getBlockchainProperties().getSettleTimeout() < 6) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(String.format("Illegal settle timeout: %d", properties.getBlockchainProperties().getSettleTimeout())).asException());
            return;
        }
        
        if (properties.getDeposit().signum() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(String.format("Illegal deposit: %d", properties.getDeposit())).asException());
            return;
        }
        
        outgoingChannelPoolManager.addPool(new Address(request.getSenderAddress()), new Address(request.getReceiverAddress()), properties);
        
        responseObserver.onNext(AddChannelPoolResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeChannelPool(RemoveChannelPoolRequest request, StreamObserver<RemoveChannelPoolResponse> responseObserver) {
        outgoingChannelPoolManager.removePool(new Address(request.getSenderAddress()), new Address(request.getReceiverAddress()));
        responseObserver.onNext(RemoveChannelPoolResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void requestCloseChannel(CloseChannelRequest request, StreamObserver<CloseChannelResponse> responseObserver) {
        incomingChannelManagers.requestCloseChannel(new Address(request.getChannelAddress()));
        outgoingChannelPoolManager.requestCloseChannel(new Address(request.getChannelAddress()));
        responseObserver.onNext(CloseChannelResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
