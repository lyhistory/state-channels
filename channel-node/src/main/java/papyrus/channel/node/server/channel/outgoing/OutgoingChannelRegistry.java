package papyrus.channel.node.server.channel.outgoing;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.ethereum.ContractsManagerFactory;

@Component
public class OutgoingChannelRegistry {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelRegistry.class);
    
    private final Map<Address, OutgoingChannelState> allChannelsByAddress = new ConcurrentHashMap<>();

    private final ContractsManager contractsManager;

    public OutgoingChannelRegistry(ContractsManagerFactory factory) {
        contractsManager = factory.getMainContractManager();
    }

    public void setAddress(OutgoingChannelState channel, Address channelAddress) {
        if (allChannelsByAddress.putIfAbsent(channelAddress, channel) != null) {
            throw new IllegalStateException("Duplicate channel: " + channel.getChannelAddress());
        }
    }

    public Optional<OutgoingChannelState> get(Address channelAddress) {
        return Optional.ofNullable(allChannelsByAddress.get(channelAddress));
    }

    public OutgoingChannelState loadChannel(Address address) {
        ChannelContract contract = contractsManager.load(ChannelContract.class, address);
        BlockchainChannel channel = BlockchainChannel.fromExistingContract(contractsManager.channelManager(), contract);
        return new OutgoingChannelState(channel);
    }

    public Collection<OutgoingChannelState> all() {
        return allChannelsByAddress.values();
    }
}
