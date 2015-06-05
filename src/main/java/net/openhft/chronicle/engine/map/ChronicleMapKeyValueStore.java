package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.bytes.IORuntimeException;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.api.Asset;
import net.openhft.chronicle.engine.api.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.RequestContext;
import net.openhft.chronicle.engine.api.SubscriptionConsumer;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapReplicationEvent;
import net.openhft.chronicle.engine.api.map.SubscriptionKeyValueStore;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import net.openhft.chronicle.map.MapEventListener;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static net.openhft.chronicle.engine.api.SubscriptionConsumer.notifyEachEvent;

/**
 * Created by daniel on 27/05/15.
 */
public class ChronicleMapKeyValueStore<K, MV, V> implements SubscriptionKeyValueStore<K, MV, V>, Closeable {
    private final ChronicleMap<K,V> chronicleMap;
    private final SubscriptionKVSCollection<K, V> subscriptions = new VanillaSubscriptionKVSCollection<>(this);
    private Asset asset;

    public ChronicleMapKeyValueStore(RequestContext context, Asset asset) {
        PublishingOperations publishingOperations = new PublishingOperations();

        Class kClass = context.type();
        Class vClass = context.type2();

        String basePath = context.basePath();

        ChronicleMapBuilder builder = ChronicleMapBuilder.of(kClass, vClass)
                .eventListener(publishingOperations);

        if (context.putReturnsNull() != Boolean.FALSE) {
            builder.putReturnsNull(true);
        }
        if(context.getAverageValueSize()!=0){
            builder.averageValueSize(context.getAverageValueSize());
        }
        if(context.getEntries()!=0){
            builder.entries(context.getEntries());
        }
        if(basePath!=null) {
            String pathname = basePath + "/" + context.name();
            try {
                builder.createPersistedTo(new File(pathname));
            } catch (IOException e) {
                IORuntimeException iore = new IORuntimeException("Could not access " + pathname);
                iore.initCause(e);
                throw iore;
            }
        }

        chronicleMap = builder.create();
    }

    @Override
    public SubscriptionKVSCollection<K, V> subscription(boolean createIfAbsent) {
        return subscriptions;
    }

    @Override
    public V getAndPut(K key, V value) {
        return chronicleMap.put(key, value);
    }

    @Override
    public V getAndRemove(K key) {
        return chronicleMap.remove(key);
    }


    @Override
    public V getUsing(K key, MV value) {
        if(value != null)throw new UnsupportedOperationException("Mutable values not supported");
        return chronicleMap.getUsing(key, (V) value);
    }

    @Override
    public long size() {
        return chronicleMap.size();
    }

    @Override
    public void keysFor(int segment, SubscriptionConsumer<K> kConsumer) throws InvalidSubscriberException {
        //Ignore the segments and return keysFor the whole map
        notifyEachEvent(chronicleMap.keySet(), kConsumer);
    }

    @Override
    public void entriesFor(int segment, SubscriptionConsumer<MapReplicationEvent<K, V>> kvConsumer) throws InvalidSubscriberException {
        //Ignore the segments and return entriesFor the whole map
        chronicleMap.entrySet().stream().map(e -> EntryEvent.of(e.getKey(), e.getValue(), 0, 0L)).forEach(e -> {
            try {
                kvConsumer.accept(e);
            } catch (InvalidSubscriberException t) {
                throw Jvm.rethrow(t);
            }
        });
    }

    @Override
    public Iterator<Map.Entry<K, V>> entrySetIterator() {
        return chronicleMap.entrySet().iterator();
    }

    @Override
    public void clear() {
        chronicleMap.clear();
    }

    @Override
    public Asset asset() {
        return asset;
    }

    @Override
    public KeyValueStore<K, MV, V> underlying() {
        return null;
    }

    @Override
    public void close() {
        chronicleMap.close();
    }

    class PublishingOperations extends MapEventListener<K,V> {
        @Override
        public void onRemove(K key, V value, boolean replicationEven) {
            try {
                int identifier = 0; // todo
                long timeStampMS = 0; // todo
                subscriptions.notifyEvent(RemovedEvent.of(key, value, identifier, timeStampMS));
            } catch (InvalidSubscriberException e) {
                // todo
                throw new AssertionError(e);
            }
        }
        @Override
        public void onPut(K key, V newValue, @Nullable V replacedValue, boolean replicationEvent) {
            try {
                int identifier = 0; // todo
                long timeStampMS = 0; // todo
                if(replacedValue!=null) {
                    subscriptions.notifyEvent(UpdatedEvent.of(key, replacedValue, newValue, identifier, timeStampMS));
                }else{
                    subscriptions.notifyEvent(InsertedEvent.of(key, newValue, identifier, timeStampMS));
                }
            } catch (InvalidSubscriberException e) {
                // todo
                throw new AssertionError(e);
            }
        }
    }
}
