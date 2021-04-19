package zhao.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@ServerEndpoint("/chat/{uid}")
@Controller
public class ChatHandler {
    @RequestMapping("/")
    public String root() {
        return "redirect:/chat.html";
    }

    static Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("uid") String uid) {
        if (ChatHandler.sessions.containsKey(uid)) {
            System.err.println("用户已存在：" + uid);
            Map<String, String> map = new HashMap<>();
            map.put("type", "err_user_exist");
            try {
                session.getBasicRemote().sendText(toJson(map));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        User user = new User(uid);
        session.getUserProperties().put("user", user);
        ChatHandler.sessions.put(uid, session);
        fireUpdateUsers();
    }

    private void fireUpdateUsers() {
        List<User> userList = sessions.values().stream().map(s -> (User) s.getUserProperties().get("user")).collect(Collectors.toList());
        Map<String, Object> message = new HashMap<>();
        message.put("type", "update_user");
        message.put("users", userList);
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        for (Session session : sessions.values()) {
            try {
                session.getBasicRemote().sendText(json);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        User user = (User) session.getUserProperties().get("user");
        sessions.remove(user.name);
        fireUpdateUsers();
    }

    private String toJson(Object value) {
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    @OnMessage
    public void onMessage(Session currentSession, String msg) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "normal_msg");
        message.put("msg", msg);
        message.put("user", currentSession.getUserProperties().get("user"));
        for (Session session : sessions.values()) {
            session.getBasicRemote().sendText(toJson(message));
        }
    }

    @OnError
    public void onError(Throwable e) {
        e.printStackTrace();
    }

    public static class User implements Serializable {
        public String name;
        public User(String name) {
            this.name = name;
        }
    }
}
