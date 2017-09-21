package papyrus.channel.node.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.concurrent.ExecutionException;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Convert;

import com.google.common.base.Throwables;

import papyrus.channel.ChannelPropertiesMessage;
import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.ChannelNodeApplication;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.ChannelStatusResponse;
import papyrus.channel.node.RegisterTransfersRequest;
import papyrus.channel.node.RemoveChannelPoolRequest;
import papyrus.channel.node.UnlockTransferRequest;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.PapyrusToken;
import papyrus.channel.node.server.NodeServer;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.SignedTransferUnlock;
import papyrus.channel.node.server.channel.incoming.IncomingChannelManagers;
import papyrus.channel.node.server.channel.incoming.IncomingChannelState;
import papyrus.channel.node.server.channel.incoming.OutgoingChannelRegistry;
import papyrus.channel.node.server.channel.outgoing.OutgoingChannelState;
import papyrus.channel.node.server.ethereum.ContractsManagerFactory;
import papyrus.channel.node.server.peer.PeerConnection;

/**
 * Before test run:
 * <br>
 * <code>
 *     testrpc -b 1 --seed 5ee1d
 * </code>
 * And 
 * <code>
 *     truffle migrate --reset
 * </code>
 * the copy contract addresses to application-testrpc.yaml
 */
public class ChannelTest {

    private static final String PROFILES = "test,testrpc";

    private ConfigurableApplicationContext sender;
    private ConfigurableApplicationContext receiver;
    private PeerConnection senderClient;
    private PeerConnection receiverClient;
    private Credentials clientCredentials;
    private PapyrusToken token;
    private Credentials senderCredentials;
    private Credentials receiverCredentials;
    private Address senderAddress;
    private Address receiverAddress;
    private BigInteger senderStartBalance;
    private BigInteger receiverStartBalance;

    @Before
    public void init() throws IOException, CipherException, ExecutionException, InterruptedException {
        sender = createServerContext("sender");
        receiver = createServerContext("receiver");
        sender.start();
        receiver.start();

        senderClient = sender.getBean(PeerConnection.class);
        Assert.assertNotNull(senderClient);
        receiverClient = receiver.getBean(PeerConnection.class);
        Assert.assertNotNull(receiverClient);

        senderCredentials = sender.getBean(EthereumConfig.class).getMainCredentials();
        receiverCredentials = receiver.getBean(EthereumConfig.class).getMainCredentials();
        senderAddress = new Address(senderCredentials.getAddress());
        receiverAddress = new Address(receiverCredentials.getAddress());
        clientCredentials = Credentials.create(sender.getBean(EthProperties.class).getAccounts().values().iterator().next().getClientPrivateKey());
        
        Address clientAddress = sender.getBean(EthereumConfig.class).getClientAddress(senderAddress);
        Assert.assertThat(clientAddress.toString(), CoreMatchers.equalTo(clientCredentials.getAddress()));
        
        token = sender.getBean(ContractsManagerFactory.class).getMainContractManager().token();
        senderStartBalance = token.balanceOf(senderAddress).get().getValue();
        receiverStartBalance = token.balanceOf(receiverAddress).get().getValue();
    }

    private ConfigurableApplicationContext createServerContext(String profile) {
        return SpringApplication.run(ChannelNodeApplication.class, "--spring.profiles.active=" + PROFILES + "," + profile);
    }

