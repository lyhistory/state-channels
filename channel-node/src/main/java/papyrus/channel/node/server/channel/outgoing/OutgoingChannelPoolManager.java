package papyrus.channel.node.server.channel.outgoing;

import java.security.SignatureException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.incoming.OutgoingChannelRegistry;
import papyrus.channel.node.server.ethereum.EthereumService;
import papyrus.channel.node.server.peer.PeerConnectionManager;

@Component
public class OutgoingChannelPoolManager {
    private final Map<Address, Map<Address, OutgoingChannelPool>> channelPools = new ConcurrentHashMap<>();

    private final EthereumService ethereumService;
    private final PeerConnectionManager peerConnectionManager;
    private final EthereumConfig ethereumConfig;
    private final OutgoingChannelRegistry registry;

    public OutgoingChannelPoolManager(
        EthereumService ethereumService,
        EthereumConfig ethereumConfig,
        ScheduledExecutorService executorService,
        PeerConnectionManager peerConnectionManager,
        OutgoingChannelRegistry registry) {
        this.ethereumService = ethereumService;
        this.ethereumConfig = ethereumConfig;
        this.peerConnectionManager = peerConnectionManager;
        this.registry = registry;
        syncChannels();
        executorService.scheduleWithFixedDelay(this::cleanup, 1, 1, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::syncChannels, 60, 60, TimeUnit.SECONDS);
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
        channelPools.computeIfAbsent(sender, a -> new ConcurrentHashMap<>()).compute(receiver, (addr, channelPool)->{
            if (channelPool == null) {
                channelPool = new OutgoingChannelPool(
                    registry, 
                    config,
                    ethereumService, 
                    ethereumConfig.getContractManager(sender),
                    peerConnectionManager,
                    ethereumConfig.getCredentials(sender),
                    ethereumConfig.getClientAddress(sender), 
                    receiver
                );
                channelPool.start();
                return channelPool;
            }
            channelPool.setChannelProperties(config);
            return channelPool;
        });
    }
    
    public void removePool(Address sender, Address receiver) {
        Map<Address, OutgoingChannelPool> poolMap = channelPools.get(sender);
        if (poolMap == null) return;
        OutgoingChannelPool manager = poolMap.get(receiver);
        if (manager != null) {
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
        signedTransfer.verifySignature(channelState.getChannel().getClientAddress()::equals);
        channelState.registerTransfer(signedTransfer);
    }

    public boolean requestCloseChannel(Address address) {
        return registry.get(address).map(OutgoingChannelState::requestClose).orElse(false);
    }
}
