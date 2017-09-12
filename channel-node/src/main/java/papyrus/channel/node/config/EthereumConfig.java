package papyrus.channel.node.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
@Configuration
public class EthereumConfig {
    private static final Logger log = LoggerFactory.getLogger(EthereumConfig.class);

    private EthProperties properties;
    private final Map<Address, EthKeyProperties> keyProperties;
    private final Map<Address, Credentials> credentialsMap;
    private final Map<Address, TransactionManager> transactionManagerMap;
    private final Credentials mainCredentials;

    @Autowired
    public EthereumConfig(EthProperties properties, ContractsProperties contractsProperties, Web3j web3j) throws IOException, CipherException {
        this.properties = properties;
        keyProperties = new HashMap<>();
        credentialsMap = new HashMap<>();
        transactionManagerMap = new HashMap<>();

        EthRpcProperties rpc = properties.getRpc();
        Credentials mainCred = null;
        for (EthKeyProperties key : properties.getKeys()) {
            Credentials credentials = loadCredentials(key);
            if (mainCred == null) mainCred = credentials;
            Address address = new Address(credentials.getAddress());
            keyProperties.put(address, key);
            credentialsMap.put(address, credentials);
            RawTransactionManager transactionManager = new RawTransactionManager(web3j, credentials, rpc.getAttempts(), (int) rpc.getSleep().toMillis());
            transactionManagerMap.put(address, transactionManager);
        }
        
        assert keyProperties.size() == properties.getKeys().size();
        assert credentialsMap.size() == properties.getKeys().size();
        assert transactionManagerMap.size() == properties.getKeys().size();
        
        Preconditions.checkState(mainCred != null, "No eth.keys defined");
        mainCredentials = mainCred;
        
        log.info("Configured ETH payment accounts:{} and rpc server: {}", credentialsMap.keySet(), this.properties.getRpc().getNodeUrl());
    }

    private static Credentials loadCredentials(EthKeyProperties keyProperties) {
        if (keyProperties.getKeyLocation() != null) {
            try {
                return WalletUtils.loadCredentials(keyProperties.getKeyPassword(), keyProperties.getKeyLocation());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else if (keyProperties.getPrivateKey() != null) {
            return Credentials.create(keyProperties.getPrivateKey());
        } else {
            throw new IllegalStateException("Either node.eth.key-location or node.eth.test.private-key required");
        }
    }

    public EthKeyProperties getKeyProperties(Address address) {
        checkAddress(address);
        return keyProperties.get(address);
    }

    public Set<Address> getAddresses() {
        return keyProperties.keySet();
    }

    public void checkAddress(Address address) {
        if (!credentialsMap.containsKey(address)) {
            throw new IllegalArgumentException("Account " + address + " is not managed");
        }
    }

    public Credentials getMainCredentials() {
        return mainCredentials;
    }

    public Address getMainAddress() {
        return new Address(getMainCredentials().getAddress());
    }

    public TransactionManager getTransactionManager(Address address) {
        checkAddress(address);
        return transactionManagerMap.get(address);
    }

    public EthRpcProperties getRpcProperties() {
        return properties.getRpc();
    }

    public Address getClientAddress(Address address) {
        checkAddress(address);
        EthKeyProperties ethKeyProperties = keyProperties.get(address);
        return ethKeyProperties == null ? null : ethKeyProperties.getClientAddress() == null ? address : ethKeyProperties.getClientAddress();
    }

    public Credentials getCredentials(Address address) {
        return credentialsMap.get(address);
    }
}
