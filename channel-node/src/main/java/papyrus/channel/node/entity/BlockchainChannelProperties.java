package papyrus.channel.node.entity;

import papyrus.channel.ChannelPropertiesMessage;

public class BlockchainChannelProperties extends DataObject {
    private long settleTimeout;

    public BlockchainChannelProperties() {
    }

    public BlockchainChannelProperties(ChannelPropertiesMessage properties) {
        this.settleTimeout = (long) properties.getSettleTimeout();
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }

    public void setSettleTimeout(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }
}
