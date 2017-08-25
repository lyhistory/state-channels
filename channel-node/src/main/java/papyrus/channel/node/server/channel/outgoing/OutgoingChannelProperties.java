package papyrus.channel.node.server.channel.outgoing;

import papyrus.channel.node.entity.BlockchainChannelProperties;

public class OutgoingChannelProperties {
    private final int activeChannels;
    private final BlockchainChannelProperties blockchainProperties;

    public OutgoingChannelProperties(int activeChannels, BlockchainChannelProperties blockchainProperties) {
        this.activeChannels = activeChannels;
        this.blockchainProperties = blockchainProperties;
    }

    public int getActiveChannels() {
        return activeChannels;
    }

    public BlockchainChannelProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
