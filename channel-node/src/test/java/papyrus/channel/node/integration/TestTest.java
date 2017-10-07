package papyrus.channel.node.integration;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.config.PropertyConvertersConfig;
import papyrus.channel.node.config.Web3jConfigurer;
import papyrus.channel.node.contract.TestContract;
import papyrus.channel.node.server.ethereum.EthereumService;

@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
@ActiveProfiles({"test", "devnet", "sender"})
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PropertyConvertersConfig.class, Web3jConfigurer.class, EthereumConfig.class, EthereumService.class})
public class TestTest {
    
    @Autowired
    private Web3j web3j;
    
    @Autowired
    private EthereumConfig cfg;
    
    @Autowired
    private ContractsProperties contractsProperties;
    
    @Test
    public void testTest() throws IOException, ExecutionException, InterruptedException {
        Address a1 = new Address("b508d41ecb22e9b9bb85c15b5fb3a90cdaddc4ea");
        Address a2 = new Address("bb9208c172166497cd04958dce1a3f67b28e4d7b");
        TestContract contract = TestContract.deploy(web3j, cfg.getMainCredentials(), cfg.getRpcProperties().getGasPrice(), cfg.getRpcProperties().getGasLimit(), BigInteger.ZERO).get();
        TransactionReceipt receipt = contract.newChannel(
            a1, a2, new Uint256(10), (DynamicArray<Address>) DynamicArray.empty(Address.TYPE_NAME), new Uint8(0)
        ).get();
        Assert.assertNotNull(receipt.getTransactionIndex());
        Assert.assertNotNull(receipt.getTransactionHash());
    }
}
