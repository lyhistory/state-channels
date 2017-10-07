package papyrus.channel.node.server.channel.outgoing;

import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.base.Throwables;

import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.SignedTransferUnlock;
import papyrus.channel.node.server.ethereum.ContractsManagerFactory;
import papyrus.channel.node.server.ethereum.EthereumService;
import papyrus.channel.node.server.peer.PeerConnectionManager;
import papyrus.channel.node.util.Retriable;

@EnableConfigurationProperties(EthProperties.class)
@Component
public class OutgoingChannelPoolManager {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelPoolManager.class);
    
    private final Map<Address, Map<Address, ChannelPoolProperties>> channelPools = new ConcurrentHashMap<>();
    private final Thread watchThread;

    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private ContractsManagerFactory contractsManagerFactory;
    @Autowired
    private PeerConnectionManager peerConnectionManager;
    @Autowired
    private EthereumConfig ethereumConfig;
    @Autowired
    private OutgoingChannelCoordinator registry;
    @Autowired
    private OutgoingChannelPoolRepository poolRepository;
    @Autowired
    private OutgoingChannelRepository channelRepository;
    @Autowired
    private ScheduledExecutorService executorService;
    @Autowired
    private EthProperties ethProperties;

    public OutgoingChannelPoolManager() {
        this.watchThread = new Thread(this::cycle,"Channel pool watcher");
    }

    private void cycle() {
        while (!Thread.interrupted()) {
            try {
                Map<Address, Map<Address, List<OutgoingChannelState>>> indexed = new HashMap<>();
                registry.all().forEach(
                    channel -> indexed.computeIfAbsent(channel.getChannel().getSenderAddress(), sender -> new HashMap<>())
                        .computeIfAbsent(channel.getChannel().getReceiverAddress(), receiver -> new ArrayList<>())
                        .add(channel)
                );
                indexed.forEach((sender, map) -> map.forEach((receiver,channels) -> managePool(sender, receiver, channels)));
                channelPools.forEach((sender, map) -> map.forEach((receiver,conf) -> {
                    if (indexed.get(sender) == null || indexed.get(sender).get(receiver) == null) {
                        managePool(sender, receiver, Collections.emptyList());
                    }
                }));

                Thread.sleep(1000L);
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

    private void managePool(Address senderAddress, Address receiverAddress, List<OutgoingChannelState> channels) {
        ChannelPoolProperties channelProperties = getProperties(senderAddress, receiverAddress);
        if (channelProperties == null) {
            channels.stream()
                .filter(ch -> ch.isActive() && ch.getOpenBlock() > 0)
                .sorted(Comparator.comparing(OutgoingChannelState::getOpenBlock))
                .forEach(c -> registry.setPolicy(c, OutgoingChannelPolicy.NONE));
        } else {
            channels.forEach(c -> registry.setPolicy(c, channelProperties.getPolicy()));
            long notClosedOrClosing = channels.stream().filter(c -> !c.isCloseRequested()).count();
            if (notClosedOrClosing < channelProperties.getMinActiveChannels()) {
                Address clientAddress = ethereumConfig.getClientAddress(senderAddress);
                OutgoingChannelState newChannel = new OutgoingChannelState(senderAddress, clientAddress, receiverAddress, channelProperties.getBlockchainProperties());
                registry.register(newChannel, channelProperties.getPolicy());
            }
            long active = channels.stream().filter(OutgoingChannelState::isActive).count();
            if (active > channelProperties.getMaxActiveChannels()) {
                channels.stream()
                    .filter(ch -> ch.isActive() && ch.getOpenBlock() > 0)
                    .sorted(Comparator.comparing(OutgoingChannelState::getOpenBlock))
                    .limit(active - channelProperties.getMaxActiveChannels())
                    .forEach(OutgoingChannelState::setNeedClose);
            }
        }
    }

    @EventListener(ContextStartedEvent.class)
    public void start() throws Exception {
        Retriable.wrapTask(() -> {
            loadPools();
            loadChannels();
        })
        .retryOn(NoHostAvailableException.class, UnknownHostException.class)
        .withErrorMessage("Failed to load state from persistent store")
        .call();

        if (ethProperties.isSync()) {
            Retriable.wrapTask(this::syncBlockchainChannels)
                .withErrorMessage("Failed to load channels from blockchain")
                .retryOn(HttpHostConnectException.class)
                .call();
        }

        //        executorService.scheduleWithFixedDelay(this::syncBlockchainChannels, 60, 60, TimeUnit.SECONDS);

        watchThread.start();
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        //TODO close all channels
        watchThread.interrupt();
        watchThread.join();
    }
    
    private void loadPools() {
        for (OutgoingChannelPoolBean bean : poolRepository.all()) {
            if (!ethereumConfig.hasAddress(bean.getSender())) {
                log.warn("Address is not managed, skipping pool: " + bean.getSender());
            } else {
                createOrUpdatePool(
                    bean.getSender(),
                    bean.getReceiver(),
                    new ChannelPoolProperties(bean)
                );
            }
        }
    }

    private void loadChannels() {
        for (OutgoingChannelBean bean : channelRepository.all()) {
            OutgoingChannelState state = registry.getChannel(bean.getAddress())
                .orElse(registry.loadChannel(bean.getAddress()));
            state.updatePersistentState(bean);
        }
    }

    private void syncBlockchainChannels() {
        try {
            for (Address senderAddress : ethereumConfig.getAddresses()) {
                ChannelManagerContract channelManager = contractsManagerFactory.getContractManager(senderAddress).channelManager();
                DynamicArray<Address> array = channelManager.getOutgoingChannels(senderAddress).get();
                List<Address> value = array.getValue();
                for (Address address : value) {
                    OutgoingChannelState state = registry.loadChannel(address);
                    BlockchainChannel channel = state.getChannel();
                    if (state.getStatus() == OutgoingChannelState.Status.SETTLED) continue;
                    log.info("Loaded {} channel from blockchain. address: {}, data: {}", state.getStatus(), address, channel);
                    Optional<OutgoingChannelState> existingState = registry.getChannel(address);
                    if (existingState.isPresent()) {
                        existingState.get().updateBlockchainState(state);
                    } else {
                        registry.register(state, getPolicy(senderAddress, state.getChannel().getReceiverAddress()));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

    
    public OutgoingChannelPolicy getPolicy(Address sender, Address receiver) {
        ChannelPoolProperties properties = getProperties(sender, receiver);
        return properties != null ? properties.getPolicy() : OutgoingChannelPolicy.NONE;
    }

    public ChannelPoolProperties getProperties(Address sender, Address receiver) {
        Map<Address, ChannelPoolProperties> map = channelPools.get(sender);
        return map != null ? map.get(receiver) : null;
    }
    
    public List<OutgoingChannelState> getChannels(Address sender, Address receiver) {
        return registry.getByParticipants(sender, receiver).collect(Collectors.toList());
    }

    public void addPool(Address sender, Address receiver, ChannelPoolProperties config) {
        OutgoingChannelPoolBean bean = new OutgoingChannelPoolBean(sender, receiver, config);
        poolRepository.save(bean);
        createOrUpdatePool(sender, receiver, config);
    }

    private void createOrUpdatePool(Address sender, Address receiver, ChannelPoolProperties config) {
        ethereumConfig.checkAddress(sender);
        channelPools.computeIfAbsent(sender, a -> new ConcurrentHashMap<>()).put(receiver, config);
    }

    public void removePool(Address sender, Address receiver) {
        Map<Address, ChannelPoolProperties> poolMap = channelPools.get(sender);
        if (poolMap == null) return;
        poolMap.remove(receiver);
    }
    
    public void registerTransfer(SignedTransfer signedTransfer) throws SignatureException {
        OutgoingChannelState channelState = registry.getChannel(signedTransfer.getChannelAddress()).orElseThrow(
            () -> new IllegalStateException("Unknown channel address: " + signedTransfer.getChannelAddress())
        );
        signedTransfer.verifySignature(channelState.getChannel().getClientAddress());
        channelState.registerTransfer(signedTransfer);
    }

    public void registerTransferUnlock(SignedTransferUnlock transferUnlock) {
        OutgoingChannelState channelState = registry.getChannel(transferUnlock.getChannelAddress()).orElseThrow(
            () -> new IllegalStateException("Unknown channel address: " + transferUnlock.getChannelAddress())
        );
        ChannelProperties properties = channelState.getChannel().getProperties();
        properties.getAuditor().ifPresent(expectedSigner -> {
            try {
                transferUnlock.verifySignature(expectedSigner);
            } catch (SignatureException e) {
                throw new RuntimeException(e);
            }
        });
        channelState.unlockTransfer(transferUnlock);
    }

    public boolean requestCloseChannel(Address address) {
        return registry.getChannel(address).map(OutgoingChannelState::setNeedClose).orElse(false);
    }

    public Collection<ChannelPoolProperties> getPools(Address senderAddress, Address receiverAddress) {
        Map<Address, ChannelPoolProperties> poolMap = channelPools.get(senderAddress);
        return receiverAddress == null ? poolMap.values() : Collections.singleton(poolMap.get(receiverAddress));
    }
}
