package papyrus.channel.node.server.channel.outgoing;

import java.security.SignatureException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.SignedTransferUnlock;
import papyrus.channel.node.server.channel.incoming.OutgoingChannelRegistry;
import papyrus.channel.node.server.ethereum.ContractsManagerFactory;
import papyrus.channel.node.server.ethereum.EthereumService;
import papyrus.channel.node.server.ethereum.TokenConvert;
import papyrus.channel.node.server.peer.PeerConnectionManager;

@Component
public class OutgoingChannelPoolManager {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelPoolManager.class);
    
    private final Map<Address, Map<Address, OutgoingChannelPool>> channelPools = new ConcurrentHashMap<>();

    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private ContractsManagerFactory contractsManagerFactory;
    @Autowired
    private PeerConnectionManager peerConnectionManager;
    @Autowired
    private EthereumConfig ethereumConfig;
    @Autowired
    private OutgoingChannelRegistry registry;
    @Autowired
    private OutgoingChannelPoolRepository repository;
    @Autowired
    private ScheduledExecutorService executorService;

    @EventListener(ContextStartedEvent.class)
    public void start() throws Exception {
        loadPersistentChannels();
        syncChannels();
        executorService.scheduleWithFixedDelay(this::cleanup, 1, 1, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::syncChannels, 60, 60, TimeUnit.SECONDS);
    }

    private void loadPersistentChannels() {
        //loading pools
        for (OutgoingChannelPoolBean bean : repository.iterate()) {
            OutgoingChannelPool pool = createOrUpdatePool(
                bean.getSender(),
                bean.getReceiver(),
                new ChannelPoolProperties(bean.getMinActive(), bean.getMaxActive(), TokenConvert.toWei(bean.getDeposit()), new ChannelProperties())
            );
            if (bean.isShutdown()) {
                pool.shutdown();
            }
        }
    }

    private void syncChannels() {
//        channelManagerContract.getOutgoingChannels()
    }

    @PreDestroy
    public void destroy() {
        channelPools.values().stream().flatMap(m -> m.values().stream()).forEach(OutgoingChannelPool::destroy);
    }

    public List<OutgoingChannelState> getChannels(Address sender, Address receiver) {

        Map<Address, OutgoingChannelPool> poolMap = channelPools.get(sender);
        if (poolMap == null) return Collections.emptyList();
        
        OutgoingChannelPool pool = poolMap.get(receiver);
        if (pool == null) {
            return Collections.emptyList();
        }
        return pool.getChannelsState();
    }

    public void addPool(Address sender, Address receiver, ChannelPoolProperties config) {
        OutgoingChannelPoolBean bean = new OutgoingChannelPoolBean(sender, receiver, config);
        repository.save(bean);
        createOrUpdatePool(sender, receiver, config);
    }

    private OutgoingChannelPool createOrUpdatePool(Address sender, Address receiver, ChannelPoolProperties config) {
        ethereumConfig.checkAddress(sender);
        return channelPools.computeIfAbsent(sender, a -> new ConcurrentHashMap<>()).compute(receiver, (addr, channelPool)->{
            if (channelPool == null) {
                Address clientAddress = ethereumConfig.getClientAddress(sender);
                log.info("Adding pool {}->{}, client: {}: {}", 
                    sender,
                    receiver,
                    clientAddress, 
                    config
                );
                channelPool = new OutgoingChannelPool(
                    registry, 
                    config,
                    ethereumService, 
                    contractsManagerFactory.getContractManager(sender),
                    peerConnectionManager,
                    ethereumConfig.getCredentials(sender),
                    clientAddress, 
                    receiver
                );

                channelPool.start();
                return channelPool;
            }
            channelPool.cancelShutdown();
            channelPool.setChannelProperties(config);
            return channelPool;
        });
    }

    public void removePool(Address sender, Address receiver) {
        Map<Address, OutgoingChannelPool> poolMap = channelPools.get(sender);
        if (poolMap == null) return;
        OutgoingChannelPool manager = poolMap.get(receiver);
        if (manager != null) {
            repository.markShutdown(sender, receiver);
            manager.shutdown();
        }
    }
    
    private void cleanup() {
        channelPools.values().stream().flatMap(m -> m.values().stream()).forEach(mgr -> {
            if (mgr.isFinished()) {
                mgr.destroy();
            }
        });
    }

    public void registerTransfer(SignedTransfer signedTransfer) throws SignatureException {
        OutgoingChannelState channelState = registry.get(signedTransfer.getChannelAddress()).orElseThrow(
            () -> new IllegalStateException("Unknown channel address: " + signedTransfer.getChannelAddress())
        );
        signedTransfer.verifySignature(channelState.getChannel().getClientAddress());
        channelState.registerTransfer(signedTransfer);
    }

    public void registerTransferUnlock(SignedTransferUnlock transferUnlock) {
        OutgoingChannelState channelState = registry.get(transferUnlock.getChannelAddress()).orElseThrow(
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
        return registry.get(address).map(OutgoingChannelState::requestClose).orElse(false);
    }

    public Collection<OutgoingChannelPool> getPools(Address senderAddress, Address receiverAddress) {
        Map<Address, OutgoingChannelPool> poolMap = channelPools.get(senderAddress);
        return receiverAddress == null ? poolMap.values() : Collections.singleton(poolMap.get(receiverAddress));
    }
}
