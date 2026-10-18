package com.shike.controller.user;

import com.shike.dto.AiChatRequestDTO;
import com.shike.result.Result;
import com.shike.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 用户端 AI 点单入口。
 *
 * 为什么放在 /user/** 下：
 *   它落在 JwtTokenUserInterceptor 的拦截范围里 —— 也就是说**必须登录**，
 *   而且拦截器会把 userId 放进 BaseContext，转发给 Python 时要用。
 *   不需要像 /internal/** 那样额外做鉴权。
 */
@RestController("userAiChatController")
@RequestMapping("/user/ai")
@Slf4j
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@RequestBody AiChatRequestDTO dto) {
        String reply = aiChatService.chat(dto.getMessages(), dto.getSessionId());
        return Result.success(Collections.singletonMap("reply", reply));
    }
}
