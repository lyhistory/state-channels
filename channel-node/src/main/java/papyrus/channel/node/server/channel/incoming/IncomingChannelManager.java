package papyrus.channel.node.server.channel.incoming;

import java.security.SignatureException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.ethereum.ContractsManager;

@Service
public class IncomingChannelManager {
    private static final Logger log = LoggerFactory.getLogger(IncomingChannelManager.class);
    private final Address receiverAddress;

    private Map<Address, IncomingChannelState> allChannels = new ConcurrentHashMap<>(); 
    private Map<Address, Map<Address, IncomingChannelState>> channelsBySender = new ConcurrentHashMap<>();

    private final ContractsManager contractsManager;
    private final ChannelManagerContract channelManagerContract;

    public IncomingChannelManager(ContractsManager contractsManager) {
        this.receiverAddress = new Address(contractsManager.linkingManager().getFromAddress());
        this.contractsManager = contractsManager;
        this.channelManagerContract = contractsManager.channelManager();
    }

    public IncomingChannelState register(Address address) {
        log.info("Registering channel: {}", address);
        try {
            IncomingChannelState state = allChannels.computeIfAbsent(address, this::loadChannel);
            Address senderAddress = state.getChannel().getSenderAddress();
            channelsBySender.computeIfAbsent(senderAddress, a -> new ConcurrentHashMap<>()).putIfAbsent(address, state);
            return state;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public IncomingChannelState loadChannel(Address address) {
        log.info("Loading channel from blockchain: {}", address);
        ChannelContract contract = contractsManager.load(ChannelContract.class, address);
        BlockchainChannel channel = BlockchainChannel.fromExistingContract(channelManagerContract, contract);
        Preconditions.checkArgument(receiverAddress.equals(channel.getReceiverAddress()), "Wrong channel receiver: %s", channel.getReceiverAddress());
        return new IncomingChannelState(channel);
    }

    public void updateSenderState(SignedChannelState updateSenderState) throws SignatureException {
        Address channelAddress = updateSenderState.getChannelAddress();
        IncomingChannelState channelState = allChannels.get(channelAddress);
        if (channelState == null) {
            log.info("Channel {} not found during update, loading", channelAddress);
            channelState = register(channelAddress);
        }
        if (channelState == null) {
            throw new IllegalStateException("Channel not found: " + channelAddress);
        }
        updateSenderState.verifySignature(channelState.getChannel().getSenderAddress()::equals);

        channelState.updateReceiverState(updateSenderState);
    }

    public Collection<IncomingChannelState> getChannels(Address address) {
        return channelsBySender.getOrDefault(address, Collections.emptyMap()).values();
    }

    public void registerTransfer(SignedTransfer signedTransfer) throws SignatureException {
        IncomingChannelState channelState = allChannels.get(signedTransfer.getChannelAddress());
        if (channelState == null) {
            throw new IllegalStateException("Unknown channel address: " + signedTransfer.getChannelAddress());
        }
        channelState.registerTransfer(signedTransfer);
    }
}
