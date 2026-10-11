import com.p2p.server.social.*;
import com.p2p.server.ops.SafeChatDemoSeedTool;
import com.p2p.client.network.social.*;
import com.p2p.common.social.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Uses the actual production adapter, with no key, to prove fail-closed through TLS. */
public class ProductionAiSmoke {
    public static void main(String[] args)throws Exception {
        if(System.getenv("OPENAI_API_KEY")!=null&&!System.getenv("OPENAI_API_KEY").isBlank())throw new IllegalStateException("This no-key smoke must run without OPENAI_API_KEY");
        Path root=Path.of(args[0]).toAbsolutePath(),qa=root.resolve("qa/production-ai");Files.createDirectories(qa);
        Path db=qa.resolve("smoke-"+UUID.randomUUID()+".db");String password="Production-Smoke-Only-2026";SafeChatDemoSeedTool.seed(db,password);
        var base=SocialServer.Config.load(root.resolve("server/config/social.properties"));
        var server=new SocialServer(new SocialServer.Config(db,qa.resolve("media-"+UUID.randomUUID()),"127.0.0.1",0,0,0,10,base.tls(),10*1024*1024,30*1024*1024,true));
        Thread serving=new Thread(()->{try{server.start();}catch(Exception e){throw new RuntimeException(e);}});serving.start();
        try {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(server.tcpPort()==0){if(System.nanoTime()>end)throw new AssertionError("Server deadline");Thread.sleep(10);}
            var tls=TlsContexts.client(root.resolve("config/tls/truststore.p12"),System.getenv("SOCIAL_TLS_PASSWORD").toCharArray());
            try(var client=new SocialClient("127.0.0.1",server.tcpPort(),tls)) {
                client.connect().get(5,TimeUnit.SECONDS);call(client,"AUTH_LOGIN",Map.of("username","demo_alice","password",password));
                @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)call(client,"CONVERSATION_LIST",Map.of("limit",20)).get("items");
                Object cid=list.stream().filter(c->"DIRECT".equals(c.get("kind"))).findFirst().orElseThrow().get("conversationId");
                if(cid==null)cid=list.stream().filter(c->"DIRECT".equals(c.get("kind"))).findFirst().orElseThrow().get("id");
                var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",out);byte[] bytes=out.toByteArray();
                String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                var upload=call(client,"MEDIA_UPLOAD_INIT",Map.of("purpose","CHAT_FILE","fileName","production-no-key.png","mimeType","image/png","size",bytes.length,"sha256",sha));
                call(client,"MEDIA_UPLOAD_CHUNK",Map.of("transferId",upload.get("transferId"),"offset",0,"data",Base64.getEncoder().encodeToString(bytes)));
                call(client,"MEDIA_UPLOAD_COMMIT",Map.of("transferId",upload.get("transferId")));Object id=upload.get("mediaId");
                end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);Map<String,Object> review;
                do{review=call(client,"MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id));if(System.nanoTime()>end)throw new AssertionError("AI deadline");Thread.sleep(10);}while("CHECKING".equals(review.get("status")));
                if(!"UNAVAILABLE".equals(review.get("status")))throw new AssertionError("No key must stay unavailable");
                boolean blocked=false;
                try{call(client,"MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Not allowed image","mediaId",id));}
                catch(ExecutionException e){if(e.getCause() instanceof RemoteException r&&"IMAGE_NOT_REVIEWED".equals(r.getCode()))blocked=true;else throw e;}
                if(!blocked)throw new AssertionError("Image was sent without moderation");
                call(client,"MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Text still works with no AI key"));
                var evidence=Map.of("adapter","production OpenAiImageModerator","apiKeyConfigured",false,"review",review,"unreviewedImageRejected",true,"textContinues",true,"runAt",java.time.Instant.now().toString());
                Files.writeString(qa.resolve("evidence.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
                System.out.println("PASS production adapter no key => UNAVAILABLE, TLS attach rejected, text continues");
            }
        }finally{server.close();serving.join(5000);}
    }
    static Map<String,Object> call(SocialClient c,String t,Map<String,Object> p)throws Exception{return c.request(t,p,UUID.randomUUID().toString()).get(5,TimeUnit.SECONDS);}
}
