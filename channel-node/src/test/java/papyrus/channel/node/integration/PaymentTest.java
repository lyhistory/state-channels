package papyrus.channel.node.integration;

import java.math.BigDecimal;
import java.security.SignatureException;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;

import papyrus.channel.ChannelPropertiesMessage;
import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.RegisterTransfersRequest;
import papyrus.channel.node.UnlockTransferRequest;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.SignedTransferUnlock;
import papyrus.channel.node.server.channel.incoming.IncomingChannelState;
import papyrus.channel.node.server.ethereum.TokenConvert;

public class PaymentTest extends BaseChannelTest {
    @Test
    public void testChannel() throws InterruptedException, ExecutionException, SignatureException {
        //add participant - this will initiate channels opening

        BigDecimal deposit = new BigDecimal("0.01");

        IncomingChannelState channelState = openChannel(AddChannelPoolRequest.newBuilder()
            .setDeposit(deposit.toString())
            .build()
        );

        BigDecimal transferSum = new BigDecimal("0.0001");

        SignedTransfer transfer = sendTransfer("1", channelState, transferSum);

        //check for double sending
        Util.assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        Thread.sleep(100);

        Util.assertEquals(transferSum, TokenConvert.fromWei(channelState.getSenderState().getCompletedTransfers()));

        BigDecimal transferSum2 = new BigDecimal("0.0002");

        SignedTransfer transfer2 = sendTransfer("2", channelState, transferSum2);

        Util.assertNoError(receiverClient.getIncomingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .addTransfer(transfer2.toMessage())
            .build()
        ).getError());

        Util.waitFor(() -> channelState.getOwnState().getCompletedTransfers().signum() > 0);

        closeAndSettleChannel(channelState, transferSum.add(transferSum2));
    }

    @Test
    public void testLockedTransfers() throws Exception {
        Credentials auditorCredentials = Credentials.create(Keys.createEcKeyPair());
        String auditorAddress = auditorCredentials.getAddress();

        BigDecimal deposit = new BigDecimal("0.01");

        IncomingChannelState channelState = openChannel(AddChannelPoolRequest.newBuilder()
            .setDeposit(deposit.toString())
            .setProperties(
                ChannelPropertiesMessage.newBuilder()
                    .setAuditorAddress(auditorAddress)
                    .build())
            .build()
        );

        BigDecimal transferSum = new BigDecimal("0.0003");

        SignedTransfer transfer = sendTransferLocked("1", channelState, transferSum);

        SignedTransferUnlock transferUnlock = unlockTransfer("1", channelState, auditorCredentials, transferSum);

        Util.assertNoError(receiverClient.getIncomingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());
        Util.assertNoError(receiverClient.getIncomingChannelClient().unlockTransfer(UnlockTransferRequest.newBuilder()
            .addUnlock(transferUnlock.toMessage())
            .build()
        ).getError());

        Util.waitFor(() -> channelState.getOwnState().getCompletedTransfers().signum() > 0);

        closeAndSettleChannel(channelState, transferSum);
    }
}
