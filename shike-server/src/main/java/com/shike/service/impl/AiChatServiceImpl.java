package com.shike.service.impl;

import com.shike.context.BaseContext;
import com.shike.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    /** Python Agent 服务地址（同机部署，所以是回环地址） */
    @Value("${shike.ai.python-base:http://127.0.0.1:8000}")
    private String pythonBase;

    @Autowired
    @Qualifier("aiRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public String chat(List<Map<String, Object>> messages, String sessionId) {

        // ★ 当前登录用户 —— 由 JwtTokenUserInterceptor 放进 BaseContext
        //   AI 要"以这个用户的名义"加购物车，所以必须带过去，不能让模型自己编。
        Long userId = BaseContext.getCurrentId();

        Map<String, Object> req = new HashMap<>();
        req.put("messages", messages);
        req.put("user_id", userId == null ? 0L : userId);
        // 会话 id：Python 侧按它区分"谁的记忆"。为空时 Python 会按用户退化成单会话。
        req.put("session_id", sessionId == null ? "" : sessionId);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    pythonBase + "/chat", req, Map.class);

            Map<?, ?> body = resp.getBody();
            Object reply = (body == null) ? null : body.get("reply");
            return (reply == null) ? "AI 没有返回内容" : String.valueOf(reply);

        } catch (Exception e) {
            // ★ 降级：把异常变成一句人话返回给前端。
            //   不要抛出去 —— 那样前端只会看到一个 500，用户不知道发生了什么。
            log.error("调用 AI 服务失败：{}", e.getMessage());
            return "AI 服务暂时不可用，请稍后再试。";
        }
    }
}
