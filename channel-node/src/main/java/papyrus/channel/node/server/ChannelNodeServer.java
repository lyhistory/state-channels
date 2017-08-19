package papyrus.channel.node.server;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class ChannelNodeServer {
    private ContractsManager contractsManager;
    private GrpcServer grpcServer;

    public ChannelNodeServer(ContractsManager contractsManager, GrpcServer grpcServer) {
        this.contractsManager = contractsManager;
        this.grpcServer = grpcServer;
    }
    
    @PostConstruct
    public void init() {
        contractsManager.registerEndpoint();
    }
}
