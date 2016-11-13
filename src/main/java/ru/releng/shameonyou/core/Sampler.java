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

import com.google.common.base.Stopwatch;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;
import ru.releng.shameonyou.config.Target;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Sampler implements Runnable, LifeCycle {

    private static final long QUEUE_HISTOGRAM_CAP = 1000000;
    private static final Logger LOG = LogManager.getLogger(Sampler.class);

    private final Target target;
    private final AsyncHttpClient asyncHttpClient;
    private final Recorder responseTimeRecorder;
    private final AtomicLong totalErrors;
    private final AtomicLong inFlight;
    private final Recorder queueLengthRecorder;
    private final AtomicLong successes;
    private final AtomicLong errors;
    private final AtomicLong totalDowntime;
    private final AtomicLong totalUptime;
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);

    public Sampler(Target target) {
        this.target = target;
        this.asyncHttpClient = Dsl.asyncHttpClient(Dsl.config()
                .setConnectTimeout((int) target.getTimeout().toMillis())
                .setReadTimeout((int) target.getTimeout().toMillis())
                .setRequestTimeout((int) target.getTimeout().toMillis())
                .setHandshakeTimeout((int) target.getTimeout().toMillis())
                .setShutdownTimeout(1000)
                .setShutdownQuietPeriod(750));
        this.responseTimeRecorder = new Recorder(1, 2 * target.getTimeout().toMillis(), 5);
        this.totalErrors = new AtomicLong(0L);
        this.inFlight = new AtomicLong(0L);
        this.queueLengthRecorder = new Recorder(1, QUEUE_HISTOGRAM_CAP, 5);
        this.successes = new AtomicLong(0L);
        this.errors = new AtomicLong(0L);
        this.totalDowntime = new AtomicLong(0L);
        this.totalUptime = new AtomicLong(0L);
    }

    public String getKey() {
        return target.getKey();
    }

    public Stats getStats() {
        long successes = this.successes.getAndSet(0L);
        long errors = this.errors.getAndSet(0L);
        double successesPercent = successesPercent(successes, errors);
        Histogram responseTimeHistogram = responseTimeRecorder.getIntervalHistogram();
        Histogram queueLengthHistogram = queueLengthRecorder.getIntervalHistogram();
        long downtime;
        long uptime;
        if (successesPercent < target.getSuccessesPercentThreshold()
                || responseTimeHistogram.getValueAtPercentile(95.0d) > target.getResponseTime95PercentileThreshold()
                || queueLengthHistogram.getValueAtPercentile(95.0d) > target.getQueueLength95PercentileThreshold())
        {
            downtime = totalDowntime.incrementAndGet();
            uptime = totalUptime.get();
        } else {
            uptime = totalUptime.incrementAndGet();
            downtime = totalDowntime.get();
        }
        double uptimePercent = successesPercent(uptime, downtime);
        return new Stats(Instant.now(), responseTimeHistogram, totalErrors.get(),
                queueLengthHistogram, successesPercent, uptimePercent);
    }

    @Override
    public void run() {
        if (state.get() == State.STARTED) {
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            boolean preparedSuccessfully = false;
            recordQueueValue(queueLengthRecorder, inFlight.incrementAndGet());
            try {
                asyncHttpClient.prepareGet(target.getUrl()).execute(new AsyncCompletionHandler<Void>() {

                    @Override
                    public Void onCompleted(Response response) throws Exception {
                        long responseTime = responseTimeStopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
                        recordTimeValue(responseTimeRecorder, responseTime);
                        recordQueueValue(queueLengthRecorder, inFlight.decrementAndGet());
                        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                            successes.incrementAndGet();
                        } else {
                            totalErrors.incrementAndGet();
                            errors.incrementAndGet();
                        }
                        LOG.info("Response {} from {} in {} ms", response.getStatusCode(), target.getUrl(), responseTime);
                        return null;
                    }

                    @Override
                    public void onThrowable(Throwable t) {
                        long responseTime = responseTimeStopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
                        recordTimeValue(responseTimeRecorder, responseTime);
                        recordQueueValue(queueLengthRecorder, inFlight.decrementAndGet());
                        totalErrors.incrementAndGet();
                        errors.incrementAndGet();
                        LOG.info("Failure [{}] from {} in {} ms", t.toString(), target.getUrl(), responseTime);
                    }
                });
                preparedSuccessfully = true;
            } finally {
                if (!preparedSuccessfully) {
                    recordQueueValue(queueLengthRecorder, inFlight.decrementAndGet());
                    totalErrors.incrementAndGet();
                    errors.incrementAndGet();
                }
            }
        }
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INITIALIZED, State.STARTING)) {
            return;
        }
        LOG.info("Starting sampler for {}...", target);
        try {
            state.set(State.STARTED);
            LOG.info("Sampler for {} started", target);
        } finally {
            state.compareAndSet(State.STARTING, State.STOPPED);
        }
    }

    @Override
    public void stop() {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }
        LOG.info("Stopping sampler for {}...", target);
        try {
            asyncHttpClient.close();
            LOG.info("Sampler for {} stopped", target);
        } catch (IOException e) {
            LOG.error("Failed to close HTTP client for {}", target, e);
        } finally {
            state.set(State.STOPPED);
        }
    }

    @Override
    public String toString() {
        return "Sampler{" +
                "target=" + target +
                ", state=" + state +
                '}';
    }

    private void recordTimeValue(Recorder recorder, long value) {
        if (value < 0L) {
            recorder.recordValueWithExpectedInterval(0L, target.getDelay().toMillis());
        } else if (value > 2 * target.getTimeout().toMillis()) {
            recorder.recordValueWithExpectedInterval(2 * target.getTimeout().toMillis(), target.getDelay().toMillis());
        } else {
            recorder.recordValueWithExpectedInterval(value, target.getDelay().toMillis());
        }
    }

    private void recordQueueValue(Recorder recorder, long value) {
        if (value < 0L) {
            recorder.recordValue(0L);
        } else if (value > QUEUE_HISTOGRAM_CAP) {
            recorder.recordValue(QUEUE_HISTOGRAM_CAP);
        } else {
            recorder.recordValue(value);
        }
    }

    private double successesPercent(long successes, long errors) {
        if (successes + errors == 0L) {
            return 100.0d;
        }
        return ((double) (successes * 100L)) / ((double)(successes + errors));
    }

}
