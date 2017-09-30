package papyrus.channel.node.server.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;

@Component
public class DatabaseCleaner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCleaner.class);
    @Autowired
    private Session session;
    
    public void clean() {
        log.info("Cleaning all tables");
        for (KeyspaceMetadata keyspaceMetadata : session.getCluster().getMetadata().getKeyspaces()) {
            String keyspace = keyspaceMetadata.getName();
            if (keyspace.startsWith("system")) {
                continue;
            }
            for (TableMetadata metadata : keyspaceMetadata.getTables()) {
                String statement = "TRUNCATE TABLE " + keyspace + "." + metadata.getName();
                session.execute(statement);
            }
        }
    }
    
    public void dropAll() {
        log.info("Dropping all tables");
        CassandraUtil.executeSchemaCql(session, true);
    }
}
