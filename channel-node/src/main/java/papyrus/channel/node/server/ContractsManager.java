package papyrus.channel.node.server;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;

import papyrus.channel.node.config.ChannelServerProperties;
import papyrus.channel.node.contract.LinkingManager;

@Component
@EnableConfigurationProperties(ChannelServerProperties.class)
public class ContractsManager {
    private static final Logger log = LoggerFactory.getLogger(ContractsManager.class);

    private final LinkingManager manager;
    private final ChannelServerProperties properties;
//    private final EndpointRegistry registry;

    public ContractsManager(LinkingManager manager, ChannelServerProperties properties) throws IOException {
        this.manager = manager;
        this.properties = properties;
//        registry = manager.loadLibraryContract(EndpointRegistry.class);
    }

    @EventListener(ContextStartedEvent.class)
    public void registerEndpoint() {
        String endpointUrl = properties.getEndpointUrl();
        if (endpointUrl == null) {
            log.warn("No endpoint url provided");
            return;
        }
//        try {
//            Utf8String currentEndpoint = registry.findEndpointByAddress(new Address(manager.getFromAddress())).get();
//            if (currentEndpoint != null && currentEndpoint.getValue().equals(endpointUrl)) {
//                log.info("Endpoint already registered, will not update");
//            } else {
//                log.info("Registering endpoint {} -> {}", manager.getFromAddress(), endpointUrl);
//                registry.registerEndpoint(new Utf8String(endpointUrl)).get();
//            }
//        } catch (InterruptedException | ExecutionException e) {
//            throw new IllegalStateException("Failed to register node", e);
//        }
    }
}
