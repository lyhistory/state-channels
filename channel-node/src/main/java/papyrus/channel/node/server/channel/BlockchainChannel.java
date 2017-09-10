package papyrus.channel.node.server.channel;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Uint;

import com.google.common.base.Preconditions;

import papyrus.channel.node.contract.ChannelContract;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.entity.ChannelProperties;

public class BlockchainChannel {
    private final Address senderAddress;
    private final Address clientAddress;
    private final Address receiverAddress;
    private BigInteger balance = BigInteger.ZERO;
    private ChannelProperties properties;
    private Address channelAddress;
    private Address closingAddress;
    private long created;
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

    private BlockchainChannel(Address managerAddress, ChannelContract contract, List<Type> st) {
        this.channelAddress = new Address(contract.getContractAddress());
        int i = 0;
//            uint settle_timeout,
        long timeout = ((Uint) st.get(i++)).getValue().longValueExact();
        Preconditions.checkState(timeout > 0);
        ChannelProperties properties = new ChannelProperties();
        properties.setSettleTimeout(timeout);
        this.properties = properties;
//            uint opened,
        created = ((Uint) st.get(i++)).getValue().longValueExact();
        Preconditions.checkState(created > 0);
//            uint closed,
        closed = ((Uint) st.get(i++)).getValue().longValueExact();
        Preconditions.checkState(closed >= 0);
//            uint settled,
        settled = ((Uint) st.get(i++)).getValue().longValueExact();
        Preconditions.checkState(settled >= 0);
//            address manager,
        Address manager_address = address(st.get(i++));
        Preconditions.checkArgument(managerAddress.equals(manager_address), "Wrong manager address: %s", manager_address);
//            address sender,
        senderAddress = address(st.get(i++));
        Preconditions.checkState(senderAddress != null);
//            address client,
        clientAddress = address(st.get(i++));
        Preconditions.checkState(clientAddress != null);
//            address receiver,
        receiverAddress = address(st.get(i++));
        Preconditions.checkState(receiverAddress != null);
//            uint256 balance,
        balance = ((Uint) st.get(i++)).getValue();
        Preconditions.checkState(balance.signum() >= 0);
//            uint256 sender_update.completed_transfers,
        nonce = ((Uint) st.get(i++)).getValue().longValueExact();
        Preconditions.checkState(nonce >= 0);
//            uint256 receiver_update.completed_transfers
        completedTransfers = ((Uint) st.get(i++)).getValue();
        Preconditions.checkState(completedTransfers.signum() >= 0);
        Preconditions.checkArgument(st.size() == i);
        this.contract = contract;
    }

    private Address address(Type address) {
        Address a = (Address) address;
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
            List<Type> st = contract.state().get();
            return new BlockchainChannel(new Address(managerContract.getContractAddress()), contract, st);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Address getclientAddress() {
        return clientAddress;
    }
}
