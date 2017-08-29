package papyrus.channel.node.entity;

import papyrus.channel.ChannelPropertiesMessage;

public class ChannelProperties extends DataObject {
    private long settleTimeout;

    public ChannelProperties() {
    }

    public ChannelProperties(ChannelPropertiesMessage properties) {
        this.settleTimeout = (long) properties.getSettleTimeout();
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }

    public void setSettleTimeout(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }
}
