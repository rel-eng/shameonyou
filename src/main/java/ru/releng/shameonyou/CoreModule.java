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

import com.github.racc.tscg.TypesafeConfigModule;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.typesafe.config.ConfigFactory;
import ru.releng.shameonyou.core.Aggregator;
import ru.releng.shameonyou.core.AggregatorService;
import ru.releng.shameonyou.core.LifeCycle;
import ru.releng.shameonyou.core.SamplerService;
import ru.releng.shameonyou.core.graphite.GraphiteAggregator;
import ru.releng.shameonyou.core.graphite.GraphiteClient;


public class CoreModule extends AbstractModule {

    @Override
    protected void configure() {
        install(TypesafeConfigModule.fromConfigWithPackage(ConfigFactory.load(), "ru.releng.shameonyou"));
        Multibinder<LifeCycle> lifeCycleBinder = Multibinder.newSetBinder(binder(), LifeCycle.class);
        lifeCycleBinder.addBinding().to(SamplerService.class);
        lifeCycleBinder.addBinding().to(AggregatorService.class);
        lifeCycleBinder.addBinding().to(GraphiteClient.class);
        Multibinder<Aggregator> aggregatorBinder = Multibinder.newSetBinder(binder(), Aggregator.class);
        aggregatorBinder.addBinding().to(GraphiteAggregator.class);
    }

}
