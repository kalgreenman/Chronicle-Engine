package net.openhft.chronicle.engine.api;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.engine.api.collection.ValuesCollection;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.set.EntrySetView;
import net.openhft.chronicle.engine.api.set.KeySetView;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by peter on 24/05/15.
 */
public class RequestContext {
    private String pathName;
    private String name;
    private Class viewType, type, type2;
    private String basePath;
    private Function<Bytes, Wire> wireType = TextWire::new;
    @Nullable
    private Boolean putReturnsNull = null,
            removeReturnsNull = null,
            bootstrap = null;
    private double averageValueSize;
    private long entries;
    private int tcpBufferSize = 1024;

    private static final Map<String, Class> classAliases = new ConcurrentHashMap<>();
    private int port;
    @NotNull
    private StringBuilder host = new StringBuilder();
    private long timeout = 1000; // in ms

    private static void addAlias(Class type, @NotNull String aliases) {
        for (String alias : aliases.split(", ?")) {
            classAliases.put(alias, type);
            classAliases.put(Character.toLowerCase(alias.charAt(0)) + alias.substring(1), type);
            classAliases.put(alias.toLowerCase(), type);
        }
    }

    static {
        addAlias(MapView.class, "Map");
        addAlias(EntrySetView.class, "EntrySet");
        addAlias(KeySetView.class, "KeySet");
        addAlias(ValuesCollection.class, "Values");
        addAlias(Set.class, "Set");
        addAlias(Publisher.class, "Publisher, Pub");
        addAlias(TopicPublisher.class, "TopicPublisher, TopicPub");
        addAlias(Reference.class, "Reference, Ref");
        addAlias(String.class, "String");
        addAlias(CharSequence.class, "CharSequence");
        addAlias(Bytes.class, "Byte, int8");
        addAlias(Character.class, "Character, Char");
        addAlias(Integer.class, "Integer, int32");
        addAlias(Long.class, "Long, Int, int64");
        addAlias(Float.class, "Float, Float32");
        addAlias(Double.class, "Double, Float64");
    }

    private RequestContext() {
    }

    public RequestContext(String pathName, String name) {
        this.pathName = pathName;
        this.name = name;
    }

    @NotNull
    public static RequestContext requestContext() {
        return new RequestContext();
    }

    // todo improve this !
    @NotNull
    public static RequestContext requestContext(@NotNull CharSequence uri) {
        return requestContext(uri.toString());
    }

    @NotNull
    public static RequestContext requestContext(@NotNull String uri) {

        int queryPos = uri.indexOf('?');
        String fullName = queryPos >= 0 ? uri.substring(0, queryPos) : uri;
        String query = queryPos >= 0 ? uri.substring(queryPos + 1) : "";
        int lastForwardSlash = fullName.lastIndexOf('/');
        if(lastForwardSlash >0 && fullName.length()==lastForwardSlash+1){
            fullName=fullName.substring(0, fullName.length()-1);
            lastForwardSlash = fullName.lastIndexOf('/');
        }
        String pathName = lastForwardSlash >= 0 ? fullName.substring(0, lastForwardSlash) : "";
        String name = lastForwardSlash >= 0 ? fullName.substring(lastForwardSlash + 1) : fullName;
        return new RequestContext(pathName, name).queryString(query);
    }

    @NotNull
    public RequestContext queryString(@NotNull String queryString) {
        if (queryString.isEmpty())
            return this;
        WireParser parser = new VanillaWireParser();
        parser.register(() -> "view", v -> v.text((Consumer<String>) this::view));
        parser.register(() -> "bootstrap", v -> v.bool(b -> this.bootstrap = b));
        parser.register(() -> "putReturnsNull", v -> v.bool(b -> this.putReturnsNull = b));
        parser.register(() -> "removeReturnsNull", v -> v.bool(b -> this.removeReturnsNull = b));
        parser.register(() -> "basePath", v -> v.text((Consumer<String>) x -> this.basePath = x));
        parser.register(() -> "viewType", v -> v.typeLiteral(this::lookupType, x -> this.viewType = x));
        parser.register(() -> "keyType", v -> v.typeLiteral(this::lookupType, x -> this.type = x));
        parser.register(() -> "valueType", v -> v.typeLiteral(this::lookupType, x -> this.type2 = x));
        parser.register(() -> "elementType", v -> v.typeLiteral(this::lookupType, x -> this.type = x));
        parser.register(() -> "port", v -> v.int32(x -> this.port = x));
        parser.register(() -> "host", v -> v.textTo(this.host));
        parser.register(() -> "timeout", v -> v.int32(x -> this.timeout = x));
        parser.register(WireParser.DEFAULT, ValueIn.DISCARD);
        Bytes bytes = Bytes.from(queryString);
        QueryWire wire = new QueryWire(bytes);
        while (bytes.remaining() > 0)
            parser.parse(wire);
        return this;
    }

