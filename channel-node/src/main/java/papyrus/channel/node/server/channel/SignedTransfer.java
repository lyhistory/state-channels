package papyrus.channel.node.server.channel;

import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;
import org.web3j.utils.Numeric;

import papyrus.channel.node.MessageLock;
import papyrus.channel.node.TransferMessage;
import papyrus.channel.node.server.ethereum.CryptoUtil;
import papyrus.channel.node.server.ethereum.SignedObject;

public class SignedTransfer extends SignedObject {
    private BigInteger transferId;
    private Address channelAddress;
    private BigInteger value;
    private boolean locked;

    public SignedTransfer(TransferMessage transferMessage) {
        this(transferMessage.getTransferId(), transferMessage.getChannelAddress(), transferMessage.getValue(), transferMessage.getLock() == MessageLock.AUDITOR);
        signature = Numeric.hexStringToByteArray(transferMessage.getSignature());
    }

    public SignedTransfer(String transferId, String channelAddress, String value, boolean locked) {
        this(Numeric.toBigInt(transferId), new Address(channelAddress), new BigInteger(value), locked);
    }

    public SignedTransfer(BigInteger transferId, Address channelAddress, BigInteger value, boolean locked) {
        this.transferId = transferId;
        this.channelAddress = channelAddress;
        this.value = value;
        this.locked = locked;
    }

    public BigInteger getTransferId() {
        return transferId;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }

    public BigInteger getValue() {
        return value;
    }

    public boolean isLocked() {
        return locked;
    }

    public TransferMessage toMessage() {
        return TransferMessage.newBuilder()
            .setChannelAddress(Numeric.toHexStringNoPrefix(channelAddress.getValue()))
            .setSignature(Numeric.toHexStringNoPrefix(signature))
            .setTransferId(Numeric.toHexStringNoPrefix(transferId))
            .setValue(value.toString())
            .setLock(locked ? MessageLock.AUDITOR : MessageLock.NONE)
            .build();
    }

    @Override
    public byte[] hash() {
        return CryptoUtil.soliditySha3(transferId, channelAddress, value, locked ? BigInteger.ONE : BigInteger.ZERO);
    }
}
