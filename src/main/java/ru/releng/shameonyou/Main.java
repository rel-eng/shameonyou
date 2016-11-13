/*
 * Copyright (c) 2016 rel-eng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.releng.shameonyou;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.releng.shameonyou.core.LifeCycle;
import ru.releng.shameonyou.log.CustomShutdownCallbackRegistry;

import java.util.Set;

public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    private static final Object shutdownLock = new Object();
    private static volatile boolean shutdown = false;

    public static void main(String[] args) {
        try {
            registerShutdownHook();
            LOG.info("Starting up...");
            Injector injector = Guice.createInjector(new CoreModule());
            try {
                start(injector);
                LOG.info("Startup complete");
                synchronized (shutdownLock) {
                    while (!shutdown) {
                        try {
                            shutdownLock.wait(100);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                }
            } finally {
                LOG.info("Shutting down...");
                stop(injector);
                LOG.info("Shutdown complete");
            }
        } finally {
            CustomShutdownCallbackRegistry.invoke();
        }
    }

    private static void start(Injector injector) {
        injector.getInstance(Key.get(setOf(LifeCycle.class))).forEach(LifeCycle::start);
    }

    private static void stop(Injector injector) {
        injector.getInstance(Key.get(setOf(LifeCycle.class))).forEach(instance -> {
            try {
                instance.stop();
            } catch (Exception e) {
                LOG.error("Failed to shutdown {}", instance, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> TypeLiteral<Set<T>> setOf(Class<T> type) {
        return (TypeLiteral<Set<T>>)TypeLiteral.get(Types.setOf(type));
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->{
            synchronized (shutdownLock) {
                shutdown = true;
                shutdownLock.notifyAll();
            }
            try {
                // Wait for shutdown
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // Do nothing
            }
        }));
    }

}
