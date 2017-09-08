package papyrus.channel.node.integration;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import org.web3j.utils.Convert;

import com.google.common.base.Throwables;

import papyrus.channel.ChannelPropertiesMessage;
import papyrus.channel.Error;
import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.ChannelNodeApplication;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.ChannelStatusResponse;
import papyrus.channel.node.RegisterTransfersRequest;
import papyrus.channel.node.RemoveChannelPoolRequest;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.PapyrusToken;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.incoming.IncomingChannelManagers;
import papyrus.channel.node.server.channel.incoming.IncomingChannelState;
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
 */
public class TestServer {

    private static final String PROFILES = "test,testrpc";
    public static final long MAX_WAIT = 60000L;

    private ConfigurableApplicationContext sender;
    private ConfigurableApplicationContext receiver;
    private PeerConnection senderClient;
    private PeerConnection receiverClient;
    private Credentials signerCredentials;
    private PapyrusToken token;
    private Credentials senderCredentials;
    private Credentials receiverCredentials;

    @Before
    public void init() throws IOException, CipherException {
        sender = createServerContext("sender");
        receiver = createServerContext("receiver");
        sender.start();
        receiver.start();
        senderClient = sender.getBean(PeerConnection.class);
        signerCredentials = Credentials.create(sender.getBean(EthProperties.class).getKeys().get(0).getSignerPrivateKey());
        senderCredentials = sender.getBean(EthereumConfig.class).getMainCredentials();
        receiverCredentials = receiver.getBean(EthereumConfig.class).getMainCredentials();
        Assert.assertNotNull(senderClient);
        receiverClient = receiver.getBean(PeerConnection.class);
        Assert.assertNotNull(receiverClient);
        token = sender.getBean(EthereumConfig.class).getMainContractManager().token();
    }

    private ConfigurableApplicationContext createServerContext(String profile) {
        return SpringApplication.run(ChannelNodeApplication.class, "--spring.profiles.active=" + PROFILES + "," + profile);
    }

    @After
    public void finish() throws IOException, CipherException {
        if (sender != null) try {
            sender.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (receiver != null) receiver.stop();
    }
    
    @Test
    public void testSendEther() throws InterruptedException, ExecutionException {
        //add participant - this will initiate channels opening
        Address senderAddress = new Address(senderCredentials.getAddress());
        Address receiverAddress = new Address(receiverCredentials.getAddress());

        BigInteger senderStartBalance = token.balanceOf(senderAddress).get().getValue();
        BigInteger receiverStartBalance = token.balanceOf(receiverAddress).get().getValue();

        BigInteger deposit = Convert.toWei("1", Convert.Unit.ETHER).toBigIntegerExact();
        senderClient.getClientAdmin().addChannelPool(
            AddChannelPoolRequest.newBuilder()
                .setSenderAddress(senderAddress.toString())
                .setReceiverAddress(receiverAddress.toString())
                .setDeposit(deposit.toString())
                .setMinActiveChannels(1)
                .setMaxActiveChannels(1)
                .setProperties(ChannelPropertiesMessage.newBuilder()
                    .setCloseTimeout(1)
                    .setSettleTimeout(6)
                    .build())
                .build()
        );

        ChannelStatusResponse response = waitFor(() -> 
            senderClient.getOutgoingChannelClient().getChannels(
                ChannelStatusRequest.newBuilder()
                    .setSenderAddress(senderAddress.toString())
                    .setReceiverAddress(receiverAddress.toString())
                    .build()
            ), 
            r -> !r.getChannelList().isEmpty()
        );
        
        String channelAddress = response.getChannel(0).getChannelAddress();

        IncomingChannelState channelState = waitFor(
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

        assertBalance(deposit, senderStartBalance.subtract(token.balanceOf(senderAddress).get().getValue()));

        BigInteger transferSum = Convert.toWei("0.001", Convert.Unit.ETHER).toBigIntegerExact();

        SignedTransfer transfer = sendTransfer("1", channelAddress, channelState, transferSum);

        //check for double sending
        assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        Thread.sleep(100);

        Assert.assertEquals(transferSum, channelState.getReceiverState().getCompletedTransfers());

        BigInteger transferSum2 = Convert.toWei("0.002", Convert.Unit.ETHER).toBigIntegerExact();
        SignedTransfer transfer2 = sendTransfer("2", channelAddress, channelState, transferSum2);

        assertNoError(receiverClient.getIncomingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .addTransfer(transfer2.toMessage())
            .build()
        ).getError());

        waitFor(() -> channelState.getOwnState().getCompletedTransfers().signum() > 0);

        BigInteger expectedTotalTransfer = transferSum.add(transferSum2);
        Assert.assertEquals(expectedTotalTransfer, channelState.getOwnState().getCompletedTransfers());
        Assert.assertTrue(channelState.getReceiverState() != null);
        Assert.assertEquals(expectedTotalTransfer, channelState.getReceiverState().getCompletedTransfers());
        
        
        assertNoError(
            senderClient.getClientAdmin().removeChannelPool(
                RemoveChannelPoolRequest.newBuilder()
                    .setSenderAddress(senderAddress.toString())
                    .setReceiverAddress(receiverAddress.toString())
                    .build()
            ).getError()
        );
        
        waitFor(() -> {
            try {
                return channelState.getChannel().getContract().settled().get().getValue().signum() > 0;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        });
        
        //must be settled
        assertBalance(expectedTotalTransfer, senderStartBalance.subtract(token.balanceOf(senderAddress).get().getValue()));
        assertBalance(expectedTotalTransfer, token.balanceOf(receiverAddress).get().getValue().subtract(receiverStartBalance));
    }

    private SignedTransfer sendTransfer(String transferId, String channelAddress, IncomingChannelState channelState, BigInteger transferred) throws InterruptedException {
        BigInteger completedTransfers = channelState.getReceiverState() != null ? channelState.getReceiverState().getCompletedTransfers() : BigInteger.ZERO;
        SignedTransfer transfer = new SignedTransfer(transferId, channelAddress, transferred.toString());
        transfer.sign(signerCredentials.getEcKeyPair());

        assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        waitFor(() -> channelState.getReceiverState() != null && channelState.getReceiverState().getCompletedTransfers().compareTo(completedTransfers) > 0);

        Assert.assertEquals(completedTransfers.add(transferred), channelState.getReceiverState().getCompletedTransfers());
        return transfer;
    }

    private <T> T waitFor(Supplier<T> supplier, Predicate<T> condition) throws InterruptedException {
        AtomicReference<T> reference = new AtomicReference<>();
        waitFor(()-> {
            T t = supplier.get();
            reference.set(t);
            return condition.test(t);
        });
        return reference.get();
    }

    private void waitFor(Supplier<Boolean> condition) throws InterruptedException {
        long start = System.currentTimeMillis();
        long sleep = 10L;
        do {
            long left = (start + MAX_WAIT) - System.currentTimeMillis();
            
            if (left < 0)
                throw new IllegalStateException("Timeout waiting for condition " + condition);

            Thread.sleep(Math.min(sleep, left));
            
            sleep *= 1.5;
            
        } while (!condition.get());
    }

    static void assertBalance(BigInteger a, BigInteger b) {
        if (a.subtract(b).abs().compareTo(BigInteger.valueOf(10000)) > 0) {
            Assert.assertEquals(a, b);
        }
    }
    
    private void assertNoError(Error error) {
        Assert.assertEquals(error.getMessage(), 0, error.getStatusValue());
    }
}
