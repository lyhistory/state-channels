package papyrus.channel.node;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.web3j.abi.datatypes.Address;
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
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.server.ChannelClientImpl;
import papyrus.channel.node.server.ChannelPeerImpl;
import papyrus.channel.node.server.EthereumService;
import papyrus.channel.node.server.NodeServer;
import papyrus.channel.node.server.admin.ChannelAdminImpl;
import papyrus.channel.node.server.outgoing.OutgoingChannelPoolManager;
import papyrus.channel.protocol.ChannelPeerGrpc;
import test.TestUtil;

public class TestServer {

    private static Participant sender;
    private static Participant receiver;

    @BeforeClass
    public static void init() throws IOException, CipherException {
        sender = new Participant("sender.wallet.json", 8801);
        receiver = new Participant("receiver.wallet.json", 8802);
    }
    
    @AfterClass
    public static void finish() throws IOException, CipherException {
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

    private static class Participant {
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

            String string = Resources.toString(Resources.getResource(walletPath), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
            WalletFile walletFile = objectMapper.readValue(string, WalletFile.class);
            credentials = Credentials.create(Wallet.decrypt(TestUtil.PASSWORD, walletFile));
            address = credentials.getAddress();

            //TODO read from application.yaml
            EthProperties properties = new EthProperties();
            properties.setGasLimit(new BigInteger("2000000"));
            properties.setGasPrice(new BigInteger("1"));
            ContractsProperties contractsProperties = new ContractsProperties();
            HashMap<String, Address> map = new HashMap<>();
            map.put("EndpointRegistry", new Address("0x7040bd5616637c8a0eaf16e3c6641c1a6f7ee165"));
            map.put("ChannelLibrary", new Address("0xef6da97a94b049aa11168f8b96b9bf75892d5834"));
            contractsProperties.setAddresses(map);
            EthProperties.Test test = new EthProperties.Test();
            test.setAutoRefill(BigDecimal.TEN);
            test.setPrivateKey(credentials.getEcKeyPair().getPrivateKey().toString(16));
            properties.setTest(test);
            properties.setNodeUrl("http://dev.papyrus.global");
            EthereumConfig config = new EthereumConfig(properties);
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
