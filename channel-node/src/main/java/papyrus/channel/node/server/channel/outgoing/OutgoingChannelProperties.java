package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import papyrus.channel.node.AddParticipantRequest;
import papyrus.channel.node.entity.BlockchainChannelProperties;

public class OutgoingChannelProperties {
    
    private final int minActiveChannels;
    private final int maxActiveChannels;
    private final BigInteger deposit;
    private final BlockchainChannelProperties blockchainProperties;

    public OutgoingChannelProperties(AddParticipantRequest request) {
        this(request.getMinActiveChannels(), request.getMaxActiveChannels(), new BigInteger(request.getDeposit()), new BlockchainChannelProperties(request.getProperties()));
    }
    
    public OutgoingChannelProperties(int minActiveChannels, int maxActiveChannels, BigInteger deposit, BlockchainChannelProperties blockchainProperties) {
        this.minActiveChannels = minActiveChannels;
        this.maxActiveChannels = maxActiveChannels;
        this.deposit = deposit;
        this.blockchainProperties = blockchainProperties;
    }

    public int getMinActiveChannels() {
        return minActiveChannels;
    }

    public int getMaxActiveChannels() {
        return maxActiveChannels;
    }

    public BigInteger getDeposit() {
        return deposit;
    }

    public BlockchainChannelProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
