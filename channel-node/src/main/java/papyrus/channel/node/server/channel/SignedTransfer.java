package papyrus.channel.node.server.channel;

import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;
import org.web3j.utils.Numeric;

import papyrus.channel.node.TransferMessage;
import papyrus.channel.node.server.ethereum.EthUtil;
import papyrus.channel.node.server.ethereum.SignedObject;

public class SignedTransfer extends SignedObject {
    private BigInteger transferId;
    private Address channelAddress;
    private BigInteger value;

    public SignedTransfer(TransferMessage transferMessage) {
        this(transferMessage.getTransferId(), transferMessage.getChannelAddress(), transferMessage.getValue());
        signature = Numeric.hexStringToByteArray(transferMessage.getSignature());
    }

    public SignedTransfer(String transferId, String channelAddress, String value) {
        this(Numeric.toBigInt(transferId), new Address(channelAddress), new BigInteger(value));
    }

    public SignedTransfer(BigInteger transferId, Address channelAddress, BigInteger value) {
        this.transferId = transferId;
        this.channelAddress = channelAddress;
        this.value = value;
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
    
    public TransferMessage toMessage() {
        return TransferMessage.newBuilder()
            .setChannelAddress(Numeric.toHexStringNoPrefix(channelAddress.getValue()))
            .setSignature(Numeric.toHexStringNoPrefix(signature))
            .setTransferId(Numeric.toHexStringNoPrefix(transferId))
            .setValue(value.toString())
            .build();
    }

    @Override
    protected byte[] hash() {
        return EthUtil.soliditySha3(transferId, channelAddress, value);
    }
}
