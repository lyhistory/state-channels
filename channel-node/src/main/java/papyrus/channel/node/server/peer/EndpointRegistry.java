package papyrus.channel.node.server.peer;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;

import com.google.common.base.Throwables;

import papyrus.channel.node.config.EthereumConfig;
import papyrus.channel.node.contract.EndpointRegistryContract;

@Service
public class EndpointRegistry {
    private static final Logger log = LoggerFactory.getLogger(EndpointRegistry.class);
    
    private EndpointRegistryContract registry;

    @Autowired
    public EndpointRegistry(EthereumConfig config) {
        this.registry = config.getContractManager(config.getMainAddress()).endpointRegistry();
    }

    public EndpointRegistry(EndpointRegistryContract registry) {
        this.registry = registry;
    }
    
    public void registerEndpoint(Address address, String endpointUrl) {
        try {
            Utf8String currentEndpoint = registry.findEndpointByAddress(address).get();
            if (currentEndpoint != null && currentEndpoint.getValue().equals(endpointUrl)) {
                log.info("Endpoint already registered, will not update");
            } else {
                log.info("Registering endpoint {} -> {}", address, endpointUrl);
                registry.registerEndpoint(new Utf8String(endpointUrl)).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Failed to register node", e);
        }
    }
    
    public String lookupEndpoint(Address address) {
        try {
            return registry.findEndpointByAddress(address).get().getValue();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
