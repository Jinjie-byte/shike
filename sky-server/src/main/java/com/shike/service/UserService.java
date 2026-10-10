package com.shike.service;

import com.shike.dto.UserLoginDTO;
import com.shike.entity.User;

public interface UserService {
    User wxLogin(UserLoginDTO userLoginDTO);
}
