package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import papyrus.channel.node.AddParticipantRequest;
import papyrus.channel.node.entity.BlockchainChannelProperties;

public class OutgoingChannelProperties {
    private final int activeChannels;
    private final BigInteger deposit;
    private final BlockchainChannelProperties blockchainProperties;

    public OutgoingChannelProperties(AddParticipantRequest request) {
        this(request.getActiveChannels(), new BigInteger(request.getDeposit()), new BlockchainChannelProperties(request.getProperties()));
    }
    
    public OutgoingChannelProperties(int activeChannels, BigInteger deposit, BlockchainChannelProperties blockchainProperties) {
        this.activeChannels = activeChannels;
        this.deposit = deposit;
        this.blockchainProperties = blockchainProperties;
    }

    public int getActiveChannels() {
        return activeChannels;
    }

    public BigInteger getDeposit() {
        return deposit;
    }

    public BlockchainChannelProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
