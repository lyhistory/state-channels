package papyrus.channel.node.server;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.LinkingManager;

@Service
@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
public class EthereumService {
    private static final Logger log = LoggerFactory.getLogger(EthereumService.class);
    
    private EthereumConfig config;
    private final LinkingManager manager;

    public EthereumService(EthereumConfig config, EthProperties ethProperties, ContractsProperties contractsProperties) throws IOException, ExecutionException, InterruptedException {
        this.config = config;

        Web3j web3j = config.getWeb3j();
        FastRawTransactionManager transactionManager = new FastRawTransactionManager(web3j, config.getCredentials());
        manager = new LinkingManager(web3j, transactionManager, ethProperties.getGasPrice(), ethProperties.getGasLimit());

        contractsProperties.getAddresses().forEach(manager::provide);

        BigDecimal autoRefill = ethProperties.getTest().getAutoRefill();
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

    public BigDecimal getBalance(String nodeAddress, Convert.Unit unit) throws IOException {
        BigInteger balance = config.getWeb3j().ethGetBalance(nodeAddress, DefaultBlockParameterName.LATEST).send().getBalance();
        return Convert.fromWei(new BigDecimal(balance), unit);
    }

    @Bean
    public LinkingManager getManager() {
        return manager;
    }
}
