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
import papyrus.channel.node.server.ethereum.ContractsManager;
import papyrus.channel.node.server.ethereum.TokenService;
import papyrus.channel.node.server.peer.PeerConnectionManager;

@Component
public class OutgoingChannelManager {
    private final Map<Address, OutgoingChannelPool> channelPools = new ConcurrentHashMap<>();
    private final Map<Address, OutgoingChannelState> allChannelsByAddress = new ConcurrentHashMap<>();

    private final Address signerAddress;
    private final TokenService tokenService;
    private final ContractsManager contractsManager;
    private final ScheduledExecutorService executorService;
    private final PeerConnectionManager peerConnectionManager;
    private final EthereumConfig ethereumConfig;

    public OutgoingChannelManager(
        TokenService tokenService, 
        EthereumConfig ethereumConfig, 
        ContractsManager contractsManager, 
        ScheduledExecutorService executorService, 
        PeerConnectionManager peerConnectionManager
    ) {
        this.tokenService = tokenService;
        this.ethereumConfig = ethereumConfig;
        signerAddress = ethereumConfig.getSignerAddress();;
        this.contractsManager = contractsManager;
        this.executorService = executorService;
        this.peerConnectionManager = peerConnectionManager;
        syncChannels();
        executorService.scheduleWithFixedDelay(this::cleanup, 1, 1, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::syncChannels, 60, 60, TimeUnit.SECONDS);
    }

    private void syncChannels() {
//        channelManagerContract.getOutgoingChannels()
    }

    @PreDestroy
    public void destroy() {
        for (OutgoingChannelPool pool : channelPools.values()) {
            pool.destroy();
        }
    }

    public List<OutgoingChannelState> getChannels(Address participantAddress) {
        OutgoingChannelPool pool = channelPools.get(participantAddress);
        if (pool == null) {
            return Collections.emptyList();
        }
        return pool.getChannelsState();
    }

    public void addParticipant(Address participantAddress, OutgoingChannelProperties config) {
        channelPools.compute(participantAddress, (addr, channelPool)->{
            if (channelPool == null) {
                channelPool = new OutgoingChannelPool(
                    this, 
                    config,
                    tokenService,
                    contractsManager, 
                    peerConnectionManager, 
                    executorService, 
                    ethereumConfig, 
                    participantAddress
                );
                channelPool.start();
                return channelPool;
            }
            channelPool.setChannelProperties(config);
            return channelPool;
        });
    }
    
    public void removeParticipant(Address participantAddress) {
        OutgoingChannelPool manager = channelPools.get(participantAddress);
        if (manager != null) {
            manager.shutdown();
        }
    }
    
    private void cleanup() {
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

    public void registerTransfer(SignedTransfer signedTransfer) throws SignatureException {
        signedTransfer.verifySignature(signerAddress::equals);
        OutgoingChannelState channelState = allChannelsByAddress.get(signedTransfer.getChannelAddress());
        if (channelState == null) {
            throw new IllegalStateException("Unknown channel address: " + signedTransfer.getChannelAddress());
        }
        channelState.registerTransfer(signedTransfer);
    }

    void putChannel(OutgoingChannelState channel) {
        if (allChannelsByAddress.putIfAbsent(channel.getChannelAddress(), channel) != null) {
            throw new IllegalStateException("Duplicate channel: " + channel.getChannelAddress());
        }
    }
}
