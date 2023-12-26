package cn.wolfcode.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description:
 * @author: 刘
 * @date: 2023年12月26日 下午 4:37
 */
@ServerEndpoint("/{token}")
@Component
@Slf4j
public class WebsocketServer {
    public static Map<String,Session> SESSION_MAP=new ConcurrentHashMap<>();
    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token){
        log.info("[websocket]有新的用户连接上来了：{}",token);
        SESSION_MAP.put(token,session);

    }
    @OnClose
    public void onClose(@PathParam("token") String token){
        log.info("[websocket]连接关闭：{}",token);
        SESSION_MAP.remove(token);
    }
    @OnError
    public void OnError(Throwable throwable){
        log.error("{}",throwable);
    }
}
