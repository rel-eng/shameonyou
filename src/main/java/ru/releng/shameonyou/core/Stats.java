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

import org.HdrHistogram.Histogram;

import java.time.Instant;

public class Stats {

    private final Instant timestamp;
    private final Histogram responseTimeHistogram;
    private final long totalErrors;
    private final Histogram queueLengthHistogram;
    private final double successesPercent;
    private final double uptimePercent;

    public Stats(Instant timestamp, Histogram responseTimeHistogram, long totalErrors, Histogram queueLengthHistogram,
                 double successesPercent, double uptimePercent)
    {
        this.timestamp = timestamp;
        this.responseTimeHistogram = responseTimeHistogram;
        this.totalErrors = totalErrors;
        this.queueLengthHistogram = queueLengthHistogram;
        this.successesPercent = successesPercent;
        this.uptimePercent = uptimePercent;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Histogram getResponseTimeHistogram() {
        return responseTimeHistogram;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public Histogram getQueueLengthHistogram() {
        return queueLengthHistogram;
    }

    public double getSuccessesPercent() {
        return successesPercent;
    }

    public double getUptimePercent() {
        return uptimePercent;
    }

}
