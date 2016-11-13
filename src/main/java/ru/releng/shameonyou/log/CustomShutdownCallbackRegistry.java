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

package ru.releng.shameonyou.log;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.LifeCycle2;
import org.apache.logging.log4j.core.util.Cancellable;
import org.apache.logging.log4j.core.util.ShutdownCallbackRegistry;
import org.apache.logging.log4j.status.StatusLogger;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CustomShutdownCallbackRegistry implements ShutdownCallbackRegistry, LifeCycle2, Runnable {

    private static final Logger LOGGER = StatusLogger.getLogger();
    private static final Collection<CustomShutdownCallbackRegistry> instances = new CopyOnWriteArrayList<>();

    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);
    private final Collection<Cancellable> hooks = new CopyOnWriteArrayList<>();

    public CustomShutdownCallbackRegistry() {
        instances.add(this);
    }

    public static void invoke() {
        for (final Runnable instance : instances) {
            try {
                instance.run();
            } catch (final Throwable t) {
                LOGGER.error(SHUTDOWN_HOOK_MARKER, "Caught exception executing shutdown hook {}", instance, t);
            }
        }
    }

    @Override
    public void run() {
        if (state.compareAndSet(State.STARTED, State.STOPPING)) {
            for (final Runnable hook : hooks) {
                try {
                    hook.run();
                } catch (final Throwable t) {
                    LOGGER.error(SHUTDOWN_HOOK_MARKER, "Caught exception executing shutdown hook {}", hook, t);
                }
            }
            state.set(State.STOPPED);
        }
    }

    @Override
    public Cancellable addShutdownCallback(Runnable callback) {
        if (isStarted()) {
            final Cancellable receipt = new RegisteredCancellable(callback, hooks);
            hooks.add(receipt);
            return receipt;
        }
        throw new IllegalStateException("Cannot add new shutdown hook as this is not started. Current state: " +
                state.get().name());
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        if (state.compareAndSet(State.STARTED, State.STOPPING)) {
            state.set(State.STOPPED);
        }
        return true;
    }

    @Override
    public State getState() {
        return state.get();
    }

    @Override
    public void initialize() {
    }

    @Override
    public void start() {
        if (state.compareAndSet(State.INITIALIZED, State.STARTING)) {
            state.set(State.STARTED);
        }
    }

    @Override
    public void stop() {
        stop(AbstractLifeCycle.DEFAULT_STOP_TIMEOUT, AbstractLifeCycle.DEFAULT_STOP_TIMEUNIT);
    }

    @Override
    public boolean isStarted() {
        return state.get() == State.STARTED;
    }

    @Override
    public boolean isStopped() {
        return state.get() == State.STOPPED;
    }

    private static class RegisteredCancellable implements Cancellable {
        private final Reference<Runnable> hook;
        private Collection<Cancellable> registered;

        public RegisteredCancellable(final Runnable callback, final Collection<Cancellable> registered) {
            this.registered = registered;
            hook = new SoftReference<>(callback);
        }

        @Override
        public void cancel() {
            hook.clear();
            registered.remove(this);
            registered = null;
        }

        @Override
        public void run() {
            final Runnable runnableHook = this.hook.get();
            if (runnableHook != null) {
                runnableHook.run();
                this.hook.clear();
            }
        }

        @Override
        public String toString() {
            return String.valueOf(hook.get());
        }

    }

}
