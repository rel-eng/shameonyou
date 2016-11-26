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

package ru.releng.shameonyou.core.graphite;

import com.github.racc.tscg.TypesafeConfig;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.HdrHistogram.Histogram;
import ru.releng.shameonyou.core.Aggregator;
import ru.releng.shameonyou.core.Stats;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class GraphiteAggregator implements Aggregator {

    private final GraphiteClient graphiteClient;
    private final String graphitePrefix;

    @Inject
    public GraphiteAggregator(GraphiteClient graphiteClient, @TypesafeConfig("graphite.prefix") String graphitePrefix) {
        this.graphiteClient = graphiteClient;
        this.graphitePrefix = preparePrefix(graphitePrefix);
    }

    @Override
    public void aggregate(String key, Stats stats) {
        GraphiteMetricsBuilder builder = new GraphiteMetricsBuilder();
        aggregateHistogram(builder, key, "responseTimeMs", stats.getResponseTimeHistogram(), stats.getTimestamp());
        aggregateHistogram(builder, key, "queueLength", stats.getQueueLengthHistogram(), stats.getTimestamp());
        builder.add(metricName(key, "totalFailures"), stats.getTotalErrors(), stats.getTimestamp());
        builder.add(metricName(key, "successPercent"), stats.getSuccessesPercent(), stats.getTimestamp());
        builder.add(metricName(key, "uptimePercent"), stats.getUptimePercent(), stats.getTimestamp());
        graphiteClient.write(builder.build());
    }

    private void aggregateHistogram(GraphiteMetricsBuilder builder, String key, String name, Histogram histogram,
                                    Instant timestamp)
    {
        builder.add(metricName(key, name + ".mean"), histogram.getMean(), timestamp);
        builder.add(metricName(key, name + ".max"), histogram.getMaxValue(), timestamp);
        builder.add(metricName(key, name + ".min"), histogram.getMinValue(), timestamp);
        builder.add(metricName(key, name + ".stdDev"), histogram.getStdDeviation(), timestamp);
        builder.add(metricName(key, name + ".25_percentile"), histogram.getValueAtPercentile(25.0d), timestamp);
        builder.add(metricName(key, name + ".50_percentile"), histogram.getValueAtPercentile(50.0d), timestamp);
        builder.add(metricName(key, name + ".75_percentile"), histogram.getValueAtPercentile(75.0d), timestamp);
        builder.add(metricName(key, name + ".80_percentile"), histogram.getValueAtPercentile(80.0d), timestamp);
        builder.add(metricName(key, name + ".90_percentile"), histogram.getValueAtPercentile(90.0d), timestamp);
        builder.add(metricName(key, name + ".95_percentile"), histogram.getValueAtPercentile(95.0d), timestamp);
        builder.add(metricName(key, name + ".98_percentile"), histogram.getValueAtPercentile(98.0d), timestamp);
        builder.add(metricName(key, name + ".99_percentile"), histogram.getValueAtPercentile(99.0d), timestamp);
        builder.add(metricName(key, name + ".999_percentile"), histogram.getValueAtPercentile(99.9d), timestamp);
        builder.add(metricName(key, name + ".100_percentile"), histogram.getValueAtPercentile(100.0d), timestamp);
    }

    private String metricName(String key, String name) {
        return graphitePrefix + key + "." + name;
    }

    private static String preparePrefix(String graphitePrefix) {
        Template prefixTemplate = Mustache.compiler().compile(graphitePrefix);
        Map<String, String> templateVariables = new HashMap<>();
        templateVariables.put("hostname", getHostName());
        String appliedTemplate = prefixTemplate.execute(templateVariables);
        return appliedTemplate.endsWith(".") ? appliedTemplate : appliedTemplate + ".";
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName().replace('.', '_');
        } catch (UnknownHostException e) {
            return "hostname";
        }
    }

}
