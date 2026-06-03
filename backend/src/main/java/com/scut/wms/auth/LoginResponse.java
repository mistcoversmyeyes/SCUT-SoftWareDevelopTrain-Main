package com.scut.wms.auth;

public record LoginResponse(String token, String username, String displayName) {
}
