/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.storage.core.benchmark;

import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.StorageEngineFactory;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by imedina on 16/06/15.
 */
public class VariantBenchmarkRunner extends BenchmarkRunner {

    private String[] queryType;
    private String queryParams;
    private BenchmarkStats benchmarkStats;

    public VariantBenchmarkRunner(StorageConfiguration storageConfiguration) throws IllegalAccessException, ClassNotFoundException,
            InstantiationException, StorageEngineException {
        this(storageConfiguration.getDefaultStorageEngineId(), storageConfiguration);
    }

    public VariantBenchmarkRunner(String storageEngine, StorageConfiguration storageConfiguration)
            throws IllegalAccessException, ClassNotFoundException, InstantiationException, StorageEngineException {
        this.storageEngine = storageEngine;
        this.storageConfiguration = storageConfiguration;
        logger = LoggerFactory.getLogger(this.getClass());
        init(storageEngine);
    }

    private void init(String storageEngine)
            throws IllegalAccessException, InstantiationException, ClassNotFoundException, StorageEngineException {
        StorageEngineFactory storageEngineFactory = StorageEngineFactory.get(storageConfiguration);
        VariantStorageEngine variantStorageEngine = storageEngineFactory.getVariantStorageEngine(storageEngine,
                storageConfiguration.getBenchmark().getDatabaseName());
        variantDBAdaptor = variantStorageEngine.getDBAdaptor();
    }


    @Override
    public BenchmarkStats convert() {
        return null;
    }

    @Override
    public BenchmarkStats insert() {
        return null;
    }

    @Override
    public BenchmarkStats query() throws ExecutionException, InterruptedException {
        return query(3, new HashSet<>(Arrays.asList("count", "queryByRegion")));
    }

    @Override
    public BenchmarkStats query(int numRepetitions, Set<String> benchmarkTests) throws ExecutionException, InterruptedException {
//        int executionTime = 0;

        benchmarkStats = new BenchmarkStats();
        for (int i = 0; i < numRepetitions; i++) {
            Iterator<String> iterator = benchmarkTests.iterator();
            while (iterator.hasNext()) {
                String next = iterator.next();

                queryType = next.split("-");
                if (queryType.length >= 2) {
                    queryParams = queryType[1];
                } else if (queryType.length == 1 && queryType[0].equals("distinct")) {
//                    System.out.println("inside else .query :: ");
                    queryParams = "gene";
                }

                Query query = new Query();
                QueryOptions queryOptions = new QueryOptions();

                switch (queryType[0]) {
                    case "count":
                        executeThreads(queryType[0], () -> variantDBAdaptor.count(new Query()));
                        System.out.println("VariantBenchmarkRunner.query" + variantDBAdaptor.count(new Query()).getResult());
//                        executionTime = count();
                        break;
                    case "distinct":
                        executeThreads(queryType[0], () -> variantDBAdaptor.distinct(new Query(), queryParams));
//                        System.out.println("VariantBenchmarkRunner.query >>>>>> " + queryParams + "   "
//                                + variantDBAdaptor.distinct(new Query(),
//                                queryParams).getResult().size());
                        break;
                    case "queryById":
                        query.put(VariantQueryParam.ID.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByRegion":
                        query.put(VariantQueryParam.REGION.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByChromosome":
                        query.put(VariantQueryParam.CHROMOSOME.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByGene":
                        query.put(VariantQueryParam.GENE.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByType":
                        query.put(VariantQueryParam.TYPE.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByReference":
                        query.put(VariantQueryParam.REFERENCE.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByAlternate":
                        query.put(VariantQueryParam.ALTERNATE.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    case "queryByStudies":
                        query.put(VariantQueryParam.STUDIES.key(), queryParams);
                        executeThreads(queryType[0], () -> variantDBAdaptor.get(query, queryOptions));
                        break;
                    default:
                        break;
                }
//                benchmarkStats.addExecutionTime(next, executionTime);
            }
        }

        benchmarkStats.printSummary(storageConfiguration.getBenchmark().getDatabaseName(),
                storageConfiguration.getBenchmark().getTable(), storageConfiguration.getBenchmark().getNumRepetitions(),
                storageConfiguration.getBenchmark().getConcurrency());
        return benchmarkStats;
    }

    private <T> List<Future<T>> executeThreads(String test, Callable<T> task) throws ExecutionException, InterruptedException {
        int concurrency = storageConfiguration.getBenchmark().getConcurrency();
//        System.out.println("concurrency :: " + concurrency);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<T>> futureList = new ArrayList<>(10);
        for (int i = 0; i < concurrency; i++) {
            futureList.add(executorService.submit(task));
        }

        int totalTime = 0;
        for (Future<T> queryResultFuture : futureList) {
            totalTime += ((QueryResult) queryResultFuture.get()).getDbTime();
//            System.out.println("queryResultFuture.get().getDbTime() = " + queryResultFuture.get().getDbTime());
//            System.out.println("queryResultFuture.get().getResult().size() = " + queryResultFuture.get().getResult().get(0));
        }

        benchmarkStats.addExecutionTime(test, totalTime);
        benchmarkStats.addStdDeviation(test, totalTime);

        return futureList;
    }
}



