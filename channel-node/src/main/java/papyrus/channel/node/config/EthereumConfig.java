package papyrus.channel.node.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@EnableConfigurationProperties({EthRpcProperties.class, EthKeyProperties.class})
@Configuration
public class EthereumConfig {
    private static final Logger log = LoggerFactory.getLogger(EthereumConfig.class);

    private EthKeyProperties keyProperties;
    private EthRpcProperties rpcProperties;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public EthereumConfig(EthKeyProperties keyProperties, EthRpcProperties rpcProperties) throws IOException, CipherException {
        this.keyProperties = keyProperties;
        this.rpcProperties = rpcProperties;
        this.credentials = loadCredentials(keyProperties);
        log.info("Using eth address {} and rpc server {}", credentials.getAddress(), rpcProperties.getNodeUrl());
        web3j = Web3j.build(new HttpService(rpcProperties.getNodeUrl()));
    }

    private static Credentials loadCredentials(EthKeyProperties keyProperties) throws IOException, CipherException {
        if (keyProperties.getKeyLocation() != null) {
            return WalletUtils.loadCredentials(keyProperties.getKeyPassword(), keyProperties.getKeyLocation());
        } else if (keyProperties.getPrivateKey() != null) {
            return Credentials.create(keyProperties.getPrivateKey());
        } else {
            throw new IllegalStateException("Either node.eth.key-location or node.eth.test.private-key required");
        }
    }

    public EthKeyProperties getKeyProperties() {
        return keyProperties;
    }

    public EthRpcProperties getRpcProperties() {
        return rpcProperties;
    }

    @Bean
    public Web3j getWeb3j() {
        return web3j;
    }

    @Bean
    public Credentials getCredentials() {
        return credentials;
    }
}
