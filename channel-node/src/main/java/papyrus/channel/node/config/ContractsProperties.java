package papyrus.channel.node.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.web3j.abi.datatypes.Address;

@ConfigurationProperties("channel.contracts")
public class ContractsProperties {
    private Map<String, Address> predeployed = new HashMap<>();

    public Map<String, Address> getPredeployed() {
        return predeployed;
    }

    public void setPredeployed(Map<String, Address> predeployed) {
        this.predeployed = predeployed;
    }
}
