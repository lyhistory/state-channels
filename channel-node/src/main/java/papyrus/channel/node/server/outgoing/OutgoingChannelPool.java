package papyrus.channel.node.server.outgoing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.contract.LinkingManager;
import papyrus.channel.node.server.EthereumService;

import static papyrus.channel.node.server.outgoing.OutgoingChannel.Status.ACTIVE;
import static papyrus.channel.node.server.outgoing.OutgoingChannel.Status.CREATED;
import static papyrus.channel.node.server.outgoing.OutgoingChannel.Status.CREATING;

/**
 * Channel pool for single receiving address.
 */
public class OutgoingChannelPool {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelPool.class);
    
    private final ScheduledExecutorService executorService;
    private final LinkingManager manager;

    private ScheduledFuture<?> watchFuture;
    private Address receiverAddress;
    private volatile OutgoingChannelProperties config;
    private final List<OutgoingChannel> channels = Collections.synchronizedList(new ArrayList<>());
    
    public OutgoingChannelPool(Address receiverAddress, OutgoingChannelProperties config, EthereumService ethereumService, ScheduledExecutorService executorService) {
        this.receiverAddress = receiverAddress;
        this.config = config;
        manager = ethereumService.getManager();
        this.executorService = executorService;
    }

    public void setConfig(OutgoingChannelProperties config) {
        this.config = config;
    }

    public boolean isFinished() {
        return config.getActiveChannels() == 0 && channels.isEmpty();
    }

    private void cycle() {
        long activeOrOpening = channels.stream().filter(c -> c.getStatus().isAnyOf(ACTIVE, CREATED, CREATING)).count();
        if (activeOrOpening < config.getActiveChannels()) {
            OutgoingChannel channel = new OutgoingChannel(receiverAddress, config.getBlockchainProperties());
            channels.add(channel);
        }
        for (OutgoingChannel channel : channels) {
            if (!channel.isActionInProgress()) {
                OutgoingChannel.Status status = channel.getStatus();
                makeTransitions(channel);
                if (channel.getStatus() != status) {
                    log.info("Channel {} (from {} to {}) status updated from {} to {}", channel.getAddressSafe());
                }
            }
        }
    }

    private void makeTransitions(OutgoingChannel channel) {
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

    private void startCreating(OutgoingChannel channel) {
//        DeployingContract<ChannelContract> deployingContract = manager.startDeployment(ChannelContract.class);
//        channel.onDeploying(deployingContract);
    }

    private void checkIfCreated(OutgoingChannel channel) {
//        manager.checkIfDeployed(channel.deployingContract).ifPresent(channel::onDeployed);
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
            return channels.stream().filter(c -> c.getStatus() == ACTIVE).map(OutgoingChannel::toState).collect(Collectors.toList());
        }
    }
}
