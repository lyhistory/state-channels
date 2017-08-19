package papyrus.channel.node.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.web3j.abi.datatypes.Address;

@ConfigurationProperties("channel.contracts")
public class ContractsProperties {
    private Map<String, Address> addresses = new HashMap<>();

    public Map<String, Address> getAddresses() {
        return addresses;
    }

    public void setAddresses(Map<String, Address> addresses) {
        this.addresses = addresses;
    }
}
