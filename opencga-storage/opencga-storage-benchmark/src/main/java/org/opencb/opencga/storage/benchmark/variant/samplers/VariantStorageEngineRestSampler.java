package org.opencb.opencga.storage.benchmark.variant.samplers;

import com.google.common.base.Throwables;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSampler;
import org.apache.jmeter.protocol.http.util.HTTPArgument;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.benchmark.variant.generators.QueryGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created on 06/04/17.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantStorageEngineRestSampler extends HTTPSampler implements VariantStorageEngineSampler{

    private Logger logger = LoggerFactory.getLogger(getClass());
    private QueryGenerator queryGenerator;

    public VariantStorageEngineRestSampler() {
        setPath("opencga/webservices/rest/variants/fetch");
        setMethod("GET");
    }

    public VariantStorageEngineRestSampler(String host, int port) {
        this();
        setDomain(host);
        setPort(port);
    }

    @Override
    public String getQueryString(String contentEncoding) {
        StringBuilder sb = new StringBuilder(super.getQueryString(contentEncoding));

        if (sb.length() != 0) {
            sb.append('&');
        }

        Query query = getQueryGenerator().generateQuery(new Query());
        query.forEach((key, value) -> sb.append(key).append('=').append(value).append('&'));

        return sb.toString();
    }

    @Override
    protected HTTPSampleResult sample(java.net.URL u, String method, boolean areFollowingRedirect, int depth) {
//        logger.debug("url = {}", u);
        return super.sample(u, method, areFollowingRedirect, depth);
    }

    @Override
    public VariantStorageEngineRestSampler setStorageEngine(String storageEngine) {
        getArguments().addArgument(new HTTPArgument("storageEngine", storageEngine, "="));
        return this;
    }

    @Override
    public VariantStorageEngineRestSampler setDBName(String dbname) {
        getArguments().addArgument(new HTTPArgument("dbName", dbname, "="));
        return this;
    }

    @Override
    public VariantStorageEngineRestSampler setLimit(int limit) {
        getArguments().addArgument(new HTTPArgument(QueryOptions.LIMIT, String.valueOf(limit), "="));
        return this;
    }

    @Override
    public VariantStorageEngineSampler setCount(boolean count) {
        getArguments().addArgument(new HTTPArgument(QueryOptions.COUNT, String.valueOf(count), "="));
        return this;
    }

    @Override
    public VariantStorageEngineRestSampler setQueryGenerator(Class<? extends QueryGenerator> queryGenerator) {
        setProperty("VariantStorageEngineSamples." + VariantStorageEngineSampler.QUERY_GENERATOR, queryGenerator.getName());
        return this;
    }

    private String getQueryGeneratorClassName() {
        return getPropertyAsString("VariantStorageEngineSamples." + VariantStorageEngineSampler.QUERY_GENERATOR);
    }

    @Override
    public VariantStorageEngineSampler setQueryGeneratorConfig(String key, String value) {
        setProperty(QueryGenerator.class.getName() + '.' + key, value);
        return this;
    }

    private QueryGenerator getQueryGenerator() {
        if (queryGenerator == null) {
            String queryGeneratorClassName = getQueryGeneratorClassName();
            try {
                Map<String, String> map = new HashMap<>();
                String queryGeneratorProperties = QueryGenerator.class.getName() + ".";
                propertyIterator().forEachRemaining(property -> {
                    if (property.getName().startsWith(queryGeneratorProperties)) {
                        map.put(property.getName().substring(queryGeneratorProperties.length()), property.getStringValue());
                    }
                });
                queryGenerator = (QueryGenerator) Class.forName(queryGeneratorClassName).newInstance();
                queryGenerator.setUp(map);
                logger.debug("Using query generator " + queryGenerator.getClass());
            } catch (Throwable e) {
                logger.error("Error creating QueryGenerator!", e);
                Throwables.propagate(e);
            }
        }
        return queryGenerator;
    }
}
