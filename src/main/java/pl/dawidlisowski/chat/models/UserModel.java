package pl.dawidlisowski.chat.models;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

@Data
public class UserModel {
    private WebSocketSession session;
    private String nickname;
    private boolean isAdmin;
    private boolean isMuted;

    public UserModel(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserModel userModel = (UserModel) o;
        return Objects.equals(session.getId(), userModel.session.getId())
                && Objects.equals(nickname, userModel.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(session.getId(), nickname);
    }
}
