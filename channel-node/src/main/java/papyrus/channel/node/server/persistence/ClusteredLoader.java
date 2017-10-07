package papyrus.channel.node.server.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.datastax.driver.core.AbstractTableMetadata;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.MaterializedViewMetadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.common.collect.Lists;

public class ClusteredLoader<Id, CKey, Data> {

    private final Mapper<Data> mapper;
    private final Session session;
    private final PreparedStatement selectUnbounded;
    private final PreparedStatement selectBefore;
    private final PreparedStatement selectAfter;
    private final PreparedStatement selectBeforeAfter;
    private final Function<Data, CKey> indexAccessor;
    private final PreparedStatement selectByIdKey;
    private final PreparedStatement selectUnboundedAsc;
    private final PreparedStatement selectBeforeAsc;
    private final PreparedStatement selectAfterAsc;
    private final PreparedStatement selectBeforeAfterAsc;

    private final PreparedStatement deleteByIdKey;
    private final PreparedStatement selectAllById;
    private final PreparedStatement deleteAllById;

    public ClusteredLoader(Mapper<Data> mapper, Class<Data> dataClass, Class<CKey> ckeyClass) {
        this(mapper, dataClass, ckeyClass, mapper.getTableMetadata().getName());
    }
    
    public ClusteredLoader(Mapper<Data> mapper, Class<Data> dataClass, Class<CKey> ckeyClass, String tableName) {
        MappingManager manager = mapper.getManager();
        session = manager.getSession();
        this.mapper = manager.mapper(dataClass);

        String keyspace = mapper.getTableMetadata().getKeyspace().getName();
        MaterializedViewMetadata mv = mapper.getTableMetadata().getKeyspace().getMaterializedView(tableName);
        AbstractTableMetadata tableMetadata = mv == null ? mapper.getTableMetadata().getKeyspace().getTable(tableName) : mv;
        if (tableMetadata == null) {
            throw new IllegalArgumentException("No table or materialized view " + keyspace + "." + tableName + "found");
        }
        
        List<ColumnMetadata> primaryKey = tableMetadata.getPrimaryKey();
        String pkEq = exceptLast(primaryKey).stream()
            .map(c -> c.getName() + "=?")
            .collect(Collectors.joining(" and "));
        
        List<ColumnMetadata> clusteringColumns = tableMetadata.getClusteringColumns();

        String orderByDesc = orderBy(clusteringColumns, "DESC");
        String orderByAsc = orderBy(clusteringColumns, "ASC");

        String indexColumn = clusteringColumns.get(clusteringColumns.size() - 1).getName();
        indexAccessor = CassandraUtil.findProperty(dataClass, ckeyClass, indexColumn);

        selectUnbounded = prepare(String.format("select * from %s.%s where " + pkEq + " order by %s limit ?", keyspace, tableName, orderByDesc));
        selectBefore = prepare(String.format("select * from %s.%s where "+pkEq+" and %s < ? order by %s limit ?", keyspace, tableName, indexColumn, orderByDesc));
        selectAfter = prepare(String.format("select * from %s.%s where "+pkEq+" and %s > ? order by %s limit ?", keyspace, tableName, indexColumn, orderByDesc));
        selectBeforeAfter = prepare(String.format("select * from %s.%s where "+pkEq+" and %s < ? and %s > ? order by %s limit ?", keyspace, tableName, indexColumn, indexColumn, orderByDesc));
        
        selectUnboundedAsc = prepare(String.format("select * from %s.%s where "+pkEq+" order by %s limit ?", keyspace, tableName, orderByAsc));
        selectBeforeAsc = prepare(String.format("select * from %s.%s where "+pkEq+" and %s < ? order by %s limit ?", keyspace, tableName, indexColumn, orderByAsc));
        selectAfterAsc = prepare(String.format("select * from %s.%s where "+pkEq+" and %s > ? order by %s limit ?", keyspace, tableName, indexColumn, orderByAsc));
        selectBeforeAfterAsc = prepare(String.format("select * from %s.%s where "+pkEq+" and %s < ? and %s > ? order by %s limit ?", keyspace, tableName, indexColumn, indexColumn, orderByAsc));
        
        selectByIdKey = prepare(String.format("select * from %s.%s where "+pkEq+" and %s=?", keyspace, tableName, indexColumn));
        deleteByIdKey = prepare(String.format("delete from %s.%s where "+pkEq+" and %s=?", keyspace, tableName, indexColumn));
        selectAllById = prepare(String.format("select * from %s.%s where " + pkEq, keyspace, tableName));
        deleteAllById = prepare(String.format("delete from %s.%s where "+pkEq, keyspace, tableName));
    }

