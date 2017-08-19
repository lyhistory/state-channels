package test;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

public class TestUtil {
    public static final BigInteger WEI_IN_ETH = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigIntegerExact();
    public final String testNode;
    public static final BigInteger GAS_PRICE = BigInteger.ONE;
    public static final BigInteger GAS_LIMIT = BigInteger.valueOf(2000000);
    private static final Logger log = LoggerFactory.getLogger(TestUtil.class);
    
    public static final String PASSWORD = "TestPass";
    public static final File WALLET_FILE = new File("testwallet.json");
    
    private Credentials credentials;
    public final Web3j web3j;

    private TestUtil(String testNode) throws Exception {
        this.testNode = testNode;
        try {
            credentials = WalletUtils.loadCredentials(PASSWORD, WALLET_FILE);
        } catch (Exception e) {
            log.info("Failed to load credentials, generating new " + e);
            credentials = Credentials.create(Keys.createEcKeyPair());
            ObjectMapperFactory.getObjectMapper().writeValue(WALLET_FILE, Wallet.createLight(PASSWORD, Keys.createEcKeyPair()));
        }
        log.info("Test address: " + credentials.getAddress());

        web3j = Web3j.build(new HttpService(this.testNode));
        String etherbase = web3j.ethCoinbase().send().getAddress();

        BigInteger balance = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getBalance();
        log.info("Current balance:" + balance.divide(WEI_IN_ETH));
        if (balance.compareTo(WEI_IN_ETH) < 0) {
            ClientTransactionManager mgr = new ClientTransactionManager(web3j, etherbase);

            BigDecimal ethers = BigDecimal.TEN;
            log.info("Sending " + ethers + "ETH");
            TransactionReceipt receipt = new Transfer(web3j, mgr).sendFundsAsync(credentials.getAddress(), ethers, Convert.Unit.ETHER).get();
            System.out.println(ReflectionToStringBuilder.reflectionToString(receipt));
            balance = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            log.info("New balance: " + balance.divide(WEI_IN_ETH));
        }
    }

    public static TestUtil devnet() throws Exception {
        return new TestUtil("http://35.201.73.170");
    }

    public static TestUtil local() throws Exception {
        return new TestUtil("http://localhost:8545");
    }

    public Credentials getCredentials() {
        return credentials;
    }
}
