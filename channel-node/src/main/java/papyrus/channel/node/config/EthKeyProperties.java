package papyrus.channel.node.config;

import java.math.BigDecimal;

import org.web3j.abi.datatypes.Address;

public class EthKeyProperties {
    
    private String keyLocation;
    private String keyPassword;
    /**
     * Address for signer transfers. If not specified main address is used.
     */
    private Address signerAddress;
    
    /*
     * !!! NEXT CONFIGS ARE FOR TESTS ONLY !!!
     */
    
    private String signerPrivateKey;
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

    public Address getSignerAddress() {
        return signerAddress;
    }

    public void setSignerAddress(Address signerAddress) {
        this.signerAddress = signerAddress;
    }

    public String getSignerPrivateKey() {
        return signerPrivateKey;
    }

    public void setSignerPrivateKey(String signerPrivateKey) {
        this.signerPrivateKey = signerPrivateKey;
    }
}