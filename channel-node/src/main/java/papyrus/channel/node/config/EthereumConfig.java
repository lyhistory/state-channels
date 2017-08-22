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

@EnableConfigurationProperties(EthProperties.class)
@Configuration
public class EthereumConfig {
    private static final Logger log = LoggerFactory.getLogger(EthereumConfig.class);
    
    private final EthProperties properties;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public EthereumConfig(EthProperties properties) throws IOException, CipherException {
        this.properties = properties;
        if (properties.getKeyLocation() != null) {
            credentials = WalletUtils.loadCredentials(properties.getKeyPassword(), properties.getKeyLocation());
        } else if (properties.getTest().getPrivateKey() != null) {
            credentials = Credentials.create(properties.getTest().getPrivateKey());
        } else {
            throw new IllegalStateException("Either node.eth.key-location or node.eth.test.private-key required");
        }
        log.info("Using address {} and rpc server {}", credentials.getAddress(), properties.getNodeUrl());
        web3j = Web3j.build(new HttpService(properties.getNodeUrl()));
    }

    public EthProperties getProperties() {
        return properties;
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
