package papyrus.channel.node.server.outgoing;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;

import com.google.common.base.Preconditions;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.entity.ChannelBlockchainProperties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class OutgoingChannel {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannel.class);
    
    private final ChannelBlockchainProperties properties;
    private final Address receiverAddress;
    private Status status;
    private Address channelAddress;
    private BigInteger transferedAmount;
    private long created;
    private long closed;
    private long settled;
    CompletableFuture<ChannelContract> deployingContract;
    ChannelContract contract;

    public OutgoingChannel(Address receiverAddress, ChannelBlockchainProperties properties) {
        this.receiverAddress = receiverAddress;
        this.properties = properties;
        setStatus(Status.NEW);
    }

    public boolean isClosed() {
        return created > 0;
    }

    public boolean isSettled() {
        return settled > 0;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }

    public void onDeploying(CompletableFuture<ChannelContract> deployingContract) {
        checkStatus(Status.NEW);
        checkNotNull(deployingContract);
        this.deployingContract = deployingContract;
        setStatus(Status.CREATING);
    }

    public void onDeployed(ChannelContract contract) {
        Preconditions.checkNotNull(contract);
        checkStatus(Status.CREATING);

        this.deployingContract = null;
        this.channelAddress = new Address(contract.getContractAddress());
        this.created = contract.getTransactionReceipt().get().getBlockNumber().longValueExact();
        this.contract = contract;
        
        setStatus(Status.CREATED);
    }

    private void checkStatus(Status expected) {
        checkState(status == expected,"Invalid status: %s, expected: %s", status, expected);
    }

    public void setActive() {
        checkStatus(Status.CREATED);
        setStatus(Status.ACTIVE);
    }

    private void setStatus(Status newStatus) {
        status = newStatus;
    }

    public Status getStatus() {
        return status;
    }

    public OutgoingChannelState toState() {
        Preconditions.checkState(channelAddress != null, "Invalid status: %s", status);
        return new OutgoingChannelState(channelAddress);
    }

    String getAddressSafe() {
        if (channelAddress != null) return channelAddress.toString();
        return status.toString();
    }

    public Optional<ChannelContract> checkIfDeployed() {
        checkStatus(Status.CREATING);
        Preconditions.checkNotNull(deployingContract);
        return Optional.ofNullable(deployingContract.getNow(null));
    }

    public enum Status {
        /** Fresh new object */
        NEW,
        /** Requested contract creation waiting for transaction receipt */
        CREATING,
        /** Created in blockchain */
        CREATED,
        /** Active (counterparty confirmed readiness) */
        ACTIVE, 
        /** Accept transactions from client, but not return as active */
        SUSPENDING, 
        /** Closed in blockchain. */
        CLOSED, 
        /** Settled */
        SETTLED, 
        /** Dispute process initiated */
        DISPUTING,
        ;
        
        public boolean isAnyOf(Status ... statuses) {
            for (Status status : statuses) 
                if (status == this) return true;
            return false;
        }
    }
}