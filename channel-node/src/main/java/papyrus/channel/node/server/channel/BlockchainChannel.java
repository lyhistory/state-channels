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
import papyrus.channel.node.entity.BlockchainChannelProperties;

public class BlockchainChannel {
    private final Address senderAddress;
    private final Address signerAddress;
    private final Address receiverAddress;
    private BigInteger balance;
    private BlockchainChannelProperties properties;
    private Address channelAddress;
    private Address closingAddress;
    private long created;
    private long closed;
    private long settled;
    private BigInteger senderUpdateTransfers;
    private BigInteger receiverUpdateTransfers;

    ChannelContract contract;

    public BlockchainChannel(Address senderAddress, Address signerAddress, Address receiverAddress) {
        Preconditions.checkNotNull(senderAddress);
        Preconditions.checkNotNull(signerAddress);
        Preconditions.checkNotNull(receiverAddress);
        
        this.senderAddress = senderAddress;
        this.signerAddress = signerAddress;
        this.receiverAddress = receiverAddress;
    }

    private BlockchainChannel(Address managerAddress, ChannelContract contract, List<Type> st) {
        Preconditions.checkArgument(st.size() == 12);
//            uint settle_timeout,
        long timeout = ((Uint) st.get(0)).getValue().longValueExact();
        Preconditions.checkState(timeout > 0);
        properties = new BlockchainChannelProperties(timeout);
//            uint opened,
        created = ((Uint) st.get(1)).getValue().longValueExact();
        Preconditions.checkState(created > 0);
//            uint closed,
        closed = ((Uint) st.get(2)).getValue().longValueExact();
        Preconditions.checkState(closed >= 0);
//            uint settled,
        settled = ((Uint) st.get(3)).getValue().longValueExact();
        Preconditions.checkState(settled >= 0);
//            address closing_address,
        closingAddress = address(st.get(4));
//            address manager,
        Address manager_address = address(st.get(5));
        Preconditions.checkArgument(managerAddress.equals(manager_address), "Wrong manager address: %s", manager_address);
//            address sender,
        senderAddress = address(st.get(6));
        Preconditions.checkState(senderAddress != null);
//            address signer,
        signerAddress = address(st.get(7));
        Preconditions.checkState(signerAddress != null);
//            address receiver,
        receiverAddress = address(st.get(8));
        Preconditions.checkState(receiverAddress != null);
//            uint256 balance,
        balance = ((Uint) st.get(9)).getValue();
        Preconditions.checkState(balance.signum() >= 0);
//            uint256 sender_update.completed_transfers,
        senderUpdateTransfers = ((Uint) st.get(10)).getValue();
        Preconditions.checkState(senderUpdateTransfers.signum() >= 0);
//            uint256 receiver_update.completed_transfers
        receiverUpdateTransfers = ((Uint) st.get(11)).getValue();
        Preconditions.checkState(receiverUpdateTransfers.signum() >= 0);
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

    public Address getChannelAddress() {
        return channelAddress;
    }

    public void setChannelAddress(Address channelAddress) {
        this.channelAddress = channelAddress;
    }

    public BlockchainChannelProperties getProperties() {
        return properties;
    }

    public void setProperties(BlockchainChannelProperties properties) {
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

    public BigInteger getSenderUpdateTransfers() {
        return senderUpdateTransfers;
    }

    public void setSenderUpdateTransfers(BigInteger senderUpdateTransfers) {
        this.senderUpdateTransfers = senderUpdateTransfers;
    }

    public BigInteger getReceiverUpdateTransfers() {
        return receiverUpdateTransfers;
    }

    public void setReceiverUpdateTransfers(BigInteger receiverUpdateTransfers) {
        this.receiverUpdateTransfers = receiverUpdateTransfers;
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

    public Address getsignerAddress() {
        return signerAddress;
    }
}
