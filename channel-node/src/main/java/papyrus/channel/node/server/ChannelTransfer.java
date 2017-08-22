package papyrus.channel.node.server;

import java.math.BigInteger;

public class ChannelTransfer {
    private final BigInteger channelAddress;
    private final BigInteger amount;

    public ChannelTransfer(BigInteger channelAddress, BigInteger amount) {
        this.channelAddress = channelAddress;
        this.amount = amount;
    }

    public BigInteger getChannelAddress() {
        return channelAddress;
    }

    public BigInteger getAmount() {
        return amount;
    }
}
