package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.generated.Uint256;

import com.google.common.base.Preconditions;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.SignedTransferUnlock;
import papyrus.channel.node.server.ethereum.TokenService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class OutgoingChannelState {
    private static final Logger log = LoggerFactory.getLogger(OutgoingChannelState.class);
    
    private Status status;
    private BlockchainChannel channel;
    private BigInteger transferedAmount = BigInteger.ZERO;
    private Map<BigInteger, SignedTransfer> transfers = new HashMap<>();
    private Map<BigInteger, SignedTransfer> lockedTransfers = new HashMap<>();
    private long currentNonce;
    private long syncedNonce;
    private volatile boolean closeRequested;

    private StateTransition transition;

    public OutgoingChannelState(Address senderAddress, Address clientAddress, Address receiverAddress, ChannelProperties properties) {
        channel = new BlockchainChannel(senderAddress, clientAddress, receiverAddress);
        channel.setProperties(properties);
        setStatus(Status.NEW);
    }

    public boolean isClosed() {
        return closeRequested || channel != null && channel.getClosed() > 0 || getPendingStatus() == Status.CLOSED;
    }

    public boolean isActive() {
        return !closeRequested && status == Status.ACTIVE && getPendingStatus() == Status.ACTIVE;
    }

    public boolean isSettled() {
        return channel.getSettled() > 0;
    }

    BlockchainChannel getChannel() {
        return channel;
    }

    public Address getChannelAddress() {
        return channel.getChannelAddress();
    }

    public void startDeploying(CompletableFuture<ChannelContract> deployingContract) {
        checkStatus(Status.NEW);
        checkNotNull(deployingContract);
        startTransition(Status.CREATED, deployingContract, contract -> channel.linkNewContract(contract));
    }

    private void checkStatus(Status... expected) {
        checkState(status.isAnyOf(expected),"Invalid status: %s, expected: %s", status, expected.length == 1 ? expected[0] : Arrays.asList(expected));
    }

    public void setActive() {
        checkStatus(Status.OPENED);
        setStatus(Status.ACTIVE);
    }

    private void setStatus(Status newStatus) {
        status = newStatus;
    }

    public Status getStatus() {
        return status;
    }

    public Status getPendingStatus() {
        StateTransition tr = this.transition;
        return tr != null ? tr.nextStatus : status;
    }
    
    public boolean checkTransitionInProgress() {
        if (transition != null) transition.checkAndApply();
        return transition != null;
    }

    public BigInteger getTransferedAmount() {
        return transferedAmount;
    }

    public long getOpenBlock() {
        return channel != null ? channel.getCreated() : 0;
    }
    
    public BigInteger getTransferredAmount() {
        return channel != null ? channel.getCompletedTransfers() : BigInteger.ZERO; 
    }
    
    String getAddressSafe() {
        if (channel.getChannelAddress() != null) return channel.getChannelAddress().toString();
        return status.toString();
    }

    public Map<BigInteger, SignedTransfer> getTransfers() {
        return Collections.unmodifiableMap(transfers);
    }

    public Map<BigInteger, SignedTransfer> getLockedTransfers() {
        return Collections.unmodifiableMap(lockedTransfers);
    }

    public synchronized boolean registerTransfer(SignedTransfer transfer) {
        Preconditions.checkArgument(transfer.getChannelAddress().equals(getChannelAddress()));
        Preconditions.checkArgument(transfer.getValue().signum() > 0);

        BigInteger transferId = transfer.getTransferId();
        if (lockedTransfers.containsKey(transferId) || transfers.containsKey(transferId)) {
            return false;
        }
        if (transfer.isLocked()) {
            lockedTransfers.put(transferId, transfer);
        } else {
            transfers.put(transferId, transfer);
            transferedAmount = transferedAmount.add(transfer.getValue());
        }
        stateChanged();
        return true;
    }

    public synchronized boolean unlockTransfer(SignedTransferUnlock transfer) {
        BigInteger transferId = transfer.getTransferId();
        SignedTransfer existing = lockedTransfers.remove(transferId);
        if (existing == null) {
            return false;
        }
        transfers.put(transferId, existing);
        transferedAmount = transferedAmount.add(existing.getValue());
        stateChanged();
        return true;
    }

    private void stateChanged() {
        if (currentNonce == syncedNonce) {
            currentNonce ++;
        }
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
        syncedNonce = state.getNonce();
    }

    public BigInteger getBalance() {
        return channel.getBalance();
    }

    public void approveDeposit(TokenService token, BigInteger value) {
        checkStatus(Status.CREATED);
        startTransition(
            Status.DEPOSIT_APPROVED,
            token.approve(channel.getChannelAddress(), value)
        );
    }

    public void deposit(BigInteger value) {
        checkStatus(Status.DEPOSIT_APPROVED);
        startTransition(
            Status.OPENED, 
            channel.getContract().deposit(new Uint256(value)),
            tr -> channel.setBalance(channel.getBalance().add(value))
        );
    }
    
    public boolean requestClose() {
        if (!status.isAnyOf(Status.SETTLED, Status.DISPUTING)) {
            this.closeRequested = true;
            return true;
        }
        return false;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    void setCloseRequested(boolean closeRequested) {
        this.closeRequested = closeRequested;
    }

    public void makeDisposable() {
        Preconditions.checkState(status.isAnyOf(Status.NEW, Status.SETTLED));
        this.status = Status.DISPOSABLE;
    }

    public void doClose() {
        checkStatus(Status.CREATED, Status.ACTIVE);
        startTransition(Status.CLOSED, 
            channel.getContract().close(
                new Uint256(currentNonce),
                new Uint256(transferedAmount),
                new DynamicBytes(new byte[0])
            ),
            tr -> channel.setClosed(tr.getBlockNumber().longValueExact())
        );
    }
    
    
    public void settleIfPossible(long currentBlockNumber) {
        Preconditions.checkState(channel.getClosed() > 0);
        Preconditions.checkState(channel.getSettled() == 0);
        checkStatus(Status.CLOSED);
        long blocksLeft = channel.getClosed() + channel.getProperties().getSettleTimeout() - currentBlockNumber;  
        if (blocksLeft <= 0) {
            startTransition(
                Status.SETTLED, 
                channel.getContract().settle(),
                tr -> {
                    channel.setSettled(tr.getBlockNumber().longValueExact());
                    log.debug("Channel {} settled. Completed transfers: {}", getAddressSafe(), getChannel().getCompletedTransfers());
                }
            );
        } else {
            log.debug("Waiting settle timeout for channel {} blocks left: {}", getAddressSafe(), blocksLeft);
        }
    }

    public enum Status {
        /** Fresh new object */
        NEW,
        /** Created in blockchain */
        CREATED,
        /** Deposit approved */
        DEPOSIT_APPROVED,
        /** Active (deposit added) */
        OPENED, 
        /** Active (deposit added) */
        ACTIVE, 
        /** Closed in blockchain. */
        CLOSED, 
        /** Settled */
        SETTLED, 
        /** Dispute process initiated */
        DISPUTING,
        /** All operations completed, may forget about channel */
        DISPOSABLE,
        ;
        
        public boolean isAnyOf(Status ... statuses) {
            for (Status status : statuses) 
                if (status == this) return true;
            return false;
        }
    }
    
    private abstract class StateTransition {
        Status nextStatus;

        public StateTransition(Status nextStatus) {
            this.nextStatus = nextStatus;
        }

        abstract void checkAndApply();
    }

    private <T> void startTransition(Status nextStatus, Future<T> task) {
        startTransition(nextStatus, task, t -> {});
    }
    
    private <T> void startTransition(Status nextStatus, Future<T> task, Consumer<T> applyResult) {
        Status prevStatus = status;
        log.warn("Channel {} starting transition {}->{}", getAddressSafe(), prevStatus, nextStatus);
        Preconditions.checkState(transition == null);
        transition = new StateTransition(nextStatus) {
            @Override
            void checkAndApply() {
                if (task.isDone()) {
                    transition = null;
                    try {
                        applyResult.accept(task.get());
                        status = nextStatus;
                        log.warn("Channel {} transition {}->{} SUCCESS", getAddressSafe(), prevStatus, nextStatus);
                    } catch (Exception e) {
                        log.warn("Channel {} transition {}->{} FAILED", getAddressSafe(), prevStatus, nextStatus, e);
                    }
                }
            }
        }; 
    }
}
