package papyrus.channel.node.server.channel.outgoing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.incoming.OutgoingChannelRegistry;
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.ethereum.EthereumService;
import papyrus.channel.node.server.ethereum.TokenService;
import papyrus.channel.node.server.peer.PeerConnection;
import papyrus.channel.node.server.peer.PeerConnectionManager;
import papyrus.channel.protocol.ChannelOpenedRequest;
import papyrus.channel.protocol.ChannelOpenedResponse;
import papyrus.channel.protocol.ChannelUpdateRequest;

import static papyrus.channel.node.server.channel.outgoing.OutgoingChannelState.Status.ACTIVE;
import static papyrus.channel.node.server.channel.outgoing.OutgoingChannelState.Status.DISPOSABLE;

/**
 * Channel pool for single receiving address.
 */
public class OutgoingChannelPool {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelPool.class);
    
    private final ContractsManager contractsManager;
    private final OutgoingChannelRegistry registry;
    private final EthereumService ethereumService;
    private final TokenService tokenService;
    private final PeerConnectionManager peerConnectionManager;
    private final Address senderAddress;
    private final Address clientAddress;
    private final Thread watchThread;

    private final Credentials credentials;
    private Address receiverAddress;
    private volatile ChannelPoolProperties channelProperties;
    private final List<OutgoingChannelState> channels = Collections.synchronizedList(new ArrayList<>());
    private boolean shutdown;

    public OutgoingChannelPool(
        OutgoingChannelRegistry registry,
        ChannelPoolProperties channelProperties,
        EthereumService ethereumService,
        ContractsManager contractsManager,
        PeerConnectionManager peerConnectionManager,
        Credentials senderCredentials,
        Address clientAddress,
        Address receiverAddress
    ) {
        this.registry = registry;
        this.ethereumService = ethereumService;
        this.tokenService = contractsManager.getTokenService();
        this.peerConnectionManager = peerConnectionManager;
        this.credentials = senderCredentials;
        this.senderAddress = new Address(senderCredentials.getAddress());
        this.clientAddress = clientAddress;
        this.receiverAddress = receiverAddress;
        this.channelProperties = channelProperties;
        this.contractsManager = contractsManager;
        this.watchThread = new Thread(this::cycle,"Channel pool OUT " + senderAddress + " -> " + receiverAddress);
    }

    public void setChannelProperties(ChannelPoolProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    public ChannelPoolProperties getChannelProperties() {
        return channelProperties;
    }

    public boolean isFinished() {
        return shutdown && channels.isEmpty();
    }

    public Address getSenderAddress() {
        return senderAddress;
    }

    public Address getClientAddress() {
        return clientAddress;
    }

    public Address getReceiverAddress() {
        return receiverAddress;
    }

    private void cycle() {
        while (!Thread.interrupted()) {
            try {
                boolean updated = false;
                long notClosedOrClosing = channels.stream().filter(c -> !c.isClosed()).count();
                if (!shutdown && notClosedOrClosing < channelProperties.getMinActiveChannels()) {
                    channels.add(new OutgoingChannelState(senderAddress, clientAddress, receiverAddress, channelProperties.getBlockchainProperties()));
                }
                long active = channels.stream().filter(OutgoingChannelState::isActive).count();
                if (active > channelProperties.getMaxActiveChannels()) {
                    channels.stream()
                        .filter(ch -> ch.isActive() && ch.getOpenBlock() > 0)
                        .sorted(Comparator.comparing(OutgoingChannelState::getOpenBlock))
                        .limit(active - channelProperties.getMaxActiveChannels())
                        .forEach(this::closeChannel);
                }
                channels.removeIf(ch -> ch.getStatus() == DISPOSABLE);

                for (OutgoingChannelState channel : channels) {
                    OutgoingChannelState.Status status = channel.getStatus();
                    try {
                        if (channel.checkTransitionInProgress()) continue;

                        if (channel.isCloseRequested() && !channel.isNeedsSync()) {
                            closeChannel(channel);
                        } else {
                            makeTransitions(channel);
                        }

                        if (channel.getStatus() != status) {
                            updated = true;
                            log.info("Outgoing channel:{} to receiver:{} updated from:{} to:{}", channel.getAddressSafe(), receiverAddress, status, channel.getStatus());
                        }
                    } catch (Exception e) {
                        log.info("Channel update completed exceptionally", e);
                        //TODO error handling 
                    }
                }
                if (!updated) {
                    Thread.sleep(1000L);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                log.error("Cycle failed", e);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e1) {
                    break;
                }
            }
        }
    }

    private void closeChannel(OutgoingChannelState channel) {
        switch (channel.getStatus()) {
            case NEW:
                channel.makeDisposable();
                break;
            case CREATED:
            case ACTIVE:
                channel.doClose();
                break;
        }
    }

    private void makeTransitions(OutgoingChannelState channel) {
        switch (channel.getStatus()) {
            case NEW:
                startCreating(channel);
                break;
            case CREATED:
                channel.approveDeposit(tokenService, channelProperties.getDeposit());
                break;
            case DEPOSIT_APPROVED:
                channel.deposit(channelProperties.getDeposit());
                break;
            case OPENED:
                PeerConnection connection = peerConnectionManager.getConnection(receiverAddress);
                ChannelOpenedResponse response = connection.getChannelPeer().opened(ChannelOpenedRequest.newBuilder().setChannelId(channel.getChannelAddress().toString()).build());
                if (response.hasError()) {
                    log.warn("Failed to notify counterparty {}", response.getError().getMessage());
                } else {
                    channel.setActive();
                }
                break;
            case ACTIVE:
                if (channel.isNeedsSync()) {
                    //TODO make async call
                    syncChannel(channel);
                }
                break;
            case CLOSED:
                channel.settleIfPossible(ethereumService.getBlockNumber());
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
        ChannelProperties properties = channelProperties.getBlockchainProperties();
        Future<TransactionReceipt> future = contractsManager.channelManager().newChannel(
            clientAddress, 
            receiverAddress, 
            new Uint256(properties.getSettleTimeout()),
            properties.getAuditor().orElse(Address.DEFAULT)
        );
        //todo store transaction hash instead of future
        channel.startDeploying(CompletableFuture.supplyAsync(() -> {
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
                registry.setAddress(channel, address);
                return contract;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public void start() {
        watchThread.start();
    }

    public void destroy() {
        log.info("Destroying pool {}", receiverAddress);
        //TODO close all channels
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    public List<OutgoingChannelState> getChannelsState() {
        synchronized (channels) {
            return channels.stream().filter(c -> c.getStatus() == ACTIVE).collect(Collectors.toList());
        }
    }

    public void shutdown() {
        this.shutdown = true;
        channels.forEach(this::closeChannel);
    }

    public void cancelShutdown() {
        this.shutdown = false;
    }
}
