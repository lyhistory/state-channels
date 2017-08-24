package papyrus.channel.node.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eth.key")
public class EthKeyProperties {
    
    private String keyLocation;
    private String keyPassword;
    //only for development
    private String privateKey;
    //automatically add some ethers to address
    private BigDecimal autoRefill = BigDecimal.ZERO;
    
    public String getKeyLocation() {
        return keyLocation;
    }

    public void setKeyLocation(String keyLocation) {
        this.keyLocation = keyLocation;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public BigDecimal getAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(BigDecimal autoRefill) {
        this.autoRefill = autoRefill;
    }
}
