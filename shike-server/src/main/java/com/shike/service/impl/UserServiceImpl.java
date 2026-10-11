package com.shike.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.shike.constant.MessageConstant;
import com.shike.dto.UserLoginDTO;
import com.shike.entity.User;
import com.shike.exception.LoginFailedException;
import com.shike.mapper.UserMapper;
import com.shike.properties.JwtProperties;
import com.shike.properties.WeChatProperties;
import com.shike.service.UserService;
import com.shike.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session?";
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private JwtProperties jwtProperties;
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        // 调用微信接口服务，获取当前用户的openid
        String openid = getOpenId(userLoginDTO.getCode());
        // 判断openid是否存在
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        //判断当前用户是否为新用户
        User user = userMapper.getByOpenId(openid);

        //如果是新用户，自动注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        //返回用户对象
        return user;
    }

    /**
     * 获取微信用户的openid
     * @param code 微信登录返回的code
     * @return  openid
     */
    public String getOpenId(String code) {
        // 开发开关：本地演示时跳过微信接口，直接返回固定 openid（appid/secret 为占位符时微信会返回 40013）
        if (weChatProperties.isEnableMock()) {
            log.warn("微信登录处于 mock 模式，跳过 jscode2session，直接返回 openid={}", weChatProperties.getMockOpenid());
            return weChatProperties.getMockOpenid();
        }
        // 调用微信接口服务，获取当前用户的openid
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        JSONObject jsonObject = JSONObject.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}
