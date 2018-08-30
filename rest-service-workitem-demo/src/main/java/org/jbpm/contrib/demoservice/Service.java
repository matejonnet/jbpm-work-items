package org.jbpm.contrib.demoservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Path("/service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class Service {

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    ObjectMapper objectMapper = new ObjectMapper();

    @POST
    @Path("/A")
    public Response actionA(RequestA request) throws JsonProcessingException {
        System.out.println(">>> Action A requested.");
        System.out.println(">>> request object: " + objectMapper.writeValueAsString(request));
        String callbackUrl = request.getCallbackUrl();

        Map<String, String> person = new HashMap<>();
        person.put("name", request.getName());

        Map<String, Object> result = new HashMap<>();
        result.put("person", person);

        scheduleCallback(callbackUrl, 10, result);

        return Response.status(200).entity(null).build();
    }

    @POST
    @Path("/B")
    public Response actionB(RequestB request) throws JsonProcessingException {
        System.out.println(">>> Action B requested.");
        System.out.println(">>> request object: " + objectMapper.writeValueAsString(request));

        String callbackUrl = request.getCallbackUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("fullName", request.getNameFromA() + " " + request.getSurname());

        scheduleCallback(callbackUrl, 10, result);

        return Response.status(200).entity(null).build();
    }

    private void scheduleCallback(String callbackUrl, int delay, Map<String, Object> result) {
        executorService.schedule(() -> executeCallback(callbackUrl, result), delay, TimeUnit.SECONDS);
    }

    private void executeCallback(String callbackUrl, Map<String, Object> result) {

        RequestConfig config = RequestConfig.custom()
                .setSocketTimeout(5000)
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setAuthenticationEnabled(true)
                .build();


        try {

            URI requestUri = new URI(callbackUrl);

            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(requestUri.getHost(),
                            requestUri.getPort(),
                            AuthScope.ANY_REALM),
                    new UsernamePasswordCredentials("admin", "admin")
            );

            HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .setDefaultCredentialsProvider(credsProvider);

            HttpClient httpClient = clientBuilder.build();
            HttpPut request = new HttpPut(requestUri);

            request.setHeader("Content-Type","application/json");

            System.out.println(">>> Calling back to: " + requestUri);

            Map<String, Map<String, Object>> wrappedResult = new HashMap<>();
            wrappedResult.put("content", result);

            String jsonContent = objectMapper.writeValueAsString(wrappedResult);

            System.out.println(">>> Result data:" + jsonContent);

            StringEntity entity = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);
            request.setEntity(entity);
            HttpResponse response = httpClient.execute(request);
            System.out.println("Callback executed. Returned status: " + response.getStatusLine().getStatusCode());

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

    }

}