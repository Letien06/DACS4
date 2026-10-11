package com.p2p.server.social;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

class ImageModerationPolicyTest {
    @TempDir Path dir;
    SocialDatabase db;SocialService social;MediaService media;long alice,bob,stranger,conversation;
    AtomicReference<String> decision=new AtomicReference<>("CLEAR");AtomicInteger calls=new AtomicInteger();
    volatile CountDownLatch gate=new CountDownLatch(0);
    @BeforeEach void setup()throws Exception {
        db=new SocialDatabase(dir.resolve("social.db"));social=new SocialService(db);
        alice=register("alice");bob=register("bob");stranger=register("stranger");
        var relation=app(alice,"FRIEND_REQUEST",Map.of("targetUserId",bob));
        app(bob,"FRIEND_ACCEPT",Map.of("relationId",relation.get("relationId"),"expectedVersion",relation.get("version")));
        conversation=((Number)app(alice,"CONVERSATION_DIRECT_GET_OR_CREATE",Map.of("targetUserId",bob)).get("conversationId")).longValue();
        openMedia();
    }
    void openMedia()throws Exception {
        media=new MediaService(db,social,dir.resolve("storage"),10*1024*1024,30*1024*1024,(bytes,mime)->{
            calls.incrementAndGet();gate.await(3,TimeUnit.SECONDS);
            return new ImageModerator.Decision(decision.get(),.6,.1,"test-fixture","fixture-model","Kết quả giả lập chính sách");
        });
    }
    @AfterEach void close()throws Exception {gate.countDown();if(media!=null)media.close();if(db!=null)db.close();}
    long register(String name)throws Exception {return ((Number)social.register(Map.of("username",name,"displayName",name,"password","Strong-Password-123")).get("userId")).longValue();}
    Map<String,Object> app(long actor,String type,Map<String,Object> p)throws Exception{return social.execute(actor,type,p,UUID.randomUUID().toString());}
    Map<String,Object> call(long actor,String type,Map<String,Object> p)throws Exception{return media.execute(actor,type,p);}
    byte[] image(String format)throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
    Map<String,Object> upload(byte[] bytes,String name,String mime)throws Exception {
        var t=call(alice,"MEDIA_UPLOAD_INIT",Map.of("purpose","CHAT_FILE","fileName",name,"mimeType",mime,"size",bytes.length,"sha256",MediaServiceTest.hash(bytes)));
        call(alice,"MEDIA_UPLOAD_CHUNK",Map.of("transferId",t.get("transferId"),"offset",0,"data",Base64.getEncoder().encodeToString(bytes)));
        call(alice,"MEDIA_UPLOAD_COMMIT",Map.of("transferId",t.get("transferId")));return t;
    }
    Map<String,Object> review(String id)throws Exception{return call(alice,"MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id));}
    void await(String id,String status)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(!status.equals(review(id).get("status"))){if(System.nanoTime()>end)fail("Review did not become "+status);Thread.sleep(10);}
    }
    void send(String id)throws Exception {app(alice,"MESSAGE_SEND",Map.of("conversationId",conversation,"clientMessageId",UUID.randomUUID().toString(),"content","Ảnh","mediaId",id));}
    @Test void renamedImageIsScannedAndCheckingCannotBeAttachedOrDownloaded()throws Exception {
        gate=new CountDownLatch(1);String id=(String)upload(image("png"),"report.bin","application/octet-stream").get("mediaId");
        assertEquals("CHECKING",review(id).get("status"));
        assertEquals("image/png",call(alice,"MEDIA_UPLOAD_STATUS",Map.of("mediaId",id)).get("mimeType"));
        MediaServiceTest.code("IMAGE_NOT_REVIEWED",()->send(id));
        MediaServiceTest.code("IMAGE_NOT_REVIEWED",()->call(alice,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id)));
        gate.countDown();await(id,"CLEAR");send(id);
        assertNotNull(call(bob,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id)).get("transferId"));assertEquals(1,calls.get());
        MediaServiceTest.code("NOT_FOUND",()->call(stranger,"MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id)));
    }
    @Test void warningRequiresOwnerConfirmationAndRecipientAcknowledgment()throws Exception {
        decision.set("WARNING");String id=(String)upload(image("jpeg"),"photo.jpg","image/jpeg").get("mediaId");await(id,"WARNING");
        MediaServiceTest.code("IMAGE_WARNING",()->send(id));
        MediaServiceTest.code("INVALID_STATE",()->call(bob,"MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id)));
        call(alice,"MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id));assertEquals(true,review(id).get("senderConfirmed"));send(id);
        MediaServiceTest.code("IMAGE_WARNING",()->call(bob,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id)));
        MediaServiceTest.code("IMAGE_WARNING",()->call(bob,"MEDIA_THUMBNAIL_GET",Map.of("mediaId",id)));
        assertNotNull(call(bob,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id,"warningAcknowledged",true)).get("transferId"));
        MediaServiceTest.code("INVALID_STATE",()->call(alice,"MEDIA_IMAGE_RECHECK",Map.of("mediaId",id)));
    }
    @Test void blockedCannotBeConfirmedSentOrDownloaded()throws Exception {
        decision.set("BLOCKED");String id=(String)upload(image("png"),"photo.png","image/png").get("mediaId");await(id,"BLOCKED");
        MediaServiceTest.code("IMAGE_BLOCKED",()->send(id));
        MediaServiceTest.code("INVALID_STATE",()->call(alice,"MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id)));
        MediaServiceTest.code("IMAGE_BLOCKED",()->call(alice,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id,"warningAcknowledged",true)));
    }
    @Test void unavailableCanRetryButOnlyByOwnerAndBeforeAttachment()throws Exception {
        decision.set("UNAVAILABLE");String id=(String)upload(image("png"),"photo.png","image/png").get("mediaId");await(id,"UNAVAILABLE");
        MediaServiceTest.code("IMAGE_NOT_REVIEWED",()->send(id));
        MediaServiceTest.code("NOT_FOUND",()->call(bob,"MEDIA_IMAGE_RECHECK",Map.of("mediaId",id)));
        decision.set("CLEAR");call(alice,"MEDIA_IMAGE_RECHECK",Map.of("mediaId",id));await(id,"CLEAR");send(id);assertEquals(2,calls.get());
        MediaServiceTest.code("INVALID_STATE",()->call(alice,"MEDIA_IMAGE_RECHECK",Map.of("mediaId",id)));
    }
    @Test void absentOrInterruptedReviewDoesNotExposeThumbnailAfterRestart()throws Exception {
        String id=(String)upload(image("png"),"photo.png","image/png").get("mediaId");await(id,"CLEAR");
        db.write(c->{SocialService.update(c,"UPDATE image_moderations SET status='CHECKING' WHERE media_id=?",id);return null;});
        media.close();openMedia();await(id,"UNAVAILABLE");MediaServiceTest.code("IMAGE_NOT_REVIEWED",()->send(id));
        db.write(c->{SocialService.update(c,"DELETE FROM image_moderations WHERE media_id=?",id);return null;});
        MediaServiceTest.code("IMAGE_WARNING",()->call(alice,"MEDIA_THUMBNAIL_GET",Map.of("mediaId",id)));
        MediaServiceTest.code("IMAGE_NOT_REVIEWED",()->call(alice,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id)));
    }
    @Test void genericFilesWorkAndUnsupportedImagesBecomeFailed()throws Exception {
        String id=(String)upload("hello".getBytes(),"notes.txt","text/plain").get("mediaId");send(id);assertEquals("NOT_IMAGE",review(id).get("status"));assertEquals(0,calls.get());
        MediaServiceTest.code("INVALID_REQUEST",()->upload(image("gif"),"spoof.bin","application/octet-stream"));
        assertEquals(1L,db.<Long>read(c->SocialService.lng(SocialService.one(c,"SELECT count(*) AS amount FROM media WHERE state='FAILED'"),"amount")));
    }
}
