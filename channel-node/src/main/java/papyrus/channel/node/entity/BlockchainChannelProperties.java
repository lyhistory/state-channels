package papyrus.channel.node.entity;

public class BlockchainChannelProperties extends DataObject {
    private long settleTimeout;

    public BlockchainChannelProperties(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }
}
