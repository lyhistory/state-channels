package papyrus.channel.node.server.outgoing;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.entity.ChannelBlockchainProperties;
import papyrus.channel.node.server.EthereumService;

@Component
public class OutgoingChannelPoolManager {
    private Map<Address, OutgoingChannelPool> channelPools = new ConcurrentHashMap<>();
    
    private EthereumService service;
    private final ScheduledExecutorService executorService;

    public OutgoingChannelPoolManager(EthereumService service, ScheduledExecutorService executorService) {
        this.service = service;
        this.executorService = executorService;
        //TODO load channels from blockchain / storage ???
        executorService.scheduleWithFixedDelay(this::watch, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        for (OutgoingChannelPool pool : channelPools.values()) {
            pool.destroy();
        }
    }

    public List<OutgoingChannelState> getChannelsState(Address participantAddress) {
        OutgoingChannelPool pool = channelPools.get(participantAddress);
        if (pool == null) {
            return Collections.emptyList();
        }
        return pool.getChannelsState();
    }

    public void addParticipant(Address participantAddress, OutgoingChannelProperties config) {
        channelPools.compute(participantAddress, (addr, channelPool)->{
            if (channelPool == null) {
                channelPool = new OutgoingChannelPool(participantAddress, config, service, executorService);
                channelPool.start();
                return channelPool;
            }
            channelPool.setConfig(config);
            return channelPool;
        });
    }
    
    public void removeParticipant(Address participantAddress) {
        OutgoingChannelPool manager = channelPools.get(participantAddress);
        if (manager != null) {
            manager.setConfig(new OutgoingChannelProperties(0, new ChannelBlockchainProperties(0)));
        }
    }
    
    private void watch() {
        for (Address address : channelPools.keySet()) {
            channelPools.computeIfPresent(address, (a, mgr) -> {
                if (!mgr.isFinished()) {
                    return mgr;
                }
                mgr.destroy();
                return null;
            });
        }
    }
}
