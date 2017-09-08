package test;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class Test {
    private static final Logger log = LoggerFactory.getLogger(Test.class);

    //    public static final String TEST_NODE = "http://localhost:8545";

    public static void main(String[] args) throws Exception {
        TestUtil util = TestUtil.local();
        Web3j web3j = util.web3j;
        Credentials testCredentials = util.getCredentials();
        
        BigInteger nonce = web3j.ethGetTransactionCount(testCredentials.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        


//        byte chainId = Byte.valueOf(web3j.netVersion().send().getNetVersion());

//        String contractAddress;
//        LinkingTransactionManager linkingManager = new LinkingTransactionManager(web3j, testCredentials);
//        ECRecovery ecRecovery = ECRecovery.deploy(web3j, linkingManager, TestCryptoUtil.GAS_PRICE, TestCryptoUtil.GAS_LIMIT, BigInteger.ZERO).get();
//        Assert.assertTrue(ecRecovery.isValid());
//        linkingManager.provide("ECRecovery", ecRecovery);
        
/*
        TestContract contract = TestContract.deploy(web3j, manager, TestCryptoUtil.GAS_PRICE, TestCryptoUtil.GAS_LIMIT, BigInteger.ZERO,
            new Uint256(12345)
        ).get();
        contractAddress = contract.getContractAddress();
        log.info("Contract address:" + contractAddress);
*//*

        contractAddress = "0x9d7b7d077f234fb6449036f213420ca60bb713c8";

        contract = TestContract.load(contractAddress, web3j, testCredentials, TestCryptoUtil.GAS_PRICE, TestCryptoUtil.GAS_LIMIT);
        Uint256 uint256 = contract.getValue().get();
        Assert.assertEquals(new Uint256(12345), uint256);

//        CompiledContract contract = CompiledContract.load(new File("smart-contracts/build/contracts/ChannelContract.json"));
//        RawTransaction rawTransaction = RawTransaction.createContractTransaction(
//            nonce,
//            gasPrice,
//            BigInteger.valueOf(2000000),
//            BigInteger.ZERO,
//            contract.binary
//        );
        // get contract address
//        String transactionHash = manager.signAndSend(rawTransaction).getTransactionHash();
//
//        if (transactionHash == null) {
//            throw new IllegalStateException();
//        }
//        TransactionReceipt receipt = waitReceipt(web3j, transactionHash);
//        log.info(receipt.getContractAddress());
        
//        web3.ethLogObservable(
//            new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, "0x28E9b8fBaacAa0615067700909017e69120ad202")
//            .addSingleTopic()
//        ).
        System.exit(0);
*/
    }

    private static TransactionReceipt waitReceipt(Web3j web3j, String h) throws java.io.IOException, InterruptedException {
        while (true) {
            EthGetTransactionReceipt rc = web3j.ethGetTransactionReceipt(h).send();
            if (rc.getTransactionReceipt().isPresent()) {
                return rc.getTransactionReceipt().get();
            }
            Thread.sleep(1000L);
        }
    }
}
