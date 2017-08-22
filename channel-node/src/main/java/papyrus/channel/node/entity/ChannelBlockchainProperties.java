package papyrus.channel.node.entity;

public class ChannelBlockchainProperties extends DataObject {
    private long settleTimeout;

    public ChannelBlockchainProperties(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }
}
