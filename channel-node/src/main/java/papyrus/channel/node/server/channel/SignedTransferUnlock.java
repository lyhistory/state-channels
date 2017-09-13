package papyrus.channel.node.server.channel;

import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;
import org.web3j.utils.Numeric;

import papyrus.channel.node.UnlockTransferMessage;
import papyrus.channel.node.server.ethereum.CryptoUtil;
import papyrus.channel.node.server.ethereum.SignedObject;

public class SignedTransferUnlock extends SignedObject {
    private BigInteger transferId;
    private Address channelAddress;

    public SignedTransferUnlock(UnlockTransferMessage unlockTransferMessage) {
        this(unlockTransferMessage.getTransferId(), unlockTransferMessage.getChannelAddress());
        signature = Numeric.hexStringToByteArray(unlockTransferMessage.getSignature());
    }

    public SignedTransferUnlock(String transferId, String channelAddress) {
        this(Numeric.toBigInt(transferId), new Address(channelAddress));
    }

    public SignedTransferUnlock(BigInteger transferId, Address channelAddress) {
        this.transferId = transferId;
        this.channelAddress = channelAddress;
    }

    public BigInteger getTransferId() {
        return transferId;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }

    public UnlockTransferMessage toMessage() {
        return UnlockTransferMessage.newBuilder()
            .setChannelAddress(Numeric.toHexStringNoPrefix(channelAddress.getValue()))
            .setSignature(Numeric.toHexStringNoPrefix(signature))
            .setTransferId(Numeric.toHexStringNoPrefix(transferId))
            .build();
    }

    @Override
    public byte[] hash() {
        return CryptoUtil.soliditySha3(transferId, channelAddress);
    }
}
