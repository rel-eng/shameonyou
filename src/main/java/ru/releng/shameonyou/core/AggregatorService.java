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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class AggregatorService implements LifeCycle {

    private static final Logger LOG = LogManager.getLogger(AggregatorService.class);

    private final ScheduledExecutorService aggregatorScheduledExecutorService;
    private final List<ScheduledFuture<?>> aggregatorScheduledFutures;
    private final Duration aggregationDelay;
    private final SamplerService samplerService;
    private final Set<Aggregator> aggregators;
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);

    @Inject
    public AggregatorService(@TypesafeConfig("aggregation.delay") Duration aggregationDelay,
                             SamplerService samplerService, Set<Aggregator> aggregators)
    {
        this.aggregatorScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.aggregatorScheduledFutures = new ArrayList<>();
        this.aggregationDelay = aggregationDelay;
        this.samplerService = samplerService;
        this.aggregators = aggregators;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INITIALIZED, State.STARTING)) {
            return;
        }
        LOG.info("Starting aggregator service...");
        try {
            aggregatorScheduledFutures.add(aggregatorScheduledExecutorService.scheduleWithFixedDelay(this::aggregate,
                    aggregationDelay.toMillis(), aggregationDelay.toMillis(), TimeUnit.MILLISECONDS));
            state.set(State.STARTED);
            LOG.info("Aggregator service started");
        } finally {
            state.compareAndSet(State.STARTING, State.STOPPED);
        }
    }

    @Override
    public void stop() {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }
        LOG.info("Stopping aggregator service...");
        try {
            aggregatorScheduledFutures.forEach(future -> future.cancel(true));
            aggregatorScheduledExecutorService.shutdown();
            aggregatorScheduledExecutorService.awaitTermination(500, TimeUnit.MILLISECONDS);
            aggregatorScheduledExecutorService.shutdownNow();
            LOG.info("Aggregator service stopped");
        } catch (InterruptedException e) {
            // Ignore
        } finally {
            state.set(State.STOPPED);
        }
    }

    private void aggregate() {
        if (state.get() == State.STARTED) {
            Map<String, Stats> statsMap = samplerService.getStats();
            aggregators.forEach(aggregator -> statsMap
                    .forEach(aggregator::aggregate));
        }
    }

    @Override
    public String toString() {
        return "AggregatorService{" +
                "aggregationDelay=" + aggregationDelay +
                ", aggregators=" + aggregators +
                ", state=" + state +
                '}';
    }

}
