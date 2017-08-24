package papyrus.channel.node;

import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;

import papyrus.channel.Error;

public class TestServer {

    private static final String PROFILES = "test,testrpc";
    
    private ConfigurableApplicationContext sender;
    private ConfigurableApplicationContext receiver;
    private NodeClient senderClient;
    private NodeClient receiverClient;

    @Before
    public void init() throws IOException, CipherException {
        sender = createServerContext("sender");
        receiver = createServerContext("receiver");
        sender.start();
        receiver.start();
        senderClient = sender.getBean(NodeClient.class);
        Assert.assertNotNull(senderClient);
        receiverClient = sender.getBean(NodeClient.class);
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
        senderClient.getClientAdmin().addParticipant(
            AddParticipantRequest.newBuilder()
                .setParticipantAddress(receiver.getBean(Credentials.class).getAddress())
                .setActiveChannels(1)
                .build()
        );

        ChannelStatusResponse response;
        for (int i = 0; ; i ++) {
            Assert.assertTrue(i < 10);
            response = senderClient.getChannelClient().outgoingChannelState(
                ChannelStatusRequest.newBuilder()
                    .setParticipantAddress(receiver.getBean(Credentials.class).getAddress())
                    .build()
            );
            if (!response.getChannelsList().isEmpty()) {
                break;
            }
            Thread.sleep(2000);
        }
    }

    private void checkError(Error error) {
        Assert.assertNull(error != null ? error.getMessage() : "", error);
    }
}
