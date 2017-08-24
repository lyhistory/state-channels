package papyrus.channel.node.server;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;

import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.node.contract.EndpointRegistry;

@Service
@EnableConfigurationProperties(ChannelServerProperties.class)
public class EndpointRegistrar {
    private static final Logger log = LoggerFactory.getLogger(EndpointRegistrar.class);
    
    private ChannelServerProperties properties;
    private Credentials credentials;
    private EndpointRegistry registry;

    public EndpointRegistrar(ChannelServerProperties properties, Credentials credentials, EndpointRegistry registry) {
        this.properties = properties;
        this.credentials = credentials;
        this.registry = registry;
    }

    @EventListener(ContextStartedEvent.class)
    public void registerEndpoint() {
        String endpointUrl = properties.getEndpointUrl();
        if (endpointUrl == null) {
            log.warn("No endpoint url provided");
            return;
        }
        try {
            String address = credentials.getAddress();
            Utf8String currentEndpoint = registry.findEndpointByAddress(new Address(address)).get();
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
}
