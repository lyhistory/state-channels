package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;
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
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.ethereum.TokenService;
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
    private final TokenService tokenService;
    private final PeerConnectionManager peerConnectionManager;
    private final Address senderAddress;
    private final Address signerAddress;

    private ScheduledFuture<?> watchFuture;
    private final Credentials credentials;
    private Address receiverAddress;
    private volatile OutgoingChannelProperties channelProperties;
    private final List<OutgoingChannelState> channels = Collections.synchronizedList(new ArrayList<>());
    private boolean shutdown;

    public OutgoingChannelPool(
        OutgoingChannelManager poolManager, 
        OutgoingChannelProperties channelProperties, 
        TokenService tokenService,
        ContractsManager contractsManager,
        PeerConnectionManager peerConnectionManager,
        ScheduledExecutorService executorService, 
        EthereumConfig ethereumConfig, 
        Address receiverAddress
    ) {
        this.poolManager = poolManager;
        this.tokenService = tokenService;
        this.peerConnectionManager = peerConnectionManager;
        this.credentials = ethereumConfig.getCredentials();
        this.senderAddress = ethereumConfig.getEthAddress();
        this.signerAddress = ethereumConfig.getSignerAddress();
        this.receiverAddress = receiverAddress;
        this.channelProperties = channelProperties;
        this.contractsManager = contractsManager;
        this.executorService = executorService;
    }

    public void setChannelProperties(OutgoingChannelProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    public boolean isFinished() {
        return shutdown && channels.isEmpty();
    }

    private void cycle() {
        try {
            long activeOrOpening = channels.stream().filter(c -> c.getStatus().isAnyOf(ACTIVE, CREATED, CREATING)).count();
            if (!shutdown && activeOrOpening < channelProperties.getActiveChannels()) {
                OutgoingChannelState channel = new OutgoingChannelState(senderAddress, signerAddress, receiverAddress, channelProperties.getBlockchainProperties());
                channels.add(channel);
            }
//            if (shutdown || activeOrOpening > channelProperties.getActiveChannels()) {
// TODO               
//            }
            
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
                checkDeposit(channel);
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

    private void checkDeposit(OutgoingChannelState channel) {
        if (channel.isDepositDeploying()) {
            channel.checkDepositCompleted();
        } else {
            BigInteger balance = channel.getBalance();
            BigInteger deposit = channelProperties.getDeposit();
            BigInteger required = deposit.subtract(balance);
            channel.deposit(tokenService, required);
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
        Future<TransactionReceipt> future = contractsManager.channelManager().newChannel(
            signerAddress, 
            receiverAddress, 
            new Uint256(channelProperties.getBlockchainProperties().getSettleTimeout())
        );
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

    public void shutdown() {
        this.shutdown = true;
    }
}
