package papyrus.channel.node.server.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class CassandraRepository<Id, T> {
    protected final Class<T> dataClass;
    protected Mapper<T> mapper;
    protected Session session;

    @Autowired
    protected MappingManager mappingManager;

    public CassandraRepository(Class<T> dataClass) {
        this.dataClass = dataClass;
    }
    
    @PostConstruct
    protected void init() {
        if (mapper != null) {
            throw new IllegalStateException("Already initialized");
        }
        mapper = mappingManager.mapper(dataClass);
        session = mappingManager.getSession();
    }

    public Optional<T> getById(Id id) {
        return Optional.ofNullable(mapper.get(CassandraUtil.keysToArray(id)));
    }

    public ListenableFuture<T> getByIdAsync(Id id) {
        return mapper.getAsync(CassandraUtil.keysToArray(id));
    }

    public void save(T value) {
        beforeSave(value); 
        mapper.save(value);
    }

    protected void beforeSave(T value) {
    }

    protected void beforeUpdate(T value) {
    }

    /**
     * Updates only non-null fields
     * @param value object with fields filled with values to update
     */
    public void update(T value) {
        beforeUpdate(value);
        mapper.save(value, Mapper.Option.saveNullFields(false));
    }

    public Statement saveQuery(T value) {
        beforeSave(value);
        return mapper.saveQuery(value);
    }

    public void delete(Id id) {
        mapper.delete(CassandraUtil.keysToArray(id));
    }
    
    public void deleteAsync(Id id) {
        mapper.deleteAsync(CassandraUtil.keysToArray(id));
    }

    public <I extends Id> Map<I, T> getAllByIds(Collection<I> ids) {
        return Maps.transformValues(Maps.toMap(ids, this::getByIdAsync), Futures::getUnchecked);
    }
}
