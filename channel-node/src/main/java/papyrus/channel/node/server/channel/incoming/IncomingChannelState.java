package papyrus.channel.node.server.channel.incoming;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.server.channel.BlockchainChannel;
import papyrus.channel.node.server.channel.SignedChannelState;
import papyrus.channel.node.server.channel.SignedTransfer;

public class IncomingChannelState {
    private static final Logger log = LoggerFactory.getLogger(IncomingChannelState.class);
    
    private BlockchainChannel channel;
    private SignedChannelState ownState;
    private SignedChannelState receiverState;
    //TODO implement merkle tree
    private Map<BigInteger, SignedTransfer> transfers = new HashMap<>(); 

    public IncomingChannelState(BlockchainChannel channel) {
        this.channel = channel;
        ownState = new SignedChannelState(channel.getChannelAddress());
    }

    public BlockchainChannel getChannel() {
        return channel;
    }

    public SignedChannelState getOwnState() {
        return ownState;
    }

    public SignedChannelState getReceiverState() {
        return receiverState;
    }

    public synchronized void updateReceiverState(SignedChannelState receiverState) {
        if (this.receiverState != null && this.receiverState.getNonce() > receiverState.getNonce()) {
            log.debug("Ignoring old state update for channel {}", channel.getChannelAddress());
        }
        this.receiverState = receiverState;
    }
    
    public synchronized boolean registerTransfer(SignedTransfer transfer) throws SignatureException {
        transfer.verifySignature(channel.getsignerAddress()::equals);
        boolean registered = transfers.putIfAbsent(transfer.getTransferId(), transfer) == null;
        if (registered) {
            ownState.setCompletedTransfers(ownState.getCompletedTransfers().add(transfer.getValue()));
        }
        return registered;
    } 

    public Address getChannelAddress() {
        return channel.getChannelAddress();
    }
}
