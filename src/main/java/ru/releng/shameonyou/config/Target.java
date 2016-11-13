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

package ru.releng.shameonyou.config;

import java.time.Duration;

public class Target {

    private String key;
    private String url;
    private Duration delay;
    private Duration timeout;
    private double successesPercentThreshold;
    private long queueLength95PercentileThreshold;
    private long responseTime95PercentileThreshold;

    public Target() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public double getSuccessesPercentThreshold() {
        return successesPercentThreshold;
    }

    public void setSuccessesPercentThreshold(double successesPercentThreshold) {
        this.successesPercentThreshold = successesPercentThreshold;
    }

    public long getQueueLength95PercentileThreshold() {
        return queueLength95PercentileThreshold;
    }

    public void setQueueLength95PercentileThreshold(long queueLength95PercentileThreshold) {
        this.queueLength95PercentileThreshold = queueLength95PercentileThreshold;
    }

    public long getResponseTime95PercentileThreshold() {
        return responseTime95PercentileThreshold;
    }

    public void setResponseTime95PercentileThreshold(long responseTime95PercentileThreshold) {
        this.responseTime95PercentileThreshold = responseTime95PercentileThreshold;
    }

    @Override
    public String toString() {
        return "Target{" +
                "key='" + key + '\'' +
                ", url='" + url + '\'' +
                ", delay=" + delay +
                ", timeout=" + timeout +
                ", successesPercentThreshold=" + successesPercentThreshold +
                ", queueLength95PercentileThreshold=" + queueLength95PercentileThreshold +
                ", responseTime95PercentileThreshold=" + responseTime95PercentileThreshold +
                '}';
    }

}
