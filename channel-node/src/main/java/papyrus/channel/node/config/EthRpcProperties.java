package papyrus.channel.node.config;

import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eth.rpc")
public class EthRpcProperties {
    
    private String nodeUrl;
    private BigInteger gasPrice;
    private BigInteger gasLimit;

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
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
}
