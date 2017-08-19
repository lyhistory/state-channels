package papyrus.channel.node.config;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eth")
public class EthProperties {
    
    private String nodeUrl;
    private String keyLocation;
    private String keyPassword;
    private BigInteger gasPrice;
    private BigInteger gasLimit;
    private Test test = new Test();

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

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

    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public BigInteger getGasPrice() {
        return gasPrice;
    }

    public void setGasPrice(BigInteger gasPrice) {
        this.gasPrice = gasPrice;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
    }

    //only for development
    public static class Test {
        private String privateKey;
        //automatically add some ethers to private address
        private BigDecimal autoRefill = BigDecimal.ZERO;

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
}
