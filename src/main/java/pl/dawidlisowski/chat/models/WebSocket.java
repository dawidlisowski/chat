package pl.dawidlisowski.chat.models;

import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;

@Component
@EnableWebSocket
@Log
public class WebSocket extends TextWebSocketHandler implements WebSocketConfigurer {

    private List<UserModel> userModelList = new LinkedList<>();
    private Deque<String> lastTenMessages = new ArrayDeque<>();

    @Value("${admin.nickname}")
    String ADMIN_NICKNAME;
    private boolean methodExecuted;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "/chat").setAllowedOrigins("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        userModelList.add(new UserModel(session));
        session.sendMessage(new TextMessage("Twoja pierwsza wiadomość będzie Twoim nickiem"));
        log.info("Ktoś połączył się z socketem");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        userModelList.removeIf(s -> s.getSession().getId().equals(session.getId()));
        log.info("Ktoś wyszedł");
    }

    private UserModel findUserBySession(WebSocketSession session) {
        return userModelList.stream()
                .filter(s -> s.getSession().getId().equals(session.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException());
    }

    private Optional<UserModel> findUserByNickname(String nickname) {
        return userModelList.stream()
                .filter(s -> s.getNickname() != null && s.getNickname().equals(nickname))
                .findFirst();
    }

    private boolean checkIfNicknameIsTaken(String nickname) {
        return userModelList.stream()
                .filter(s -> s.getNickname() != null)
                .anyMatch(s -> s.getNickname().equals(nickname));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserModel sender = findUserBySession(session);

        if (sender.getNickname() == null) {
            if (checkIfNicknameIsTaken(message.getPayload())) {
                sender.getSession().sendMessage(new TextMessage("Nick zajęty"));
                return;
            }
            sender.setNickname(message.getPayload());
            sender.getSession().sendMessage(new TextMessage("Twój nick to " + message.getPayload()));
            if (sender.getNickname().equals(ADMIN_NICKNAME)) {
                sender.setAdmin(true);
                sender.getSession().sendMessage(new TextMessage("Jesteś adminem"));
            }
            sendLastTenMessages(sender);
            return;
        }

        checkIfMessageIsCommand(sender, message);
        if (methodExecuted) {
            methodExecuted = false;
            return;
        }

        if (sender.isMuted()) {
            sender.getSession().sendMessage(new TextMessage(sender.getNickname() + ": " + message.getPayload()));
            return;
        }

        for (UserModel userModel : userModelList) {
            if (userModel.getNickname() != null) {
                userModel.getSession().sendMessage(new TextMessage(sender.getNickname() + ": "
                        + message.getPayload()));
            }
        }

        addMessageToQue(sender.getNickname() + ": " + message.getPayload());
    }

    private void checkIfMessageIsCommand(UserModel sender, TextMessage message) throws IOException {
        if (!sender.isAdmin()) {
            return;
        }

        if (message.getPayload().startsWith("/mute")) {
            String nickname = message.getPayload().split(" ")[1];
            Optional<UserModel> userToMuteOptional = findUserByNickname(nickname);
            UserModel userToMute;
            if (!userToMuteOptional.isPresent()) {
                sender.getSession().sendMessage(new TextMessage("Podany użytkownik nie istenieje"));
                methodExecuted = true;
                return;
            }
            userToMute = userToMuteOptional.get();
            userToMute.setMuted(true);
            sender.getSession().sendMessage(new TextMessage("użytkownik " + userToMute.getNickname() + " wyciszony"));
            methodExecuted = true;
        }
    }

    private void sendLastTenMessages(UserModel sender) throws IOException {
        for (String lastMessage : lastTenMessages) {
            sender.getSession().sendMessage(new TextMessage(lastMessage));
        }
    }

    private void addMessageToQue(String message) {
        if (lastTenMessages.size() > 9) {
            lastTenMessages.removeFirst();
        }
        lastTenMessages.addLast(message);
    }
}
