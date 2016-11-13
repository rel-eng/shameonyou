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

package ru.releng.shameonyou.core;

import com.github.racc.tscg.TypesafeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.releng.shameonyou.config.Target;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Singleton
public class SamplerService implements LifeCycle {

    private static final Logger LOG = LogManager.getLogger(SamplerService.class);

    private final List<Target> targets;
    private final ScheduledExecutorService samplerScheduledExecutorService;
    private final List<Sampler> samplers;
    private final List<ScheduledFuture<?>> samplerScheduledFutures;
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);

    @Inject
    public SamplerService(@TypesafeConfig("targets") List<Target> targets) {
        this.targets = targets;
        this.samplerScheduledExecutorService = Executors.newScheduledThreadPool(Math.min(targets.size(), 16));
        this.samplers = new ArrayList<>();
        this.samplerScheduledFutures = new ArrayList<>();
    }

    public Map<String, Stats> getStats() {
        return this.samplers.stream().collect(Collectors.toMap(Sampler::getKey, Sampler::getStats));
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INITIALIZED, State.STARTING)) {
            return;
        }
        LOG.info("Starting sampler service...");
        try {
            targets.forEach(target -> {
                Sampler sampler = new Sampler(target);
                samplers.add(sampler);
                sampler.start();
                samplerScheduledFutures.add(samplerScheduledExecutorService.scheduleWithFixedDelay(sampler, 0,
                        target.getDelay().toMillis(), TimeUnit.MILLISECONDS));
            });
            state.set(State.STARTED);
            LOG.info("Sampler service started");
        } finally {
            state.compareAndSet(State.STARTING, State.STOPPED);
        }
    }

    @Override
    public void stop() {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }
        LOG.info("Stopping sampler service...");
        try {
            samplers.forEach(Sampler::stop);
            samplerScheduledFutures.forEach(future -> future.cancel(true));
            samplerScheduledExecutorService.shutdown();
            samplerScheduledExecutorService.awaitTermination(500, TimeUnit.MILLISECONDS);
            samplerScheduledExecutorService.shutdownNow();
            LOG.info("Sampler service stopped");
        } catch (InterruptedException e) {
            // Ignore
        } finally {
            state.set(State.STOPPED);
        }
    }

    @Override
    public String toString() {
        return "SamplerService{" +
                "samplers=" + samplers +
                ", state=" + state +
                '}';
    }

}
