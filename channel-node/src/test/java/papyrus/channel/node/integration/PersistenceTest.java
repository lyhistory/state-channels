package papyrus.channel.node.integration;

import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;

import papyrus.channel.ChannelPropertiesMessage;
import papyrus.channel.node.ChannelPoolMessage;
import papyrus.channel.node.ChannelStatusMessage;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.GetChannelPoolsRequest;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.incoming.IncomingChannelState;
import papyrus.channel.node.server.ethereum.TokenConvert;

public class PersistenceTest extends BaseChannelTest {
    @Test
    public void test() throws ExecutionException, InterruptedException, SignatureException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        Credentials auditorCredentials = Credentials.create(Keys.createEcKeyPair());

        String auditorAddress = auditorCredentials.getAddress();

        BigDecimal deposit = new BigDecimal("0.01");

        IncomingChannelState channelState = createPool(ChannelPoolMessage.newBuilder()
            .setDeposit(deposit.toString())
            .setCloseBlocksCount(200)
            .setProperties(
                ChannelPropertiesMessage.newBuilder()
                    .setAuditorAddress(auditorAddress)
                    .build())
            .build()
        );

        BigDecimal transferSum = new BigDecimal("0.0001");

        SignedTransfer transfer = sendTransfer("1", channelState, transferSum);

        BigDecimal transferSum2 = new BigDecimal("0.0002");

        SignedTransfer transferLocked = sendTransferLocked("2", channelState, transferSum2);

        Util.assertEquals(transferSum, TokenConvert.fromWei(channelState.getSenderState().getCompletedTransfers()));

        unlockTransfer("2", channelState, auditorCredentials, transferSum2);

        //restart server
        stopContext(sender);
        initSender();

        List<ChannelPoolMessage> poolList = senderClient.getClientAdmin().getChannelPools(
            GetChannelPoolsRequest.newBuilder()
                .setSenderAddress(senderAddress.toString())
                .build()
        ).getPoolList();

        Assert.assertEquals(1, poolList.size());

        List<ChannelStatusMessage> channelList = senderClient.getOutgoingChannelClient().getChannels(
            ChannelStatusRequest.newBuilder()
                .setSenderAddress(senderAddress.toString())
                .setReceiverAddress(receiverAddress.toString())
                .build()
        ).getChannelList();

        Assert.assertEquals(1, channelList.size());
    }

    @Override
    protected void configureContext(SpringApplicationBuilder builder) {
        builder.properties(Collections.singletonMap("eth.sync", "true"));
    }
}
