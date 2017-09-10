package papyrus.channel.node.server.channel;

import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;

import papyrus.channel.node.server.ethereum.CryptoUtil;
import papyrus.channel.node.server.ethereum.SignedObject;
import papyrus.channel.protocol.ChannelStateMessage;

public class SignedChannelState extends SignedObject {
    private long nonce;
    private Address channelAddress;
    private BigInteger completedTransfers = BigInteger.ZERO;

    public SignedChannelState(Address channelAddress) {
        this.channelAddress = channelAddress;
    }

    public SignedChannelState(ChannelStateMessage message) {
        this.signature = message.getSignature().toByteArray();
        this.nonce = message.getNonce();
        this.channelAddress = new Address(message.getChannelAddress());
        this.completedTransfers = new BigInteger(message.getCompletedTransfers());
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public Address getChannelAddress() {
        return channelAddress;
    }

    public void setChannelAddress(Address channelAddress) {
        this.channelAddress = channelAddress;
    }

    public BigInteger getCompletedTransfers() {
        return completedTransfers;
    }

    public void setCompletedTransfers(BigInteger completedTransfers) {
        this.completedTransfers = completedTransfers;
    }
    
    public ChannelStateMessage toMessage() {
        Preconditions.checkState(signature != null);
        return ChannelStateMessage.newBuilder()
            .setNonce(nonce)
            .setChannelAddress(channelAddress.toString())
            .setCompletedTransfers(completedTransfers.toString())
            .setSignature(ByteString.copyFrom(signature))
            .build();
    }

    public byte[] hash() {
        return CryptoUtil.soliditySha3(channelAddress, new Uint256(nonce), completedTransfers);
    }
}
