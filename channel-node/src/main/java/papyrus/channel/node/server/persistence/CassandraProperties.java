package papyrus.channel.node.server.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cassandra")
public class CassandraProperties {

    private String cluster;
    private String contactPoints;
    private int port;
    private int connectDelaySec = 5;
    private boolean deleteData;

    public CassandraProperties() {
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getContactPoints() {
        return contactPoints;
    }

    public void setContactPoints(String contactPoints) {
        this.contactPoints = contactPoints;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectDelaySec() {
        return connectDelaySec;
    }

    public void setConnectDelaySec(int connectDelaySec) {
        this.connectDelaySec = connectDelaySec;
    }

    public boolean isDeleteData() {
        return deleteData;
    }

    public void setDeleteData(boolean deleteData) {
        this.deleteData = deleteData;
    }
}
