import com.formdev.flatlaf.FlatDarkLaf;
import com.p2p.client.network.social.*;
import com.p2p.client.ui.social.SocialWindow;
import com.p2p.common.social.*;
import com.p2p.server.social.*;
import com.p2p.server.ops.SafeChatDemoSeedTool;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real packaged TLS/UDP + actual hidden Swing component paints. AI result is an injected fixture. */
public final class SafeChatSmoke {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();Path qa=root.resolve("qa/safechat-smoke");Files.createDirectories(qa);
        Path database=qa.resolve("smoke-"+UUID.randomUUID()+".db");String password="SafeChat-Smoke-Only-2026";
        SafeChatDemoSeedTool.seed(database,password);
        SocialServer.Config base=SocialServer.Config.load(root.resolve("server/config/social.properties"));
        SocialServer server=new SocialServer(new SocialServer.Config(database,qa.resolve("media-"+UUID.randomUUID()),"127.0.0.1",0,0,0,10,base.tls(),10*1024*1024,30*1024*1024,true),
            (bytes,mime)->new ImageModerator.Decision("WARNING",.6,.1,"test-fixture","fixture-model","Cảnh báo giả lập để kiểm thử giao diện"));
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread serving=new Thread(()->{try{server.start();}catch(Throwable e){failure.set(e);}});serving.start();
        SocialClient alice=null,bob=null;UdpClient ua=null,ub=null;SocialWindow[] window={null};
        Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("aiSource","injected fixture; no model accuracy measurement");
        try {
            await(()->server.tcpPort()>0&&server.typingPort()>0,5000);if(failure.get()!=null)throw new RuntimeException(failure.get());
            var tls=TlsContexts.client(root.resolve("config/tls/truststore.p12"),System.getenv("SOCIAL_TLS_PASSWORD").toCharArray());
            alice=new SocialClient("127.0.0.1",server.tcpPort(),tls);bob=new SocialClient("127.0.0.1",server.tcpPort(),tls);
            var hello=alice.connect().get(5,TimeUnit.SECONDS);bob.connect().get(5,TimeUnit.SECONDS);
            var loginA=request(alice,"AUTH_LOGIN",Map.of("username","demo_alice","password",password));
            var loginB=request(bob,"AUTH_LOGIN",Map.of("username","demo_bob","password",password));
            Map<String,Object> a=map(loginA.get("currentUser")),b=map(loginB.get("currentUser"));
            if(a.isEmpty())a=map(loginA.get("user"));if(b.isEmpty())b=map(loginB.get("user"));
            long bid=number(b,"id","userId");
            long cid=number(request(alice,"CONVERSATION_DIRECT_GET_OR_CREATE",Map.of("targetUserId",bid)),"conversationId");
            check(!rows(request(alice,"MESSAGE_HISTORY",Map.of("conversationId",cid,"limit",20)).get("items")).isEmpty(),"Seeded offline history");
            check(UdpClient.discover("127.0.0.1",server.discoveryPort()).get(5,TimeUnit.SECONDS).stream().anyMatch(o->o.serverId().equals(hello.get("serverId"))),"UDP discovery");
            ua=new UdpClient("127.0.0.1",server.typingPort());ub=new UdpClient("127.0.0.1",server.typingPort());
            ua.configure(map(loginA.get("udp")),alice.serverTime());ub.configure(map(loginB.get("udp")),bob.serverTime());UdpClient finalUa=ua,finalUb=ub;
            await(()->finalUa.isRegistered()&&finalUb.isRegistered(),5000);CountDownLatch typed=new CountDownLatch(1);
            ub.onTyping(e->{if(number(e,"conversationId")==cid&&Boolean.TRUE.equals(e.get("typing")))typed.countDown();});ua.typing(cid,true);check(typed.await(3,TimeUnit.SECONDS),"UDP typing");
            String key=UUID.randomUUID().toString();Map<String,Object> text=Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","SafeChat kiểm thử bản đóng gói: TCP/TLS lưu tin nhắn, UDP báo đang nhập.");
            var ack=alice.request("MESSAGE_SEND",text,key).get(5,TimeUnit.SECONDS);var again=alice.request("MESSAGE_SEND",text,key).get(5,TimeUnit.SECONDS);check(ack.get("messageId").equals(again.get("messageId")),"Deduplicated retry");
            var image=new BufferedImage(160,90,BufferedImage.TYPE_INT_RGB);Graphics2D ig=image.createGraphics();ig.setColor(new Color(30,80,120));ig.fillRect(0,0,160,90);ig.dispose();var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);byte[] png=out.toByteArray();
            String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png));
            var upload=alice.request("MEDIA_UPLOAD_INIT",Map.of("purpose","CHAT_FILE","fileName","ui-fixture.png","mimeType","image/png","size",png.length,"sha256",sha),UUID.randomUUID().toString()).get(5,TimeUnit.SECONDS);
            request(alice,"MEDIA_UPLOAD_CHUNK",Map.of("transferId",upload.get("transferId"),"offset",0,"data",Base64.getEncoder().encodeToString(png)));
            request(alice,"MEDIA_UPLOAD_COMMIT",Map.of("transferId",upload.get("transferId")));Object id=upload.get("mediaId");SocialClient finalAlice=alice;
            await(()->{try{return "WARNING".equals(request(finalAlice,"MEDIA_IMAGE_REVIEW_GET",Map.of("mediaId",id)).get("status"));}catch(Exception e){throw new RuntimeException(e);}},5000);
            request(alice,"MEDIA_IMAGE_CONFIRM",Map.of("mediaId",id));
            alice.request("MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Ảnh mẫu cho kiểm thử cảnh báo. Nhãn WARNING là dữ liệu giả lập, không phải nhận diện AI thật.","mediaId",id),UUID.randomUUID().toString()).get(5,TimeUnit.SECONDS);
            boolean gated=false;try{request(bob,"MEDIA_DOWNLOAD_INIT",Map.of("mediaId",id));}catch(Exception expected){gated=true;}check(gated,"Warning download acknowledgment required");
            var conversations=rows(request(alice,"CONVERSATION_LIST",Map.of("limit",20)).get("items"));Map<String,Object> direct=conversations.stream().filter(c->"DIRECT".equals(c.get("kind"))).findFirst().orElseThrow();
            Map<String,Object> userA=a;SocialClient liveAlice=alice;
            SwingUtilities.invokeAndWait(()->{try{FlatDarkLaf.setup();SocialWindow w=new SocialWindow();window[0]=w;w.addNotify();w.validate();paint(w,qa.resolve("login.png"));
                set(w,"client",liveAlice);set(w,"authenticated",true);set(w,"currentUser",new LinkedHashMap<>(userA));set(w,"host","127.0.0.1");set(w,"port",server.tcpPort());
                Object cache=get(w,"thumbnails");Method bind=cache.getClass().getDeclaredMethod("bind",SocialClient.class);bind.setAccessible(true);bind.invoke(cache,liveAlice);
                call(w,"showMain",new Class<?>[0]);call(w,"openChat",new Class<?>[]{Map.class},direct);w.validate();
            }catch(Exception e){throw new RuntimeException(e);}});
            await(()->edtContains(window[0],"Nhãn WARNING"),6000);Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{try{layout(window[0].getContentPane());JScrollPane scroll=(JScrollPane)get(window[0],"chatScroll");scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(()->{try{paint(window[0],qa.resolve("chat-warning.png"));}catch(Exception e){throw new RuntimeException(e);}});
            evidence.put("tlsAuth",true);evidence.put("history",true);evidence.put("udpDiscovery",true);evidence.put("udpTyping",true);evidence.put("retryDeduplication",true);evidence.put("warningGate",true);evidence.put("swingPaint",List.of("login.png","chat-warning.png"));evidence.put("runAt",java.time.Instant.now().toString());
            Files.writeString(qa.resolve("evidence.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));System.out.println("PASS SafeChat packaged TLS/UDP, seed, warning fixture and Swing paints");
        } finally {
            if(window[0]!=null)SwingUtilities.invokeAndWait(()->{try{call(window[0],"shutdown",new Class<?>[0]);window[0].dispose();}catch(Exception ignored){}});
            if(ua!=null)ua.close();if(ub!=null)ub.close();if(alice!=null)alice.close();if(bob!=null)bob.close();server.close();serving.join(5000);check(!serving.isAlive(),"Clean server shutdown");
        }
    }
    static Map<String,Object> request(SocialClient c,String t,Map<String,Object> p)throws Exception{return c.request(t,p,UUID.randomUUID().toString()).get(5,TimeUnit.SECONDS);}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Object o){return o instanceof List<?> l?(List<Map<String,Object>>)(List<?>)l:List.of();}
    static long number(Map<String,Object> m,String...keys){for(String k:keys)if(m.get(k) instanceof Number n)return n.longValue();return 0;}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void await(BooleanSupplier b,long ms)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(System.nanoTime()<end){if(b.getAsBoolean())return;Thread.sleep(25);}throw new AssertionError("Runtime/UI deadline");}
    static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String n,Class<?>[] t,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,args);}
    static boolean edtContains(SocialWindow w,String text){var found=new AtomicReference<>(false);try{SwingUtilities.invokeAndWait(()->found.set(contains(w.getContentPane(),text)));return found.get();}catch(Exception e){throw new RuntimeException(e);}}
    static boolean contains(Container c,String text){for(Component child:c.getComponents()){if(child instanceof JTextArea a&&a.getText().contains(text))return true;if(child instanceof JLabel l&&l.getText().contains(text))return true;if(child instanceof Container nested&&contains(nested,text))return true;}return false;}
    static void layout(Container c){c.doLayout();for(Component component:c.getComponents())if(component instanceof Container child)layout(child);}
    static void paint(SocialWindow w,Path path)throws Exception{w.validate();layout(w.getContentPane());var image=new BufferedImage(w.getContentPane().getWidth(),w.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();w.getContentPane().printAll(g);g.dispose();ImageIO.write(image,"png",path.toFile());}
}
