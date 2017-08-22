package papyrus.channel.node.server.outgoing;

import papyrus.channel.node.entity.ChannelBlockchainProperties;

public class OutgoingChannelProperties {
    private final int activeChannels;
    private final ChannelBlockchainProperties blockchainProperties;

    public OutgoingChannelProperties(int activeChannels, ChannelBlockchainProperties blockchainProperties) {
        this.activeChannels = activeChannels;
        this.blockchainProperties = blockchainProperties;
    }

    public int getActiveChannels() {
        return activeChannels;
    }

    public ChannelBlockchainProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