    @After
    public void finish() throws IOException, CipherException {
        if (sender != null) try {
            sender.stop();
            sender.getBean(NodeServer.class).awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (receiver != null) {
            try {
                receiver.stop();
                receiver.getBean(NodeServer.class).awaitTermination();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
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

        Assert.assertEquals(transferSum, ethers(channelState.getSenderState().getCompletedTransfers()));

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

    private BigDecimal ethers(BigInteger completedTransfers) {
        return Convert.fromWei(new BigDecimal(completedTransfers), Convert.Unit.ETHER);
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

    private void closeAndSettleChannel(IncomingChannelState channelState, BigDecimal expectedTotalTransfer) throws InterruptedException, ExecutionException {
        Assert.assertEquals(expectedTotalTransfer, ethers(channelState.getOwnState().getCompletedTransfers()));
        Assert.assertTrue(channelState.getSenderState() != null);
        Assert.assertEquals(expectedTotalTransfer, ethers(channelState.getSenderState().getCompletedTransfers()));

        Util.assertNoError(
            senderClient.getClientAdmin().removeChannelPool(
                RemoveChannelPoolRequest.newBuilder()
                    .setSenderAddress(senderAddress.toString())
                    .setReceiverAddress(receiverAddress.toString())
                    .build()
            ).getError()
        );

        Util.waitFor(() -> {
            try {
                return channelState.getChannel().getContract().settled().get().getValue().signum() > 0;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        });

        //must be settled
        BigInteger expectedTotalTransferWei = Convert.toWei(expectedTotalTransfer, Convert.Unit.ETHER).toBigIntegerExact();
        Util.assertBalance(expectedTotalTransferWei, senderStartBalance.subtract(token.balanceOf(senderAddress).get().getValue()));
        Util.assertBalance(expectedTotalTransferWei, token.balanceOf(receiverAddress).get().getValue().subtract(receiverStartBalance));
    }

    private IncomingChannelState openChannel(AddChannelPoolRequest request) throws InterruptedException, ExecutionException {
        BigDecimal deposit = new BigDecimal(request.getDeposit());
        
        AddChannelPoolRequest.Builder builder = AddChannelPoolRequest.newBuilder();
        builder.setSenderAddress(senderAddress.toString());
        builder.setReceiverAddress(receiverAddress.toString());
        builder.setMinActiveChannels(1);
        builder.setMaxActiveChannels(1);
        ChannelPropertiesMessage.Builder propertiesBuilder = builder.getPropertiesBuilder();
        propertiesBuilder.setCloseTimeout(1);
        propertiesBuilder.setSettleTimeout(6);
        builder.mergeFrom(request);
        
        senderClient.getClientAdmin().addChannelPool(
            builder.build()
        );

        ChannelStatusResponse response = Util.waitFor(() -> 
            senderClient.getOutgoingChannelClient().getChannels(
                ChannelStatusRequest.newBuilder()
                    .setSenderAddress(senderAddress.toString())
                    .setReceiverAddress(receiverAddress.toString())
                    .build()
            ), 
            r -> !r.getChannelList().isEmpty()
        );

        response.getChannel(0).getChannelAddress();

        IncomingChannelState channelState = Util.waitFor(
            () -> {
                try {
                    IncomingChannelManagers managers = receiver.getBean(IncomingChannelManagers.class);
                    return managers.getManager(receiverAddress).getChannels(senderAddress);
                } catch (BeansException e) {
                    throw Throwables.propagate(e);
                }
            },
            ch -> ch.size() >= 1
        ).iterator().next();

        Assert.assertEquals(BigInteger.ZERO, channelState.getOwnState().getCompletedTransfers());

        Util.assertBalance(Convert.toWei(deposit, Convert.Unit.ETHER).toBigIntegerExact(), senderStartBalance.subtract(token.balanceOf(senderAddress).get().getValue()));
        return channelState;
    }

    private SignedTransfer sendTransfer(String transferId, IncomingChannelState channelState, BigDecimal sum) throws InterruptedException, SignatureException {
        BigInteger completedTransfers = channelState.getSenderState() != null ? channelState.getSenderState().getCompletedTransfers() : BigInteger.ZERO;
        SignedTransfer transfer = new SignedTransfer(transferId, channelState.getChannelAddress().toString(), sum.toString(), false);
        transfer.sign(clientCredentials.getEcKeyPair());
        Assert.assertEquals(new Address(clientCredentials.getAddress()), transfer.getSignerAddress());
        Assert.assertEquals(transfer, new SignedTransfer(transfer.toMessage()));

        Util.assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        Util.waitFor(() -> channelState.getSenderState() != null && channelState.getSenderState().getCompletedTransfers().compareTo(completedTransfers) > 0);

        Assert.assertEquals(completedTransfers.add(Convert.toWei(sum, Convert.Unit.ETHER).toBigIntegerExact()), channelState.getSenderState().getCompletedTransfers());
        return transfer;
    }

    private SignedTransfer sendTransferLocked(String transferId, IncomingChannelState channelState, BigDecimal sum) throws InterruptedException, SignatureException {
        SignedTransfer transfer = new SignedTransfer(transferId, channelState.getChannelAddress().toString(), sum.toString(), true);
        transfer.sign(clientCredentials.getEcKeyPair());
        Assert.assertEquals(new Address(clientCredentials.getAddress()), transfer.getSignerAddress());
        Assert.assertEquals(transfer, new SignedTransfer(transfer.toMessage()));

        Util.assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        OutgoingChannelState outgoingChannelState = sender.getBean(OutgoingChannelRegistry.class).get(channelState.getChannelAddress()).get();

        Util.waitFor(() -> !outgoingChannelState.getLockedTransfers().isEmpty());

        Assert.assertEquals(Convert.toWei(sum, Convert.Unit.ETHER).toBigIntegerExact(), outgoingChannelState.getLockedTransfers().get(new BigInteger(transferId)).getValue());
        return transfer;
    }

    private SignedTransferUnlock unlockTransfer(String transferId, IncomingChannelState channelState, Credentials auditorCredentials, BigDecimal sum) throws InterruptedException, SignatureException {
        BigInteger completedTransfers = channelState.getSenderState() != null ? channelState.getSenderState().getCompletedTransfers() : BigInteger.ZERO;
        SignedTransferUnlock transferUnlock = new SignedTransferUnlock(transferId, channelState.getChannelAddress().toString());
        transferUnlock.sign(auditorCredentials.getEcKeyPair());
        Assert.assertEquals(new Address(auditorCredentials.getAddress()), transferUnlock.getSignerAddress());
        Assert.assertEquals(transferUnlock, new SignedTransferUnlock(transferUnlock.toMessage()));

        Util.assertNoError(senderClient.getOutgoingChannelClient().unlockTransfer(UnlockTransferRequest.newBuilder()
            .addUnlock(transferUnlock.toMessage())
            .build()
        ).getError());

        Util.waitFor(() -> channelState.getSenderState() != null && channelState.getSenderState().getCompletedTransfers().compareTo(completedTransfers) > 0);

        Assert.assertEquals(completedTransfers.add(Convert.toWei(sum, Convert.Unit.ETHER).toBigIntegerExact()), channelState.getSenderState().getCompletedTransfers());
        return transferUnlock;
    }

}
