package org.opencb.opencga.storage.benchmark.variant.generators;

import com.google.common.base.Throwables;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Created on 06/04/17.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public abstract class QueryGenerator {
    public static final String DATA_DIR = "dataDir";
    public static final String ARITY = "arity";
    protected Random random;

    private Logger logger = LoggerFactory.getLogger(getClass());
    private int arity;

    public void setUp(Map<String, String> params) {
        random = new Random(System.nanoTime());
        arity = Integer.parseInt(params.getOrDefault(ARITY, "1"));
    }

    protected void readCsvFile(Path path, Consumer<List<String>> consumer) {
        try (BufferedReader is = FileUtils.newBufferedReader(path)) {
            while (true) {
                String line = is.readLine();
                if (line == null) {
                    break;
                } else if (StringUtils.isBlank(line) || line.startsWith("#")) {
                    continue;
                }
                consumer.accept(Arrays.asList(line.split(",")));
            }
        } catch (IOException e) {
            logger.error("Error reading file " + path, e);
            throw Throwables.propagate(e);
        }
    }

    public abstract Query generateQuery(Query query);

    protected int getArity() {
        return arity;
    }

    public QueryGenerator setArity(int arity) {
        this.arity = arity;
        return this;
    }
}
