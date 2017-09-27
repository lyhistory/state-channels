package papyrus.channel.node.server.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import papyrus.channel.node.entity.DataObject;

public class Page<Data> extends DataObject {
    private final List<Data> data;
    private final Object before;
    private final Object after;
    private final int limit;
    private final boolean hasBefore;
    private final boolean hasAfter;

    public Page(List<Data> data, Object before, Object after, int limit, boolean hasBefore, boolean hasAfter) {
        this.data = data.isEmpty() ? Collections.emptyList() : Lists.newArrayList(data);
        this.before = before;
        this.after = after;
        this.limit = limit;
        this.hasBefore = hasBefore;
        this.hasAfter = hasAfter;
    }

    /**
     * Returns whether limit should be applied starting at 'after' cursor.
     */
    public static <CKey> boolean isStartsAtAfter(CKey before, CKey after) {
        return before == null && after != null;
    }

    public List<Data> getData() {
        return data;
    }

    public Object getBefore() {
        return before;
    }

    public Object getAfter() {
        return after;
    }

    @JsonProperty("has_before")
    public boolean isHasBefore() {
        return hasBefore;
    }

    @JsonProperty("has_after")
    public boolean isHasAfter() {
        return hasAfter;
    }

    public int getLimit() {
        return limit;
    }

    public static <T,C> Page<T> from(List<T> data, C requestedBefore, C requestedAfter, int requestedLimit, Function<T, C> cursorFunction) {
        boolean startsAtAfter = isStartsAtAfter(requestedBefore, requestedAfter);
        boolean hasBefore = startsAtAfter || data.size() == requestedLimit;
        boolean hasAfter = startsAtAfter && data.size() == requestedLimit || requestedBefore != null;
        return new Page<>(
            data,
            data.isEmpty() ? requestedBefore : cursorFunction.apply(data.get(data.size() - 1)),
            data.isEmpty() ? requestedAfter : cursorFunction.apply(data.get(0)),
            requestedLimit, hasBefore, hasAfter);
    }

    public static <T, Key extends Comparable<? super Key>> Page<T> selectPage(List<T> data, Key before, Key after, int limit, Function<T, Key> timestampFunction) {
        List<T> filtered = new ArrayList<>(Math.min(limit, data.size()));
        for (T item : data) {
            Key timestamp = timestampFunction.apply(item);
            if (before == null || timestamp.compareTo(before) < 0) {
                if (after == null || timestamp.compareTo(after) > 0) {
                    filtered.add(item);
                }
            }
        }

        if (filtered.isEmpty()) {
            return empty(before, after, null, limit);
        }

        filtered.sort(Ordering.natural().reverse().onResultOf(timestampFunction::apply));
        if (filtered.size() > limit) {
            filtered = before == null && after != null ? filtered.subList(filtered.size() - limit, filtered.size()) : filtered.subList(0, limit);
        }
        
        return from(filtered, before, after, limit, timestampFunction);
    }

    public <T> Page<T> transform(Function<Data, T> function) {
        if (data.isEmpty()) {
            //noinspection unchecked
            return (Page<T>) this;
        }
        List<T> newData = Lists.transform(data, function::apply);
        return withData(newData);
    }

    public <T> Page<T> withData(List<T> newData) {
        return new Page<>(newData, before, after, limit, hasBefore, hasAfter);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Page)) return false;
        if (!super.equals(o)) return false;

        Page<?> page = (Page<?>) o;

        if (!data.equals(page.data)) return false;
        if (before != null ? !before.equals(page.before) : page.before != null) return false;
        return after != null ? after.equals(page.after) : page.after == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + data.hashCode();
        result = 31 * result + (before != null ? before.hashCode() : 0);
        result = 31 * result + (after != null ? after.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return data +
            ", before:" + before +
            ", after:" + after;
    }

    public static <T,C> Page<T> empty(C requestedBefore, C requestedAfter, C cursor, int limit) {
        boolean startsAtAfter = isStartsAtAfter(requestedBefore, requestedAfter);
        C before = cursor != null ? cursor : requestedBefore;
        C after = cursor != null ? cursor : requestedAfter;
        return new Page<>(Collections.emptyList(), before, after, limit, startsAtAfter, startsAtAfter || requestedBefore != null);
    }
}
