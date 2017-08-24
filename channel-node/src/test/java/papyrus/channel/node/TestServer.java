package papyrus.channel.node;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.protocol.ObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import papyrus.channel.Error;
import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthRpcProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.config.EthKeyProperties;
import papyrus.channel.node.server.ChannelClientImpl;
import papyrus.channel.node.server.ChannelPeerImpl;
import papyrus.channel.node.server.EthereumService;
import papyrus.channel.node.server.NodeServer;
import papyrus.channel.node.server.admin.ChannelAdminImpl;
import papyrus.channel.node.server.outgoing.OutgoingChannelPoolManager;
import papyrus.channel.protocol.ChannelPeerGrpc;
import test.TestUtil;

@ActiveProfiles("testrpc")
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource("classpath:/test.properties")
@EnableConfigurationProperties({ContractsProperties.class, EthRpcProperties.class})
public class TestServer {

    @Autowired
    private ContractsProperties contractsProperties;
    @Autowired
    private EthRpcProperties ethRpcProperties;
    private Participant sender;
    private Participant receiver;

    @Before
    public void init() throws IOException, CipherException {
        sender = new Participant("sender.wallet.json", 8801);
        receiver = new Participant("receiver.wallet.json", 8802);
    }

    @After
    public void finish() throws IOException, CipherException {
        if (sender != null) sender.stop();
        if (sender != null) receiver.stop();
    }
    
    @Test
    public void testSendEther() throws InterruptedException {
        //add participant - this will initiate channels opening
        sender.clientAdmin.addParticipant(
            AddParticipantRequest.newBuilder()
                .setParticipantAddress(receiver.credentials.getAddress())
                .setActiveChannels(1)
                .build()
        );

        ChannelStatusResponse response;
        for (int i = 0; ; i ++) {
            Assert.assertTrue(i < 10);
            response = sender.channelClient.outgoingChannelState(
                ChannelStatusRequest.newBuilder()
                    .setParticipantAddress(receiver.credentials.getAddress())
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

    private class Participant {
        ChannelClientGrpc.ChannelClientBlockingStub channelClient;
        ChannelPeerGrpc.ChannelPeerBlockingStub channelPeer;
        String address;
        ManagedChannel channel;
        Credentials credentials;
        ChannelAdminGrpc.ChannelAdminBlockingStub clientAdmin;
        NodeServer server;

        Participant(String walletPath, int serverPort) {
            try {
                createServer(walletPath, serverPort);
                createClient(serverPort);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private void createServer(String walletPath, int serverPort) throws IOException, CipherException, ExecutionException, InterruptedException {
            ChannelServerProperties serverProperties = new ChannelServerProperties();
            serverProperties.setPort(serverPort);
            serverProperties.setEndpointUrl("localhost:"+serverPort);

            String string = Resources.toString(Resources.getResource(walletPath), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
            WalletFile walletFile = objectMapper.readValue(string, WalletFile.class);
            credentials = Credentials.create(Wallet.decrypt(TestUtil.PASSWORD, walletFile));
            address = credentials.getAddress();

            EthKeyProperties keyProperties = new EthKeyProperties();
            keyProperties.setAutoRefill(BigDecimal.TEN);
            keyProperties.setPrivateKey(credentials.getEcKeyPair().getPrivateKey().toString(16));
            EthereumConfig config = new EthereumConfig(keyProperties, ethRpcProperties);
            OutgoingChannelPoolManager poolManager = new OutgoingChannelPoolManager(new EthereumService(config, contractsProperties), Executors.newScheduledThreadPool(1));
            server = new NodeServer(new ChannelClientImpl(poolManager), new ChannelPeerImpl(), new ChannelAdminImpl(poolManager), serverProperties);
            server.start();
        }

        private void createClient(int serverPort) {
            channel = ManagedChannelBuilder.forAddress("localhost", serverPort).usePlaintext(true).build();
            clientAdmin = ChannelAdminGrpc.newBlockingStub(channel);
            channelClient = ChannelClientGrpc.newBlockingStub(channel);
            channelPeer = ChannelPeerGrpc.newBlockingStub(channel);
        }

        void stop() {
            if (server != null) server.stop();
        }
    }
    
}
