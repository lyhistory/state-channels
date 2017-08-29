package papyrus.channel.node.server.channel.incoming;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.server.channel.outgoing.OutgoingChannelState;

@Component
public class OutgoingChannelRegistry {
    private final Map<Address, OutgoingChannelState> allChannelsByAddress = new ConcurrentHashMap<>();

    public void setAddress(OutgoingChannelState channel, Address channelAddress) {
        if (allChannelsByAddress.putIfAbsent(channelAddress, channel) != null) {
            throw new IllegalStateException("Duplicate channel: " + channel.getChannelAddress());
        }
    }

    public Optional<OutgoingChannelState> get(Address channelAddress) {
        return Optional.ofNullable(allChannelsByAddress.get(channelAddress));
    }
}
