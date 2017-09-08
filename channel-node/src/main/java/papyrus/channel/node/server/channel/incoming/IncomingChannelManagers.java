package papyrus.channel.node.server.channel.incoming;

import java.security.SignatureException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.SignedTransfer;

@Service
public class IncomingChannelManagers {

    private Map<Address, IncomingChannelManager> managers = new ConcurrentHashMap<>();
    private final EthereumConfig config;
    private IncomingChannelRegistry registry;

    public IncomingChannelManagers(EthereumConfig config, IncomingChannelRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public IncomingChannelManager getManager(Address receiverAddress) {
        IncomingChannelManager manager = managers.get(receiverAddress);
        if (manager == null) throw new IllegalStateException();
        return manager;
    }

    private IncomingChannelManager getOrCreateManager(Address receiverAddress) {
        return managers.computeIfAbsent(receiverAddress, address -> new IncomingChannelManager(config.getContractManager(receiverAddress)));
    }

    public void registerTransfer(SignedTransfer signedTransfer) {
        IncomingChannelState state = registry.get(signedTransfer.getChannelAddress()).orElseThrow(() -> new IllegalArgumentException("Channel not found: " + signedTransfer.getChannelAddress()));
        if (!state.registerTransfer(signedTransfer)) {
            throw new IllegalArgumentException();
        }
    }

    public boolean requestCloseChannel(Address address) {
        return registry.get(address).map(IncomingChannelState::requestClose).orElse(false);
    }

    public IncomingChannelState load(Address address) {
        IncomingChannelState state = registry.getOrLoad(address);
        if (state != null) {
            return getOrCreateManager(state.getReceiverAddress()).putChannel(state);
        }
        return null;
    }

    public void updateSenderState(SignedChannelState updateSenderState) throws SignatureException {
        Address channelAddress = updateSenderState.getChannelAddress();
        IncomingChannelState channelState = load(channelAddress);
        if (channelState == null) {
            throw new IllegalStateException("Channel not found: " + channelAddress);
        }
        updateSenderState.verifySignature(channelState.getChannel().getSenderAddress()::equals);

        channelState.updateReceiverState(updateSenderState);
    }
}
