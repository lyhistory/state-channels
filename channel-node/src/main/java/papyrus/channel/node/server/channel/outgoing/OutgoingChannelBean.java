package papyrus.channel.node.server.channel.outgoing;

import java.math.BigDecimal;

import org.web3j.abi.datatypes.Address;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;

import papyrus.channel.node.server.persistence.Keyspaces;

@Table(keyspace = Keyspaces.OUTGOING, name = "channel")
public class OutgoingChannelBean {

    @PartitionKey
    private Address address;

    private OutgoingChannelState.Status status;
    
    private BigDecimal transferred;

    @Column(name = "current_nonce")
    private int currentNonce;

    @Column(name = "synced_nonce")
    private int syncedNonce;

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public OutgoingChannelState.Status getStatus() {
        return status;
    }

    public void setStatus(OutgoingChannelState.Status status) {
        this.status = status;
    }

    public BigDecimal getTransferred() {
        return transferred;
    }

    public void setTransferred(BigDecimal transferred) {
        this.transferred = transferred;
    }

    public int getCurrentNonce() {
        return currentNonce;
    }

    public void setCurrentNonce(int currentNonce) {
        this.currentNonce = currentNonce;
    }

    public int getSyncedNonce() {
        return syncedNonce;
    }

    public void setSyncedNonce(int syncedNonce) {
        this.syncedNonce = syncedNonce;
    }
}
