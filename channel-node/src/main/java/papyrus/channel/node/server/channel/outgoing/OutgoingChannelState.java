package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.entity.BlockchainChannelProperties;
import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.ethereum.TokenService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class OutgoingChannelState {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelState.class);
    
    private Status status;
    private BlockchainChannel channel;
    private BigInteger transferedAmount = BigInteger.ZERO;
    private Map<BigInteger, SignedTransfer> transfers = new HashMap<>();
    private long currentNonce;
    private long syncedNonce;
    CompletableFuture<ChannelContract> deployingContract;
    private Future<TransactionReceipt> depositFuture;
    private BigInteger depositPending;

    public OutgoingChannelState(Address senderAddress, Address signerAddress, Address receiverAddress, BlockchainChannelProperties properties) {
        channel = new BlockchainChannel(senderAddress, signerAddress, receiverAddress);
        channel.setProperties(properties);
        setStatus(Status.NEW);
    }

    public boolean isClosed() {
        return channel.getCreated() > 0;
    }

    public boolean isSettled() {
        return channel.getSettled() > 0;
    }

    public Address getChannelAddress() {
        return channel.getChannelAddress();
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
        channel.linkNewContract(contract);
        
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

    public BigInteger getTransferedAmount() {
        return transferedAmount;
    }

    String getAddressSafe() {
        if (channel.getChannelAddress() != null) return channel.getChannelAddress().toString();
        return status.toString();
    }

    public Optional<ChannelContract> checkIfDeployed() {
        checkStatus(Status.CREATING);
        Preconditions.checkNotNull(deployingContract);
        return Optional.ofNullable(deployingContract.getNow(null));
    }

    public synchronized boolean registerTransfer(SignedTransfer transfer) {
        Preconditions.checkArgument(transfer.getChannelAddress().equals(getChannelAddress()));
        Preconditions.checkArgument(transfer.getValue().signum() > 0);
        
        if (transfers.putIfAbsent(transfer.getTransferId(), transfer) == null) {
            transferedAmount = transferedAmount.add(transfer.getValue());
            if (currentNonce == syncedNonce) {
                currentNonce ++;
            }
            return true;
        }
        return false;
    }

    public synchronized boolean isNeedsSync() {
        return currentNonce > syncedNonce;
    }

    public synchronized SignedChannelState createState() {
        SignedChannelState state = new SignedChannelState(channel.getChannelAddress());
        state.setNonce(currentNonce);
        state.setCompletedTransfers(transferedAmount);
        return state;
    }

    public synchronized void syncCompleted(SignedChannelState state) {
        syncedNonce = currentNonce;
    }

    public BigInteger getBalance() {
        return channel.getBalance();
    }

    public void deposit(TokenService token, BigInteger value) {
        checkStatus(Status.CREATED);
        Preconditions.checkState(depositFuture == null);
        if (value.signum() > 0) {
            depositFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    token.approve(channel.getChannelAddress(), value);
                    //TODO rollback approval in case contract was not created
                    return channel.getContract().deposit(new Uint256(value)).get();
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            });
            depositPending = value;
        } else {
            status = Status.ACTIVE;
        }
    }
    
    public boolean isDepositDeploying() {
        return depositFuture != null;
    }

    public void checkDepositCompleted() {
        checkStatus(Status.CREATED);
        if (depositFuture.isDone()) {
            try {
                depositFuture.get();
            } catch (Exception e) {
                depositFuture = null;
                depositPending = null;
                throw Throwables.propagate(e);
            }
            channel.setBalance(channel.getBalance().add(depositPending));
            depositFuture = null;
            depositPending = null;                            
            status = Status.ACTIVE;
        }
    }

    public enum Status {
        /** Fresh new object */
        NEW,
        /** Requested contract creation waiting for transaction receipt */
        CREATING,
        /** Created in blockchain */
        CREATED,
        /** Active (deposit added) */
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
