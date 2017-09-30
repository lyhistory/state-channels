package papyrus.channel.node.server.persistence;

import java.io.IOException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.datastax.driver.core.AtomicMonotonicTimestampGenerator;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TimestampGenerator;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;
import com.datastax.driver.extras.codecs.jdk8.ZonedDateTimeCodec;
import com.datastax.driver.mapping.MappingManager;

import papyrus.channel.node.server.channel.outgoing.OutgoingChannelState;
import papyrus.channel.node.util.Retriable;

@EnableConfigurationProperties(CassandraProperties.class)
@Configuration
public class CassandraConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CassandraConfiguration.class);
    private static final Class[] ENUMS = {
        OutgoingChannelState.Status.class
    };
    static {
        CodecRegistry registry = CodecRegistry.DEFAULT_INSTANCE;
        registry.register(LocalDateCodec.instance);
        for (Class e : ENUMS) {
            //noinspection unchecked
            registry.register(new EnumNameCodec(e));
        }
        registry.register(new AddressCodec());
    }

    @Bean
    public Cluster createCluster(CassandraProperties properties) {
        return Retriable.wrap(() -> doCreateCluster(properties))
            .withErrorMessage("Cannot connect to cassandra cluster")
            .retryOn(NoHostAvailableException.class, UnknownHostException.class)
            .withDelaySec(properties.getConnectDelaySec())
            .call();
    }

    private Cluster doCreateCluster(CassandraProperties properties) {
        Cluster cluster = Cluster.builder()
                .withClusterName(properties.getCluster())
                .withPort(properties.getPort())
                .addContactPoints(properties.getContactPoints())
                .withTimestampGenerator(getTimestampGenerator())
                .withPoolingOptions(
                        //TODO some default options - move to config
                        new PoolingOptions()
                                .setConnectionsPerHost(HostDistance.LOCAL, 4, 4)
                                .setConnectionsPerHost(HostDistance.REMOTE, 2, 2)
                                .setMaxRequestsPerConnection(HostDistance.LOCAL, 1024)
                                .setMaxRequestsPerConnection(HostDistance.REMOTE, 256)
                )
                .build();
        //almost all queries are idempotent except counter updates, so it's easier to mark them as idempotent
        cluster.getConfiguration().getQueryOptions().setDefaultIdempotence(true);
        
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();

        TupleType tupleType = cluster.getMetadata()
                .newTupleType(DataType.timestamp(), DataType.varchar());
        codecRegistry.register(new ZonedDateTimeCodec(tupleType));

        QueryLogger queryLogger = QueryLogger.builder()
                .withConstantThreshold(100)
                .withMaxQueryStringLength(200)
                .build();
        cluster.register(queryLogger);

        return cluster;
    }

    @Bean
    public TimestampGenerator getTimestampGenerator() {
        return new AtomicMonotonicTimestampGenerator();
    }

    @Bean
    public Session createSession(CassandraProperties properties, Cluster cluster) throws Exception {

        Session session = Retriable.wrap(cluster::connect)
            .withErrorMessage("Cannot connect to cassandra cluster")
            .retryOn(NoHostAvailableException.class)
            .withDelaySec(properties.getConnectDelaySec())
            .call();

        initDb(properties, session);

        if (!session.getCluster().getMetadata().checkSchemaAgreement()) {
            log.warn("SCHEMA IS NOT IN AGREEMENT!!!");
        }

        return session;
    }

    @Bean
    public MappingManager createMappingManager(Session session) {
        return new MappingManager(session);
    }

    private void initDb(CassandraProperties properties, Session session) throws InterruptedException, IOException {
        CassandraUtil.executeSchemaCql(session, properties.isDeleteData());
        
        log.info("Database created");
    }
}
