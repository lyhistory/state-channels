package papyrus.channel.node.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("node")
public class NodeProperties {
    private Path dataLocation;
    private GrpcProperties grpc = new GrpcProperties();
    private EthProperties eth = new EthProperties();

    public Path getDataLocation() {
        return dataLocation;
    }

    public void setDataLocation(Path dataLocation) {
        this.dataLocation = dataLocation;
    }

    public GrpcProperties getGrpc() {
        return grpc;
    }

    public void setGrpc(GrpcProperties grpc) {
        this.grpc = grpc;
    }

    public EthProperties getEth() {
        return eth;
    }

    public void setEth(EthProperties eth) {
        this.eth = eth;
    }

    public static class GrpcProperties {
        private int port;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
