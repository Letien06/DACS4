package com.p2p.server.social;

import com.sun.net.httpserver.HttpServer;
import com.p2p.common.social.Json;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Contract tests use a loopback HTTP fixture; they do not measure model accuracy. */
class OpenAiImageModeratorTest {
    HttpServer server;
    AtomicReference<String> request = new AtomicReference<>(), authorization = new AtomicReference<>();
    String body; int code=200; long stall;
    @BeforeEach void setup() throws Exception {
        body=response(.1,.01,false,false);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/moderations",exchange->{
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code,bytes.length);
            try { if(stall>0)Thread.sleep(stall); exchange.getResponseBody().write(bytes); }
            catch(Exception ignored){} finally { exchange.close(); }
        }); server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    OpenAiImageModerator adapter(String key,long timeout) {
        return new OpenAiImageModerator(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
            URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/moderations"),key,Duration.ofMillis(timeout),.5,.8);
    }
    static String response(double violence,double graphic,boolean v,boolean g)throws Exception {
        return Json.MAPPER.writeValueAsString(Map.of("model","omni-moderation-test","results",List.of(Map.of(
            "category_scores",Map.of("violence",violence,"violence/graphic",graphic),
            "categories",Map.of("violence",v,"violence/graphic",g),
            "category_applied_input_types",Map.of("violence",List.of("image"),"violence/graphic",List.of("image"))))));
    }
    @Test void submitsDocumentedPayloadAndReadsImageSignals()throws Exception {
        byte[] image={1,2,3};var decision=adapter("test-only-secret",3000).classify(image,"image/png");
        assertEquals("CLEAR",decision.status());assertEquals("omni-moderation-test",decision.model());
        var json=Json.MAPPER.readTree(request.get());assertEquals("omni-moderation-latest",json.path("model").asText());
        assertEquals("image_url",json.path("input").get(0).path("type").asText());
        assertEquals("data:image/png;base64,AQID",json.path("input").get(0).path("image_url").path("url").asText());
        assertEquals("Bearer test-only-secret",authorization.get());assertFalse(decision.reason().contains("test-only-secret"));
    }
    @Test void mapsWarningsAndBlocksUsingScoresAndProviderLabels()throws Exception {
        var ai=adapter("test",3000);
        body=response(.5,.2,false,false);assertEquals("WARNING",ai.classify(new byte[]{1},"image/jpeg").status());
        body=response(.01,.01,true,false);assertEquals("WARNING",ai.classify(new byte[]{1},"image/png").status());
        body=response(.1,.8,false,false);assertEquals("BLOCKED",ai.classify(new byte[]{1},"image/png").status());
        body=response(.01,.01,false,true);assertEquals("BLOCKED",ai.classify(new byte[]{1},"image/png").status());
    }
    @Test void missingKeyAndUnsupportedInputNeverCallProvider()throws Exception {
        assertEquals("UNAVAILABLE",adapter(null,500).classify(new byte[]{1},"image/png").status());
        assertEquals("UNAVAILABLE",adapter("test",500).classify(new byte[0],"image/png").status());
        assertEquals("UNAVAILABLE",adapter("test",500).classify(new byte[]{1},"image/gif").status());assertNull(request.get());
    }
    @Test void malformedAndTextOnlyResultsAreUnavailable()throws Exception {
        for(String invalid:List.of("not-json","{}",response(.1,.1,false,false).replace("image","text"),response(.1,.1,false,false).replace("0.1","2.0"))) {
            body=invalid;assertEquals("UNAVAILABLE",adapter("test",3000).classify(new byte[]{1},"image/png").status());
        }
    }
    @Test void httpErrorsNeverExposeProviderBodyOrCredentials()throws Exception {
        body="test-only-secret provider-private-body";
        for(int status:List.of(401,429,500,302)) {
            code=status;var d=adapter("test-only-secret",3000).classify(new byte[]{1},"image/png");
            assertEquals("UNAVAILABLE",d.status());assertFalse(d.reason().contains("test-only-secret"));assertFalse(d.reason().contains("provider-private-body"));
        }
    }
    @Test void deadlineIncludesStalledResponseBody()throws Exception {
        stall=500;long begin=System.nanoTime();var d=adapter("test",100).classify(new byte[]{1},"image/png");
        assertEquals("UNAVAILABLE",d.status());assertTrue((System.nanoTime()-begin)/1_000_000<1500);
    }
    @Test void oversizedResponseIsRejected()throws Exception {
        body=" ".repeat(140*1024);assertEquals("UNAVAILABLE",adapter("test",3000).classify(new byte[]{1},"image/png").status());
    }
}
