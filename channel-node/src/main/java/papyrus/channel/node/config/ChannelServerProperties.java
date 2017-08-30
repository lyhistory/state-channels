package papyrus.channel.node.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("channel.server")
public class ChannelServerProperties {
    private int port;
    private boolean secure;
    private String endpointUrl;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }
}
