package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;

@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
@Component
public class ContractsManagerFactory {
    private final Map<Address, ContractsManager> managerMap;
    private final EthProperties ethProperties;
    private final EthereumConfig config;
    private final ContractsProperties contractsProperties;
    private final Web3j web3j;

    @Autowired
    public ContractsManagerFactory(EthProperties ethProperties, EthereumConfig config, ContractsProperties contractsProperties, Web3j web3j) throws IOException, CipherException {
        this.ethProperties = ethProperties;
        this.config = config;
        this.contractsProperties = contractsProperties;
        this.web3j = web3j;
        managerMap = new HashMap<>();

        for (Address address : config.getAddresses()) {
            ThreadsafeTransactionManager transactionManager = config.getTransactionManager(address);
            managerMap.put(address, createManager(transactionManager, config.getCredentials(address)));
        }
    }

    public ContractsManager createManager(TransactionManager transactionManager, Credentials credentials) {
        return new ContractsManager(ethProperties.getRpc(), web3j, credentials, contractsProperties, transactionManager);
    }


    public ContractsManager getContractManager(Address address) {
        config.checkAddress(address);
        return managerMap.get(address);
    }

    public ContractsManager getMainContractManager() {
        return getContractManager(config.getMainAddress());
    }
}