    @NotNull
    RequestContext view(@NotNull String viewName) {
        try {
            Class clazz = lookupType(viewName);
            viewType(clazz);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Unknown view name:" + viewName);
        }
        return this;
    }

    Class lookupType(@NotNull CharSequence typeName) throws IllegalArgumentException {
        String typeNameStr = typeName.toString();
        Class clazz = classAliases.get(typeNameStr);
        if (clazz != null)
            return clazz;
        try {
            clazz = Class.forName(typeNameStr);
            classAliases.put(typeNameStr, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown class name:" + typeNameStr);
        }
    }

    @NotNull
    public RequestContext type(Class type) {
        this.type = type;
        return this;
    }

    @NotNull
    public RequestContext keyType(Class type) {
        this.type = type;
        return this;
    }

    public Class type() {
        return type;
    }

    public Class elementType() {
        return type;
    }

    public Class keyType() {
        return type;
    }

    public Class valueType() {
        return type2;
    }

    @NotNull
    public RequestContext valueType(Class type2) {
        this.type2 = type2;
        return this;
    }

    @NotNull
    public RequestContext type2(Class type2) {
        this.type2 = type2;
        return this;
    }

    public Class type2() {
        return type2;
    }

    @NotNull
    public String fullName() {
        return pathName.isEmpty() ? name : (pathName + "/" + name);
    }

    @NotNull
    public RequestContext basePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    public String basePath() {
        return basePath;
    }

    @NotNull
    public RequestContext wireType(Function<Bytes, Wire> writeType) {
        this.wireType = writeType;
        return this;
    }

    public Function<Bytes, Wire> wireType() {
        return wireType;
    }

    public String namePath() {
        return pathName;
    }

    public String name() {
        return name;
    }

    public double getAverageValueSize() {
        return averageValueSize;
    }

    @NotNull
    public RequestContext averageValueSize(double averageValueSize) {
        this.averageValueSize = averageValueSize;
        return this;
    }

    public long getEntries() {
        return entries;
    }

    @NotNull
    public RequestContext entries(long entries) {
        this.entries = entries;
        return this;
    }

    @NotNull
    public RequestContext name(String name) {
        this.name = name;
        return this;
    }

    @NotNull
    public RequestContext viewType(Class assetType) {
        this.viewType = assetType;
        return this;
    }


    public Class viewType() {
        return viewType;
    }

    @NotNull
    public RequestContext fullName(@NotNull String fullName) {
        int dirPos = fullName.lastIndexOf('/');
        this.pathName = dirPos >= 0 ? fullName.substring(0, dirPos) : "";
        this.name = dirPos >= 0 ? fullName.substring(dirPos + 1) : fullName;
        return this;
    }

    @Nullable
    public Boolean putReturnsNull() {
        return putReturnsNull;
    }

    @Nullable
    public Boolean removeReturnsNull() {
        return removeReturnsNull;
    }

    @Nullable
    public Boolean bootstrap() {
        return bootstrap;
    }

    @NotNull
    public RequestContext bootstrap(boolean bootstrap) {
        this.bootstrap = bootstrap;
        return this;
    }

    @NotNull
    @Override
    public String toString() {
        return "RequestContext{" +
                "pathName='" + pathName + '\'' +
                ", name='" + name + '\'' +
                ", viewType=" + viewType +
                ", type=" + type +
                ", type2=" + type2 +
                ", basePath='" + basePath + '\'' +
                ", wireType=" + wireType +
                ", putReturnsNull=" + putReturnsNull +
                ", removeReturnsNull=" + removeReturnsNull +
                ", bootstrap=" + bootstrap +
                ", port=" + port +
                ", host=" + host +
                ", timeout=" + timeout +
                '}';
    }

    public int port() {
        return port;
    }

    @NotNull
    public String host() {
        return host.toString();
    }

    public boolean doHandShaking() {
        return false;
    }

    public int tcpBufferSize() {
        return tcpBufferSize;
    }

    public long timeout() {
        return timeout;
    }
}
