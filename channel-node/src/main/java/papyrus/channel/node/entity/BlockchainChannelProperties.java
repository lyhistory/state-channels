package papyrus.channel.node.entity;

import papyrus.channel.ChannelProperties;

public class BlockchainChannelProperties extends DataObject {
    private long settleTimeout;

    public BlockchainChannelProperties() {
    }

    public BlockchainChannelProperties(ChannelProperties properties) {
        this.settleTimeout = (long) properties.getSettleTimeout();
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }

    public void setSettleTimeout(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }
}
