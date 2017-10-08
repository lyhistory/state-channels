package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import javax.annotation.PostConstruct;

import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import com.google.common.base.Throwables;

import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.util.Retriable;

@Service
public class EthereumService {
    private static final Logger log = LoggerFactory.getLogger(EthereumService.class);
    private String netVersion;

    private EthereumConfig config;
    private final Web3j web3j;
    private long blockNumber;
    private long blockNumberUpdated;

    public EthereumService(EthereumConfig config, Web3j web3j) throws IOException, ExecutionException, InterruptedException {
        this.config = config;

        this.web3j = web3j;
    }

    @PostConstruct
    public void init() throws IOException, InterruptedException, ExecutionException {
        Retriable.wrapTask(this::doInit)
            .retryOn(HttpHostConnectException.class)
            .withErrorMessage("Failed to connect to ethereum RPC")
            .withDelaySec(10)
            .call();
    }

    private void doInit() throws IOException, InterruptedException, ExecutionException {
        netVersion = web3j.netVersion().send().getNetVersion();
        for (Address address : config.getAddresses()) {
            BigDecimal autoRefill = config.getKeyProperties(address).getAutoRefill();

            String nodeAddress = address.toString();
            refill(autoRefill, nodeAddress);
        }
    }

    public void refill(BigDecimal minBalance, String address) throws IOException, InterruptedException, ExecutionException {
        BigDecimal balance = getBalance(address, Convert.Unit.ETHER);

        log.info("Balance of {} is {}", address, balance);

        if (minBalance != null && minBalance.signum() > 0) {
            if (balance.compareTo(minBalance) < 0) {
                String coinbase = web3j.ethCoinbase().send().getAddress();
                
                log.info("Refill {} ETH from {} to {}", minBalance, coinbase, address);
                new Transfer(web3j, new ClientTransactionManager(web3j, coinbase, 100, 1000)).sendFundsAsync(address, minBalance, Convert.Unit.ETHER).get();
            }
            log.info("Balance after refill of {} is {}", address, getBalance(address, Convert.Unit.ETHER));
        }
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    public BigDecimal getBalance(String nodeAddress, Convert.Unit unit) {
        try {
            BigInteger balance = web3j.ethGetBalance(nodeAddress, DefaultBlockParameterName.LATEST).send().getBalance();
            return Convert.fromWei(new BigDecimal(balance), unit);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public long getBlockNumber() {
        try {
            if (blockNumber == 0 || System.currentTimeMillis() - blockNumberUpdated > 1000) {
                blockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValueExact();
                blockNumberUpdated = System.currentTimeMillis();
            }
            return blockNumber;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
