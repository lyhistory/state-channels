package papyrus.channel.node.server.channel.outgoing;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.web3j.abi.datatypes.Address;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.Table;

import papyrus.channel.node.server.persistence.Keyspaces;

@Table(keyspace = Keyspaces.OUTGOING, name = "transfer")
public class OutgoingTransferBean {
    @Column(name = "channel_id")
    private Address channelId;
    @Column(name = "transfer_id")
    private BigInteger transferId;
    private BigDecimal value;
    private boolean locked;

    public Address getChannelId() {
        return channelId;
    }

    public void setChannelId(Address channelId) {
        this.channelId = channelId;
    }

    public BigInteger getTransferId() {
        return transferId;
    }

    public void setTransferId(BigInteger transferId) {
        this.transferId = transferId;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
