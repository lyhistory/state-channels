package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthRpcProperties;
import papyrus.channel.node.config.EthereumConfig;

@Service
@EnableConfigurationProperties({EthRpcProperties.class, ContractsProperties.class})
public class EthereumService {
    private static final Logger log = LoggerFactory.getLogger(EthereumService.class);
    
    private EthereumConfig config;
    private final Web3j web3j;
    private BigInteger blockNumber;
    private long blockNumberUpdated;

    public EthereumService(EthereumConfig config, ContractsProperties contractsProperties) throws IOException, ExecutionException, InterruptedException {
        this.config = config;

        web3j = config.getWeb3j();

        BigDecimal autoRefill = config.getKeyProperties().getAutoRefill();
        if (autoRefill != null && autoRefill.signum() > 0) {
            String nodeAddress = config.getCredentials().getAddress();
            BigDecimal balance = getBalance(nodeAddress, Convert.Unit.ETHER);
            if (balance.compareTo(autoRefill) < 0) {
                String coinbase = web3j.ethCoinbase().send().getAddress();
                log.info("Refill {} ETH from {} to {}", autoRefill, coinbase, nodeAddress);
                new Transfer(web3j, new ClientTransactionManager(web3j, coinbase)).sendFundsAsync(nodeAddress, autoRefill, Convert.Unit.ETHER).get();
            }
            log.info("Balance of {} is {}", nodeAddress, getBalance(nodeAddress, Convert.Unit.ETHER));
        }
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    public BigDecimal getBalance(String nodeAddress, Convert.Unit unit) throws IOException {
        BigInteger balance = config.getWeb3j().ethGetBalance(nodeAddress, DefaultBlockParameterName.LATEST).send().getBalance();
        return Convert.fromWei(new BigDecimal(balance), unit);
    }

    public BigInteger getBlockNumber() throws IOException {
        if (blockNumber == null || System.currentTimeMillis() - blockNumberUpdated > 1000) {
            blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            blockNumberUpdated = System.currentTimeMillis();
        }
        return blockNumber;
    }
}
