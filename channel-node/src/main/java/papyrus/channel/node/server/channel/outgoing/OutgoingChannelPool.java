package papyrus.channel.node.server.channel.outgoing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.google.common.base.Preconditions;

import papyrus.channel.Error;
import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.peer.PeerConnection;
import papyrus.channel.node.server.peer.PeerConnectionManager;
import papyrus.channel.protocol.ChannelUpdateRequest;

import static papyrus.channel.node.server.channel.outgoing.OutgoingChannelState.Status.ACTIVE;
import static papyrus.channel.node.server.channel.outgoing.OutgoingChannelState.Status.CREATED;
import static papyrus.channel.node.server.channel.outgoing.OutgoingChannelState.Status.CREATING;

/**
 * Channel pool for single receiving address.
 */
public class OutgoingChannelPool {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelPool.class);
    
    private final ScheduledExecutorService executorService;
    private final ContractsManager contractsManager;
    private final OutgoingChannelManager poolManager;
    private final PeerConnectionManager peerConnectionManager;
    private final Address senderAddress;

    private ScheduledFuture<?> watchFuture;
    private final Credentials credentials;
    private Address receiverAddress;
    private volatile OutgoingChannelProperties config;
    private final List<OutgoingChannelState> channels = Collections.synchronizedList(new ArrayList<>());

    public OutgoingChannelPool(
        OutgoingChannelManager poolManager, 
        OutgoingChannelProperties config, 
        ContractsManager contractsManager,
        PeerConnectionManager peerConnectionManager,
        ScheduledExecutorService executorService, 
        Credentials credentials, 
        Address receiverAddress
    ) {
        this.poolManager = poolManager;
        this.peerConnectionManager = peerConnectionManager;
        this.senderAddress = new Address(credentials.getAddress());
        this.credentials = credentials;
        this.receiverAddress = receiverAddress;
        this.config = config;
        this.contractsManager = contractsManager;
        this.executorService = executorService;
    }

    public void setConfig(OutgoingChannelProperties config) {
        this.config = config;
    }

    public boolean isFinished() {
        return config.getActiveChannels() == 0 && channels.isEmpty();
    }

    private void cycle() {
        try {
            long activeOrOpening = channels.stream().filter(c -> c.getStatus().isAnyOf(ACTIVE, CREATED, CREATING)).count();
            if (activeOrOpening < config.getActiveChannels()) {
                OutgoingChannelState channel = new OutgoingChannelState(senderAddress, receiverAddress, config.getBlockchainProperties());
                channels.add(channel);
            }
            for (OutgoingChannelState channel : channels) {
                OutgoingChannelState.Status status = channel.getStatus();
                try {
                    makeTransitions(channel);
                    if (channel.getStatus() != status) {
                        log.info("Outgoing channel:{} to receiver:{} updated from:{} to:{}", channel.getAddressSafe(), receiverAddress, status, channel.getStatus());
                    }
                } catch (Exception e) {
                    log.info("Channel update completed exceptionally", e);
                    //TODO error handling 
                }
            }
        } catch (Throwable e) {
            log.error("Cycle failed", e);
        }
    }

    private void makeTransitions(OutgoingChannelState channel) {
        switch (channel.getStatus()) {
            case NEW:
                startCreating(channel);
                break;
            case CREATING:
                checkIfCreated(channel);
                break;
            case CREATED:
                //TODO ask counterparty
                channel.setActive();
                break;
            case ACTIVE:
                if (channel.isNeedsSync()) {
                    syncChannel(channel);
                }
                break;
            case SUSPENDING:
                break;
            case CLOSED:
                break;
            case SETTLED:
                break;
            case DISPUTING:
                break;
        }
    }

    private void syncChannel(OutgoingChannelState channel) {
        PeerConnection connection = peerConnectionManager.getConnection(receiverAddress);
        SignedChannelState state = channel.createState();
        state.sign(credentials.getEcKeyPair());
        Error error = connection.getChannelPeer().update(
            ChannelUpdateRequest.newBuilder()
                .setState(state.toMessage())
                .build()
        ).getError();
        if (error == null) {
            channel.syncCompleted(state);
        }
    }

    private void startCreating(OutgoingChannelState channel) {
        Future<TransactionReceipt> future = contractsManager.channelManager().newChannel(receiverAddress, new Uint256(config.getBlockchainProperties().getSettleTimeout()));
        //todo store transaction hash instead of future
        channel.onDeploying(CompletableFuture.supplyAsync(() -> {
            try {
                TransactionReceipt receipt = future.get();
                List<ChannelManagerContract.ChannelNewEventResponse> events = contractsManager.channelManager().getChannelNewEvents(receipt);
                if (events.isEmpty()) {
                    throw new IllegalStateException("Channel contract was not created");
                }
                Preconditions.checkState(events.size() == 1);
                Address address = events.get(0).channel_address;
                ChannelContract contract = contractsManager.load(ChannelContract.class, address);
                contract.setTransactionReceipt(receipt);
                return contract;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void checkIfCreated(OutgoingChannelState channel) {
        channel.checkIfDeployed().ifPresent(contract -> {
            channel.onDeployed(contract);
            poolManager.putChannel(channel);
        });
    }

    public void start() {
        watchFuture = executorService.scheduleWithFixedDelay(this::cycle, ThreadLocalRandom.current().nextInt(1), 1, TimeUnit.SECONDS);
    }

    public void destroy() {
        //TODO close all channels
        if (watchFuture != null) {
            watchFuture.cancel(false);
        }
    }

    public List<OutgoingChannelState> getChannelsState() {
        synchronized (channels) {
            return channels.stream().filter(c -> c.getStatus() == ACTIVE).collect(Collectors.toList());
        }
    }
}
