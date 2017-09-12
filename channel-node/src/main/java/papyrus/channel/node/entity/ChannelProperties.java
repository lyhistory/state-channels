package papyrus.channel.node.entity;

import org.web3j.abi.datatypes.Address;

import papyrus.channel.ChannelPropertiesMessage;

public class ChannelProperties extends DataObject {
    private long closeTimeout;
    private long settleTimeout;
    private Address auditor;

    public ChannelProperties() {
    }

    public ChannelProperties(ChannelPropertiesMessage properties) {
        this.closeTimeout = properties.getCloseTimeout();
        this.settleTimeout = properties.getSettleTimeout();
        auditor = properties.getAuditorAddress() != null && !properties.getAuditorAddress().isEmpty() ? new Address(properties.getAuditorAddress()) : Address.DEFAULT;
    }

    public long getCloseTimeout() {
        return closeTimeout;
    }

    public void setCloseTimeout(long closeTimeout) {
        this.closeTimeout = closeTimeout;
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }

    public void setSettleTimeout(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }

    public Address getAuditor() {
        return auditor;
    }

    public boolean hasAuditor() {
        return auditor.getValue().signum() != 0;
    }

    public void setAuditor(Address auditor) {
        this.auditor = auditor;
    }

    public ChannelPropertiesMessage toMessage() {
        ChannelPropertiesMessage.Builder builder = ChannelPropertiesMessage.newBuilder();
        builder.setCloseTimeout(closeTimeout);
        builder.setSettleTimeout(settleTimeout);
        if (auditor != null) {
            builder.setAuditorAddress(auditor.toString());
        }
        return builder.build();
    }
}
