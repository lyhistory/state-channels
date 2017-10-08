package papyrus.channel.node.server.channel;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import com.google.common.base.Preconditions;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.entity.DataObject;

public class BlockchainChannel extends DataObject {
    private static final int TIMEOUT_MS = 10000;
    private final Address senderAddress;
    private final Address clientAddress;
    private final Address receiverAddress;
    private BigInteger balance = BigInteger.ZERO;
    private ChannelProperties properties;
    private Address channelAddress;
    private Address closingAddress;
    private long created;
    private long closeRequested;
    private long closed;
    private long settled;
    private long nonce;
    private BigInteger completedTransfers = BigInteger.ZERO;

    ChannelContract contract;

    public BlockchainChannel(Address senderAddress, Address clientAddress, Address receiverAddress) {
        Preconditions.checkNotNull(senderAddress);
        Preconditions.checkNotNull(clientAddress);
        Preconditions.checkNotNull(receiverAddress);
        
        this.senderAddress = senderAddress;
        this.clientAddress = clientAddress;
        this.receiverAddress = receiverAddress;
    }

    private BlockchainChannel(Address managerAddress, ChannelContract contract) throws ExecutionException, InterruptedException, TimeoutException {
        this.channelAddress = new Address(contract.getContractAddress());
        long settleTimeout = getLong(contract.settleTimeout());
        Preconditions.checkState(settleTimeout > 0);
        long closeTimeout = getLong(contract.closeTimeout());
        Preconditions.checkState(closeTimeout >= 0);
        long auditTimeout = getLong(contract.closeTimeout());
        Preconditions.checkState(auditTimeout >= 0);
        ChannelProperties properties = new ChannelProperties();
        properties.setSettleTimeout(settleTimeout);
        properties.setCloseTimeout(closeTimeout);
        properties.setAuditTimeout(auditTimeout);
        this.properties = properties;
        created = getLong(contract.opened());
        Preconditions.checkState(created > 0);
        closed = getLong(contract.closed());
        Preconditions.checkState(closed >= 0);
        closeRequested = getLong(contract.closeRequested());
        Preconditions.checkState(closeRequested >= 0);
        settled = getLong(contract.settled());
        Preconditions.checkState(settled >= 0);
        Address manager_address = getAddress(contract.manager());
        Preconditions.checkArgument(managerAddress.equals(manager_address), "Wrong manager address: %s", manager_address);
        senderAddress = getAddress(contract.sender());
        Preconditions.checkState(senderAddress != null);
        clientAddress = getAddress(contract.client());
        Preconditions.checkState(clientAddress != null);
        receiverAddress = getAddress(contract.receiver());
        Preconditions.checkState(receiverAddress != null);
        balance = contract.balance().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).getValue();
        Preconditions.checkState(balance.signum() >= 0);
        nonce = getLong(contract.nonce());
        Preconditions.checkState(nonce >= 0);
        completedTransfers = contract.completedTransfers().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).getValue();
        Preconditions.checkState(completedTransfers.signum() >= 0);
        Address auditorAddress = getAddress(contract.auditor());
        properties.setAuditor(auditorAddress);
        this.contract = contract;
    }

    private long getLong(Future<Uint256> future) throws InterruptedException, ExecutionException, TimeoutException {
        return getResult(future).getValue().longValueExact();
    }

    private Uint256 getResult(Future<Uint256> future) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private Address getAddress(Future<Address> address) throws InterruptedException, ExecutionException, TimeoutException {
        Address a = address.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return a != null && !a.getValue().equals(BigInteger.ZERO) ? a : null;
    }

    public Address getSenderAddress() {
        return senderAddress;
    }

    public Address getReceiverAddress() {
        return receiverAddress;
    }

    public Address getClientAddress() {
        return clientAddress;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }

    public void setChannelAddress(Address channelAddress) {
        this.channelAddress = channelAddress;
    }

    public ChannelProperties getProperties() {
        return properties;
    }

    public void setProperties(ChannelProperties properties) {
        this.properties = properties;
    }

    public long getCreated() {
        return created;
    }

    public long getCloseRequested() {
        return closeRequested;
    }

    public void setCloseRequested(long closeRequested) {
        this.closeRequested = closeRequested;
    }

    public long getClosed() {
        return closed;
    }

    public void setClosed(long closed) {
        this.closed = closed;
    }

    public long getSettled() {
        return settled;
    }

    public void setSettled(long settled) {
        this.settled = settled;
    }

    public ChannelContract getContract() {
        return contract;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }

    public Address getClosingAddress() {
        return closingAddress;
    }

    public void setClosingAddress(Address closingAddress) {
        this.closingAddress = closingAddress;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public BigInteger getCompletedTransfers() {
        return completedTransfers;
    }

    public void setCompletedTransfers(BigInteger completedTransfers) {
        this.completedTransfers = completedTransfers;
    }

    public void linkNewContract(ChannelContract contract) {
        Preconditions.checkArgument(contract.getTransactionReceipt().isPresent());
        
        this.contract = contract;
        this.channelAddress = new Address(contract.getContractAddress());
        this.created = contract.getTransactionReceipt().get().getBlockNumber().longValueExact();
    }
    
    public static BlockchainChannel fromExistingContract(ChannelManagerContract managerContract, ChannelContract contract) {
        try {
            return new BlockchainChannel(new Address(managerContract.getContractAddress()), contract);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
