package com.p2p.server.social;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Required AI policy through real TCP/TLS sockets, with an explicitly injected fixture. */
class SafeChatRuntimeTest {
    @TempDir Path dir;
    @ParameterizedTest
    @ValueSource(strings={"CLEAR","WARNING","BLOCKED","UNAVAILABLE"})
    void imagePolicySurvivesRealTlsFrames(String status)throws Exception {
        ProtocolRuntimeTest fixture=new ProtocolRuntimeTest();fixture.dir=dir;fixture.setup();fixture.stop();
        var decision=new java.util.concurrent.atomic.AtomicReference<>(status);
        fixture.server=new SocialServer(new SocialServer.Config(dir.resolve("ai.db"),dir.resolve("ai-media"),"127.0.0.1",0,0,0,10,fixture.serverTls,10*1024*1024,30*1024*1024,true),
            (bytes,mime)->new ImageModerator.Decision(decision.get(),.6,.1,"test-fixture","fixture-model","Kết quả giả lập kiểm thử"));
        fixture.acceptor=Executors.newSingleThreadExecutor();fixture.running=fixture.acceptor.submit(()->{try{fixture.server.start();}catch(Exception e){throw new CompletionException(e);}});
        try {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(fixture.server.tcpPort()==0){if(System.nanoTime()>end)fail("Startup deadline");Thread.sleep(10);}
            try(var alice=fixture.client();var bob=fixture.client()) {
                alice.hello();bob.hello();long aid=alice.register("alice"),bid=bob.register("bob");alice.login("alice");bob.login("bob");
                var relation=alice.call("FRIEND_REQUEST",Map.of("targetUserId",bid));bob.call("FRIEND_ACCEPT",Map.of("relationId",relation.get("relationId"),"expectedVersion",relation.get("version")));
                Object cid=alice.call("CONVERSATION_DIRECT_GET_OR_CREATE",Map.of("targetUserId",bid)).get("conversationId");
                var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_RGB),"png",bytes);byte[] png=bytes.toByteArray();
                var upload=alice.call("MEDIA_UPLOAD_INIT",Map.of("purpose","CHAT_FILE","fileName","renamed.bin","mimeType","application/octet-stream","size",png.length,"sha256",MediaServiceTest.hash(png)));
                alice.call("MEDIA_UPLOAD_CHUNK",Map.of("transferId",upload.get("transferId"),"offset",0,"data",Base64.getEncoder().encodeToString(png)));
                alice.call("MEDIA_UPLOAD_COMMIT",Map.of("transferId",upload.get("transferId")));Object id=upload.get("mediaId");
                end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!status.equals(alice.call("MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id)).get("status"))){if(System.nanoTime()>end)fail("AI fixture deadline");Thread.sleep(10);}
                var send=Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Ảnh cảnh báo","mediaId",id);
                if(Set.of("BLOCKED","UNAVAILABLE").contains(status)) {
                    String code="BLOCKED".equals(status)?"IMAGE_BLOCKED":"IMAGE_NOT_REVIEWED";
                    ProtocolRuntimeTest.error(code,alice.attempt("MESSAGE_SEND",send,UUID.randomUUID().toString()));
                    ProtocolRuntimeTest.error(code,alice.attempt("MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id,"warningAcknowledged",true),UUID.randomUUID().toString()));
                    assertTrue(((List<?>)bob.call("MESSAGE_HISTORY",Map.of("conversationId",cid,"limit",20)).get("items")).isEmpty());
                    alice.call("MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Text still works"));
                    if("BLOCKED".equals(status)) {
                        ProtocolRuntimeTest.error("INVALID_STATE",alice.attempt("MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id),UUID.randomUUID().toString()));
                        return;
                    }
                    decision.set("CLEAR");alice.call("MEDIA_IMAGE_RECHECK",Map.of("mediaId",id));
                    end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(!"CLEAR".equals(alice.call("MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id)).get("status"))){if(System.nanoTime()>end)fail("Retry deadline");Thread.sleep(10);}
                }
                if("WARNING".equals(status)) {
                    ProtocolRuntimeTest.error("IMAGE_WARNING",alice.attempt("MESSAGE_SEND",send,UUID.randomUUID().toString()));
                    alice.call("MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id));
                }
                alice.call("MESSAGE_SEND",send);
                if("WARNING".equals(status))ProtocolRuntimeTest.error("IMAGE_WARNING",bob.attempt("MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id),UUID.randomUUID().toString()));
                assertNotNull(bob.call("MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id,"warningAcknowledged","WARNING".equals(status))).get("transferId"));
                assertFalse(((List<?>)bob.call("MESSAGE_HISTORY",Map.of("conversationId",cid,"limit",20)).get("items")).isEmpty());assertTrue(aid>0);
            }
        } finally {fixture.stop();}
    }
}
