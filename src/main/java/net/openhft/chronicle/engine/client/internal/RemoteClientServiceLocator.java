/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.engine.client.internal;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.IORuntimeException;
import net.openhft.chronicle.core.MemoryUnit;
import net.openhft.chronicle.engine.Chassis;
import net.openhft.chronicle.map.ChronicleMap;

import net.openhft.chronicle.network.connection.TcpConnectionHub;
import net.openhft.chronicle.wire.Wire;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by Rob Austin
 */
public class RemoteClientServiceLocator {

    @NotNull
    private final TcpConnectionHub hub;

    public RemoteClientServiceLocator(@NotNull String hostname,
                                      int port,
                                      byte identifier,
                                      @NotNull Function<Bytes, Wire> byteToWire) throws IOException {

        final InetSocketAddress inetSocketAddress = new InetSocketAddress(hostname, port);
        int tcpBufferSize = (int) MemoryUnit.MEGABYTES.toBytes(2) + 1024;
        long timeoutMs = TimeUnit.SECONDS.toMillis(20);

        hub = new TcpConnectionHub(identifier,
                false,
                inetSocketAddress,
                tcpBufferSize,
                timeoutMs,
                byteToWire);
    }

    @NotNull
    public <I> I getService(@NotNull Class<I> iClass, String name, Class... args) {
        try {

            if (ChronicleMap.class.isAssignableFrom(iClass)) {
                final Class kClass = args[0];
                final Class vClass = args[1];
                return (I) mapInstance(kClass, vClass, name);

            }

        } catch (IOException e) {
            throw new IORuntimeException(e);
        }

        throw new IllegalStateException("iClass=" + iClass + " not supported");
    }

    private < KI, VI> ConcurrentMap<KI, VI> mapInstance(Class<KI> kClass, Class<VI> vClass, String name)
            throws IOException {
        return Chassis.acquireMap(name,kClass,vClass);
    }

    public void close() {
        hub.close();
    }
}