    private String orderBy(List<ColumnMetadata> clusteringColumns, final String order) {
        return clusteringColumns.stream()
                .map(c -> c.getName() + " " + order)
                .collect(Collectors.joining(", "));
    }

    private <T> List<T> exceptLast(List<T> list) {
        return list.subList(0, list.size() - 1);
    }

    private PreparedStatement prepare(String query) {
        try {
            return session.prepare(query);
        } catch (Exception e) {
            throw new RuntimeException("Invalid query: " + query, e); 
        }
    }                                                                                                                           

    public Page<Data> selectPage(Id id, CKey before, CKey after, int limit) {
        if (Page.isStartsAtAfter(before, after)) {
            return selectAfter(id, after, limit);
        } else {
            return selectBefore(id, before, limit);
        }
    }

    public Page<Data> selectBefore(Id id, CKey before, int limit) {
        return selectLast(id, before, null, limit);
    }
    
    public Page<Data> selectAfter(Id id, CKey after, int limit) {
        return selectFirst(id, null, after, limit);
    }
    
    public Page<Data> selectLast(Id id, CKey before, CKey after, int limit) {
        if (before == null && after == null) {
            return page(execute(selectUnbounded, false, id, limit), before, after, limit);
        }
        if (before != null && after == null) {
            return page(execute(selectBefore, false, id, before, limit), before, after, limit);
        }
        if (before == null) {
            return page(execute(selectAfter, false, id, after, limit), before, after, limit);
        }
        return page(execute(selectBeforeAfter, false, id, before, after, limit), before, after, limit);
    }

    public Page<Data> selectFirst(Id id, CKey before, CKey after, int limit) {
        if (before == null && after == null) {
            return page(execute(selectUnboundedAsc, true, id, limit), before, after, limit);
        }
        if (before != null && after == null) {
            return page(execute(selectBeforeAsc, true, id, before, limit), before, after, limit);
        }
        if (before == null) {
            return page(execute(selectAfterAsc, true, id, after, limit), before, after, limit);
        }
        return page(execute(selectBeforeAfterAsc, true, id, before, after, limit), before, after, limit);
    }

    public Optional<Data> selectByIdKey(Id id, CKey ckey) {
        BoundStatement boundStatement = bind(selectByIdKey, id, ckey);
        return Optional.ofNullable(mapper.map(session.execute(boundStatement)).one());
    }

    public void deleteByIdKey(Id id, CKey ckey) {
        session.execute(deleteByIdKeyQuery(id, ckey));
    }

    public Statement deleteByIdKeyQuery(Id id, CKey ckey) {
        return bind(deleteByIdKey,id, ckey);
    }

    public Result<Data> selectAllById(Id id) {
        return mapper.map(session.execute(bind(selectAllById, id)));
    }

    public void deleteAllById(Id id) {
        session.execute(bind(deleteAllById, id));
    }

    public void deleteByIdKeyAsync(Id id, Object ckey) {
        session.executeAsync(deleteByIdKeyQuery(id, (CKey) ckey));
    }

    private Page<Data> page(List<Data> list, CKey before, CKey after, int limit) {
        return list.isEmpty() ? Page.empty(before, after, null, limit) : Page.from(list, before, after, limit, indexAccessor);
    }
    private List<Data> execute(PreparedStatement statement, boolean reverse, Object... parameters) {
        BoundStatement boundStatement = bind(statement, parameters);
        ResultSet resultSet = session.execute(boundStatement);
        List<Data> data = mapper.map(resultSet).all();
        if (!data.isEmpty() && reverse) {
            data = Lists.reverse(data);
        }
        return data;
    }

    private BoundStatement bind(PreparedStatement statement, Object... parameters) {
        return statement.bind(CassandraUtil.keysToArray(parameters));
    }
}
