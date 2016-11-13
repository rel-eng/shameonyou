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

import java.time.Instant;
import java.util.Locale;

public class GraphiteMetricsBuilder {

    private final StringBuilder builder;

    public GraphiteMetricsBuilder() {
        this.builder = new StringBuilder();
    }

    public void add(String name, String value, Instant timestamp) {
        builder.append(String.format(Locale.US, "%s %s %d\n", name, value, timestamp.toEpochMilli()));
    }

    public void add(String name, long value, Instant timestamp) {
        add(name, String.format(Locale.US, "%d", value), timestamp);
    }

    public void add(String name, double value, Instant timestamp) {
        add(name, String.format(Locale.US, "%2.2f", value), timestamp);
    }

    public String build() {
        return builder.toString();
    }

}
