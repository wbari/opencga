/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryResponse;
import org.opencb.datastore.core.QueryResult;
import org.opencb.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.analysis.AnalysisJobExecutor;
import org.opencb.opencga.analysis.storage.AnalysisFileIndexer;
import org.opencb.opencga.catalog.CatalogManagerTest;
import org.opencb.opencga.core.common.Config;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Created by ralonso on 9/25/14.
 */
public class WSServerTestUtils {

    static ObjectMapper mapper = OpenCGAWSServer.jsonObjectMapper;
    private Server server;
    private String restURL;
    public static final int PORT = 8889;
    public static final String DATABASE_PREFIX = "opencga_server_test_";


    public static <T> QueryResponse<QueryResult<T>> parseResult(String json, Class<T> clazz) throws IOException {
        ObjectReader reader = mapper.reader(mapper.getTypeFactory().constructParametrizedType(
                QueryResponse.class, QueryResponse.class, mapper.getTypeFactory().constructParametrizedType(QueryResult.class, QueryResult.class, clazz)));
        return reader.readValue(json);
    }


    public void initServer() throws Exception {

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.packages(false, "org.opencb.opencga.server.ws");
        resourceConfig.property("jersey.config.server.provider.packages", "org.opencb.opencga.server.ws;com.wordnik.swagger.jersey.listing;com.jersey.jaxb;com.fasterxml.jackson.jaxrs.json");
        resourceConfig.property("jersey.config.server.provider.classnames", "org.glassfish.jersey.media.multipart.MultiPartFeature");

        ServletContainer sc = new ServletContainer(resourceConfig);
        ServletHolder sh = new ServletHolder(sc);

        server = new Server(PORT);

        ServletContextHandler context = new ServletContextHandler(server, null, ServletContextHandler.SESSIONS);
        context.addServlet(sh, "/opencga/webservices/rest/*");

        System.err.println("Starting server");
        server.start();
        System.err.println("Waiting for conections");
        System.out.println(server.getState());


        restURL = server.getURI().resolve("/opencga/webservices/rest/").resolve("v1/").toString();
        System.out.println(server.getURI());
    }

    public void shutdownServer() throws Exception {
        System.err.println("Shout down server");
        server.stop();
        server.join();
    }

    public WebTarget getWebTarget() {
        Client webClient = ClientBuilder.newClient();
        webClient.register(MultiPartFeature.class);
        return webClient.target(restURL);
    }

    public void setUp() throws Exception {
        //Create test environment. Override OpenCGA_Home
        Path opencgaHome = Paths.get("/tmp/opencga-server-test");
        System.setProperty("app.home", opencgaHome.toString());
        Config.setOpenCGAHome(opencgaHome.toString());

        Files.createDirectories(opencgaHome);
        Files.createDirectories(opencgaHome.resolve("conf"));

        InputStream inputStream = CatalogManagerTest.class.getClassLoader().getResourceAsStream("catalog.properties");
        Files.copy(inputStream, opencgaHome.resolve("conf").resolve("catalog.properties"), StandardCopyOption.REPLACE_EXISTING);
        inputStream = new ByteArrayInputStream((AnalysisJobExecutor.OPENCGA_ANALYSIS_JOB_EXECUTOR + "=LOCAL" + "\n" +
                AnalysisFileIndexer.OPENCGA_ANALYSIS_STORAGE_DATABASE_PREFIX + "=" + DATABASE_PREFIX).getBytes());
        Files.copy(inputStream, opencgaHome.resolve("conf").resolve("analysis.properties"), StandardCopyOption.REPLACE_EXISTING);
        inputStream = CatalogManagerTest.class.getClassLoader().getResourceAsStream("storage.properties");
        Files.copy(inputStream, opencgaHome.resolve("conf").resolve("storage.properties"), StandardCopyOption.REPLACE_EXISTING);
        inputStream = CatalogManagerTest.class.getClassLoader().getResourceAsStream("storage-mongodb.properties");
        Files.copy(inputStream, opencgaHome.resolve("conf").resolve("storage-mongodb.properties"), StandardCopyOption.REPLACE_EXISTING);

        CatalogManagerTest catalogManagerTest = new CatalogManagerTest();

        catalogManagerTest.setUp(); //Clear and setup CatalogDatabase
        OpenCGAWSServer.catalogManager = catalogManagerTest.getTestCatalogManager();
    }
}
