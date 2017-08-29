package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.entity.ChannelProperties;

public class ChannelPoolProperties {
    
    private final int minActiveChannels;
    private final int maxActiveChannels;
    private final BigInteger deposit;
    private final ChannelProperties blockchainProperties;

    public ChannelPoolProperties(AddChannelPoolRequest request) {
        this(request.getMinActiveChannels(), request.getMaxActiveChannels(), new BigInteger(request.getDeposit()), new ChannelProperties(request.getProperties()));
    }
    
    public ChannelPoolProperties(int minActiveChannels, int maxActiveChannels, BigInteger deposit, ChannelProperties blockchainProperties) {
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

    public ChannelProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
