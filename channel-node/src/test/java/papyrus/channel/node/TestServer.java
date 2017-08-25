package papyrus.channel.node;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;

import papyrus.channel.Error;
import papyrus.channel.node.config.EthKeyProperties;
import papyrus.channel.node.server.channel.SignedTransfer;
import papyrus.channel.node.server.channel.incoming.IncomingChannelManager;
import papyrus.channel.node.server.channel.incoming.IncomingChannelState;
import papyrus.channel.node.server.peer.PeerConnection;

public class TestServer {

    private static final String PROFILES = "test,testrpc";
    
    private ConfigurableApplicationContext sender;
    private ConfigurableApplicationContext receiver;
    private PeerConnection senderClient;
    private PeerConnection receiverClient;
    private Credentials signerCredentials;

    @Before
    public void init() throws IOException, CipherException {
        sender = createServerContext("sender");
        receiver = createServerContext("receiver");
        sender.start();
        receiver.start();
        senderClient = sender.getBean(PeerConnection.class);
        signerCredentials = Credentials.create(sender.getBean(EthKeyProperties.class).getSignerPrivateKey());
        Assert.assertNotNull(senderClient);
        receiverClient = receiver.getBean(PeerConnection.class);
        Assert.assertNotNull(receiverClient);
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
    public void testSendEther() throws InterruptedException {
        //add participant - this will initiate channels opening
        Address senderAddress = new Address(sender.getBean(Credentials.class).getAddress());

        senderClient.getClientAdmin().addParticipant(
            AddParticipantRequest.newBuilder()
                .setParticipantAddress(receiver.getBean(Credentials.class).getAddress())
                .setActiveChannels(1)
                .build()
        );

        ChannelStatusResponse response;
        for (int i = 0; ; i ++) {
            Assert.assertTrue(i < 10);
            response = senderClient.getOutgoingChannelClient().getChannels(
                ChannelStatusRequest.newBuilder()
                    .setParticipantAddress(receiver.getBean(Credentials.class).getAddress())
                    .build()
            );
            if (!response.getChannelList().isEmpty()) {
                break;
            }
            Thread.sleep(2000);
        }

        String channelAddress = response.getChannel(0).getChannelAddress();

        SignedTransfer transfer = new SignedTransfer("1", channelAddress, "1000");
        transfer.sign(signerCredentials.getEcKeyPair());
        
        assertNoError(senderClient.getOutgoingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        Thread.sleep(2000);

        IncomingChannelManager incomingChannelManager = receiver.getBean(IncomingChannelManager.class);
        Collection<IncomingChannelState> channels = incomingChannelManager.getChannels(senderAddress);
        Assert.assertEquals(1, channels.size());
        IncomingChannelState channelState = channels.iterator().next();
        Assert.assertEquals(BigInteger.valueOf(1000), channelState.getReceiverState().getCompletedTransfers());

        assertNoError(receiverClient.getIncomingChannelClient().registerTransfers(RegisterTransfersRequest.newBuilder()
            .addTransfer(transfer.toMessage())
            .build()
        ).getError());

        Thread.sleep(2000);

        Assert.assertEquals(BigInteger.valueOf(1000), channelState.getOwnState().getCompletedTransfers());
        Assert.assertTrue(channelState.getReceiverState() != null);
        Assert.assertEquals(BigInteger.valueOf(1000), channelState.getReceiverState().getCompletedTransfers());
    }

    private void assertNoError(Error error) {
        Assert.assertEquals(error.getMessage(), 0, error.getStatusValue());
    }
}
