package com.p2p.server.ops;
import com.p2p.server.social.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Fictional text-only demo; no images are pre-approved or attached outside the AI policy. */
public final class SafeChatDemoSeedTool {
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!"--config".equals(args[0]))throw new IllegalArgumentException("--config <properties>");
        String password=System.getenv("SOCIAL_DEMO_PASSWORD");
        if(password==null||password.length()<10)throw new IllegalArgumentException("Set SOCIAL_DEMO_PASSWORD (at least 10 characters)");
        Path file=Path.of(args[1]).toAbsolutePath();Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
        Path base=file.getParent().resolve(p.getProperty("app.root","../..")).normalize();
        seed(base.resolve(p.getProperty("database.path","data/social-network.db")),password);
        System.out.println("SafeChat demo accounts: demo_alice, demo_bob, demo_carol. Password comes from SOCIAL_DEMO_PASSWORD.");
    }
    public static void seed(Path database,String password)throws Exception {
        try(ApplicationLock lock=ApplicationLock.acquire(database.toAbsolutePath().getParent());SocialDatabase db=new SocialDatabase(database)) {
            boolean empty=db.read(c->{try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM users")){return r.next()&&r.getInt(1)==0;}});
            if(!empty)throw new IllegalStateException("Demo requires an empty database. Existing data was preserved.");
            SocialService social=new SocialService(db);Map<String,Long> users=new LinkedHashMap<>();
            for(String name:List.of("alice","bob","carol"))users.put(name,((Number)social.register(Map.of("username","demo_"+name,"displayName","Demo "+name,"password",password)).get("userId")).longValue());
            long alice=users.get("alice"),bob=users.get("bob"),carol=users.get("carol");
            for(long friend:List.of(bob,carol)) {
                var r=command(social,alice,"FRIEND_REQUEST",Map.of("targetUserId",friend));
                command(social,friend,"FRIEND_ACCEPT",Map.of("relationId",r.get("relationId"),"expectedVersion",r.get("version")));
            }
            Object cid=command(social,alice,"CONVERSATION_DIRECT_GET_OR_CREATE",Map.of("targetUserId",bob)).get("conversationId");
            command(social,alice,"MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Chào Bob, đây là cuộc trò chuyện mẫu của SafeChat."));
            command(social,bob,"MESSAGE_SEND",Map.of("conversationId",cid,"clientMessageId",UUID.randomUUID().toString(),"content","Tin nhắn được lưu trên server. Ảnh cần AI kiểm tra trước khi gửi."));
            Object group=command(social,alice,"GROUP_CREATE",Map.of("title","Nhóm học tập SafeChat")).get("conversationId");
            for(long member:List.of(bob,carol)) {
                var invite=command(social,alice,"GROUP_INVITE",Map.of("conversationId",group,"targetUserId",member));
                command(social,member,"GROUP_INVITE_ACCEPT",Map.of("inviteId",invite.get("inviteId")));
            }
            command(social,carol,"MESSAGE_SEND",Map.of("conversationId",group,"clientMessageId",UUID.randomUUID().toString(),"content","Nhóm dùng TCP/TLS để gửi tin nhắn và UDP cho trạng thái đang nhập."));
        }
    }
    private static Map<String,Object> command(SocialService s,long actor,String type,Map<String,Object> p)throws Exception{return s.execute(actor,type,p,UUID.randomUUID().toString());}
}
