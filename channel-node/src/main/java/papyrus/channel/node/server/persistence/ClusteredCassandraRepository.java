package papyrus.channel.node.server.persistence;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import com.datastax.driver.mapping.Result;

public class ClusteredCassandraRepository<PKey, CKey, T> extends CassandraRepository<Pair<PKey, CKey>, T> {
    private final Class<CKey> ckeyClass;
    protected ClusteredLoader<PKey, CKey, T> clusteredLoader;

    public ClusteredCassandraRepository(Class<T> dataClass, Class<CKey> ckeyClass) {
        super(dataClass);
        this.ckeyClass = ckeyClass;
    }

    protected void init() {
        super.init();

        clusteredLoader = new ClusteredLoader<>(mapper, dataClass, ckeyClass);
    }

    public Optional<T> getById(PKey pkey, CKey ckey) {
        return clusteredLoader.selectByIdKey(pkey, ckey);  
    }

    public Result<T> getAllById(PKey pkey) {
        return clusteredLoader.selectAllById(pkey);  
    }

    public void deleteById(PKey pkey, CKey ckey) {
        clusteredLoader.deleteByIdKey(pkey, ckey);  
    }

    public void deleteByIdAsync(PKey pkey, CKey ckey) {
        clusteredLoader.deleteByIdKeyAsync(pkey, ckey);  
    }

    public ClusteredLoader<PKey, CKey, T> loader() {
        return clusteredLoader;
    }
}
