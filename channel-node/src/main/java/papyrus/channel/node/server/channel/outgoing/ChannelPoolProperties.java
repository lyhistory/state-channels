package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import org.web3j.utils.Convert;

import papyrus.channel.node.ChannelPoolMessage;
import papyrus.channel.node.entity.ChannelProperties;
import papyrus.channel.node.entity.DataObject;
import papyrus.channel.node.server.ethereum.TokenConvert;

public class ChannelPoolProperties extends DataObject {
    
    private final int minActiveChannels;
    private final int maxActiveChannels;
    private final BigInteger deposit;
    private final ChannelProperties blockchainProperties;

    public ChannelPoolProperties(ChannelPoolMessage request) {
        this(
            request.getMinActiveChannels(), 
            request.getMaxActiveChannels(),
            Convert.toWei(request.getDeposit(), Convert.Unit.ETHER).toBigIntegerExact(), 
            new ChannelProperties(request.getProperties())
        );
    }
    
    public ChannelPoolProperties(OutgoingChannelPoolBean bean) {
        this(
            bean.getMinActive(), 
            bean.getMaxActive(), 
            TokenConvert.toWei(bean.getDeposit()), 
            new ChannelProperties(bean)
        );
    }
    
    public ChannelPoolProperties(
        int minActiveChannels, 
        int maxActiveChannels, 
        BigInteger deposit, 
        ChannelProperties blockchainProperties
    ) {
        this.minActiveChannels = minActiveChannels;
        this.maxActiveChannels = maxActiveChannels;
        this.deposit = deposit;
        this.blockchainProperties = blockchainProperties;
    }

    public int getMinActiveChannels() {
        return minActiveChannels;
    }

    public int getMaxActiveChannels() {
        return maxActiveChannels;
    }

    public BigInteger getDeposit() {
        return deposit;
    }

    public ChannelProperties getBlockchainProperties() {
        return blockchainProperties;
    }
}
