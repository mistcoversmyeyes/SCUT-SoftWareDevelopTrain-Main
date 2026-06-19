package com.scut.wms.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String DEMO_USERNAME = "admin";
    private static final String DEMO_PASSWORD = "123456";
    private static final String DEMO_TOKEN = "demo-token-admin";
    private static final String DEMO_DISPLAY_NAME = "系统管理员";

    public LoginResponse login(LoginRequest request) {
        if (DEMO_USERNAME.equals(request.username()) && DEMO_PASSWORD.equals(request.password())) {
            return new LoginResponse(DEMO_TOKEN, DEMO_USERNAME, DEMO_DISPLAY_NAME);
        }
        throw new InvalidCredentialsException("用户名或密码错误");
    }

    public UserResponse currentUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.equals("Bearer " + DEMO_TOKEN)) {
            throw new InvalidCredentialsException("登录状态已失效");
        }
        return new UserResponse(DEMO_USERNAME, DEMO_DISPLAY_NAME);
    }
}
