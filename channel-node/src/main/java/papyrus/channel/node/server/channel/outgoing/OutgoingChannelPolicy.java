package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import papyrus.channel.node.entity.DataObject;

public class OutgoingChannelPolicy extends DataObject {
    public static final OutgoingChannelPolicy NONE = new OutgoingChannelPolicy(BigInteger.ZERO);
    
    private final BigInteger deposit;

    public OutgoingChannelPolicy(BigInteger deposit) {
        this.deposit = deposit;
    }

    public BigInteger getDeposit() {
        return deposit;
    }
    
    public boolean isNone() {
        return deposit.signum() == 0;
    }
}
