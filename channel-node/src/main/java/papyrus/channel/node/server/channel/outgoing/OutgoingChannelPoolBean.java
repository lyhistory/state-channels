package papyrus.channel.node.server.channel.outgoing;

import java.math.BigDecimal;

import org.web3j.abi.datatypes.Address;

import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;

import papyrus.channel.node.server.ethereum.TokenConvert;
import papyrus.channel.node.server.persistence.Keyspaces;

@Table(keyspace = Keyspaces.OUTGOING, name = "channel_pool")
public class OutgoingChannelPoolBean {
    @PartitionKey
    private Address sender;
    @ClusteringColumn
    private Address receiver;
    
    private Address auditor;
    @Column(name = "min_active")
    private int minActive;
    @Column(name = "max_active")
    private int maxActive;
    private BigDecimal deposit;
    @Column(name = "close_timeout")
    private long closeTimeout;
    @Column(name = "settle_timeout")
    private long settleTimeout;
    private boolean shutdown;

    public OutgoingChannelPoolBean() {
    }

    public OutgoingChannelPoolBean(Address sender, Address receiver, ChannelPoolProperties config) {
        this.sender = sender;
        this.receiver = receiver;
        this.auditor = config.getBlockchainProperties().getAuditor().orElse(null);
        this.minActive = config.getMinActiveChannels();
        this.maxActive = config.getMaxActiveChannels();
        this.deposit = TokenConvert.fromWei(config.getDeposit());
        this.closeTimeout = config.getBlockchainProperties().getCloseTimeout();
        this.settleTimeout = config.getBlockchainProperties().getSettleTimeout();
    }

    public Address getSender() {
        return sender;
    }

    public void setSender(Address sender) {
        this.sender = sender;
    }

    public Address getReceiver() {
        return receiver;
    }

    public void setReceiver(Address receiver) {
        this.receiver = receiver;
    }

    public Address getAuditor() {
        return auditor;
    }

    public void setAuditor(Address auditor) {
        this.auditor = auditor;
    }

    public int getMinActive() {
        return minActive;
    }

    public void setMinActive(int minActive) {
        this.minActive = minActive;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public long getCloseTimeout() {
        return closeTimeout;
    }

    public void setCloseTimeout(long closeTimeout) {
        this.closeTimeout = closeTimeout;
    }

    public long getSettleTimeout() {
        return settleTimeout;
    }

    public void setSettleTimeout(long settleTimeout) {
        this.settleTimeout = settleTimeout;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }
}
