package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.Web3j;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthProperties;
import papyrus.channel.node.config.EthereumConfig;

@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
@Component
public class ContractsManagerFactory {
    private final Map<Address, ContractsManager> managerMap;
    private final EthereumConfig config;

    @Autowired
    public ContractsManagerFactory(EthProperties properties, EthereumConfig config, ContractsProperties contractsProperties, Web3j web3j) throws IOException, CipherException {
        this.config = config;
        managerMap = new HashMap<>();

        for (Address address : config.getAddresses()) {
            managerMap.put(address, new ContractsManager(properties.getRpc(), web3j, config.getCredentials(address), contractsProperties));
        }
    }


    public ContractsManager getContractManager(Address address) {
        config.checkAddress(address);
        return managerMap.get(address);
    }

    public ContractsManager getMainContractManager() {
        return getContractManager(config.getMainAddress());
    }
}
