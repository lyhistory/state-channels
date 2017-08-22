package papyrus.channel.node.server.outgoing;

import org.web3j.abi.datatypes.Address;

public class OutgoingChannelState {
    private final Address channelAddress;

    public OutgoingChannelState(Address channelAddress) {
        this.channelAddress = channelAddress;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }
}
