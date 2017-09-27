package papyrus.channel.node.server.persistence;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.MaterializedViewMetadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.datastax.driver.mapping.annotations.Column;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;

public class CassandraUtil {
    private static final int MAX_RETRY = 5;
    private static final Logger log = LoggerFactory.getLogger(CassandraUtil.class);
    
    private CassandraUtil() {
    }

    public static Object getSingleValue(ResultSet resultSet) {
        Row one = resultSet.one();
        return one != null ? one.getObject(0) : null;
    }
    
    public static <T, P> Function<T, P> findProperty(Class<T> dataClass, Class<P> propertyClass, String columnName) {
        for (Method method : dataClass.getMethods()) {
            String name = getColumnName(method);
            if (columnName.equals(name)) {
                return new PropertyMethodAccessor<>(method, propertyClass);
            }
        }
        for (Class<? super T> superClass = dataClass; superClass != Object.class; superClass = superClass.getSuperclass()) {
            for (Field field : superClass.getDeclaredFields()) {
                String name = getColumnName(field);
                if (columnName.equals(name)) {
                    return new PropertyFieldAccessor<>(field, propertyClass);
                }
            }
        }
        throw new IllegalArgumentException("Mapped property for column '" + columnName + "' not found in " + dataClass);
    }

    private static <F extends AnnotatedElement & Member> String getColumnName(F element) {
        if (element.isAnnotationPresent(Column.class)) {
            String name = element.getAnnotation(Column.class).name();
            if (!name.isEmpty()) return name;
        }
        if (element instanceof Method) {
            if (element.getName().startsWith("get")) {
                return element.getName().substring(3);
            }
            if (element.getName().startsWith("is")) {
                return element.getName().substring(2);
            }
        }
        if (element instanceof Field) {
            return element.getName();
        }
        return null; 
    }

    public static Object[] keysToArray(Object... keys) {
        Object[] array = new Object[16];
        int length = writeTo(array, 0, keys);
        return Arrays.copyOf(array, length);
    }
    
    public static boolean untilApplied(Session session, BatchStatement.Type type, Consumer<BatchStatement> transaction) {
        for (int i = 1; i <= MAX_RETRY; i ++) {
            BatchStatement batchStatement = new BatchStatement(type);
            transaction.accept(batchStatement);
            if (batchStatement.size() == 0) return false;
            boolean applied;
            if (batchStatement.size() > 1) {
                applied = session.execute(batchStatement).wasApplied();
            } else {
                Statement statement = Iterables.getOnlyElement(batchStatement.getStatements());
                applied = session.execute(statement).wasApplied();
            }
            if (applied) return true;
            log.warn("Attempt {}/{} failed executing {}", i, MAX_RETRY, batchStatement);
            try {
                Thread.sleep(100 * i);
            } catch (InterruptedException e) {
                throw new AttemptsFailedException(e);
            }
        }
        throw new AttemptsFailedException();
    }

    public static ResultSet batch(Session session, BatchStatement.Type type, Consumer<BatchStatement> transaction) {
        BatchStatement batchStatement = new BatchStatement(type);
        transaction.accept(batchStatement);
        if (batchStatement.size() == 0) return null;
        if (batchStatement.size() > 1) {
            return session.execute(batchStatement);
        } else {
            Statement statement = Iterables.getOnlyElement(batchStatement.getStatements());
            return session.execute(statement);
        }
    }

    private static int writeTo(Object[] array, int i, Object id) {
        if (id instanceof Pair) {
            Pair pair = (Pair) id;
            i = writeTo(array, i, pair.getLeft());
            return writeTo(array, i, pair.getRight());
        } else if (id instanceof Object[]) {
            Object[] pair = (Object[]) id;
            for (Object o : pair) {
                i = writeTo(array, i, o);
            }
            return i;
        } else {
            array[i] = id;
            return i + 1;
        }
    }

    static void executeWithLog(Session session, String statement) {
        try {
            log.info("Executing: {}", statement);
            session.execute(statement);
        } catch (Exception e) {
            log.error("Failed to execute schema statement:\n{}", statement);
            throw new RuntimeException(e);
        }
    }

    static void dropSchema(Session session) {
        for (KeyspaceMetadata keyspaceMetadata : session.getCluster().getMetadata().getKeyspaces()) {
            String keyspace = keyspaceMetadata.getName();
            if (keyspace.startsWith("system")) {
                continue;
            }
            for (MaterializedViewMetadata metadata : keyspaceMetadata.getMaterializedViews()) {
                executeWithLog(session, "DROP MATERIALIZED VIEW " + keyspace + "." + metadata.getName());
            }
            for (TableMetadata metadata : keyspaceMetadata.getTables()) {
                executeWithLog(session, "DROP TABLE " + keyspace + "." + metadata.getName());
            }
        }
    }

    public static void executeSchemaCql(Session session, boolean deleteData) {
        try {
            
            URL schema = Resources.getResource("schema.cql");
            String schemaCql = Resources.toString(schema, Charset.forName("UTF-8"));
            schemaCql = schemaCql.replaceAll("(?m)//.*$", "");
            String[] statements = schemaCql.split(";");

            if (deleteData) {
                dropSchema(session);
            }

            for (String statement : statements) {
                statement = statement.trim();
                if (statement.isEmpty()) continue;
                executeWithLog(session, statement);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class PropertyFieldAccessor<T, P> implements Function<T, P> {
        final Field field;
        final Class<P> type;

        public PropertyFieldAccessor(Field field, Class<P> type) {
            this.type = type;
            if (!ClassUtils.isAssignable(type, field.getType())) {
                throw new IllegalArgumentException("Incompatible types: " + type.getName() + " is not assignable from " + field); 
            }
            this.field = field;
            this.field.setAccessible(true);
        }

        @Override
        public P apply(T data) {
            try {
                return type.cast(field.get(data));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    } 
    
    private static class PropertyMethodAccessor<T,P> implements Function<T,P> {
        final Method method;
        final Class<P> type;

        PropertyMethodAccessor(Method method, Class<P> type) {
            this.type = type;
            if (ClassUtils.isAssignable(type, method.getReturnType())) {
                throw new IllegalArgumentException("Incompatible types: " + type.getName() + " is not assignable from return type of " + method);
            }
            this.method = method;
            this.method.setAccessible(true);
        }

        @Override
        public P apply(T data) {
            try {
                return type.cast(method.invoke(data));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw Throwables.propagate(e.getCause());
            }
        }
    }

    public static <T> T get(Future<T> future) {
        try {
            return Uninterruptibles.getUninterruptibly(future);
        } catch (ExecutionException e) {
            throw propagateCause(e);
        }
    }

    static RuntimeException propagateCause(ExecutionException e) {
        Throwable cause = e.getCause();

        if (cause instanceof Error)
            throw ((Error) cause);

        // We could just rethrow e.getCause(). However, the cause of the ExecutionException has likely been
        // created on the I/O thread receiving the response. Which means that the stacktrace associated
        // with said cause will make no mention of the current thread. This is painful for say, finding
        // out which execute() statement actually raised the exception. So instead, we re-create the
        // exception.
        if (cause instanceof DriverException)
            throw ((DriverException) cause).copy();
        else
            throw new DriverInternalError("Unexpected exception thrown", cause);
    }
}
