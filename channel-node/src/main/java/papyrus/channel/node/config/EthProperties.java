package papyrus.channel.node.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eth")
public class EthProperties {
    private EthRpcProperties rpc = new EthRpcProperties();
    private List<EthKeyProperties> keys = new ArrayList<>();

    public EthRpcProperties getRpc() {
        return rpc;
    }

    public void setRpc(EthRpcProperties rpc) {
        this.rpc = rpc;
    }

    public List<EthKeyProperties> getKeys() {
        return keys;
    }

    public void setKeys(List<EthKeyProperties> keys) {
        this.keys = keys;
    }
}
