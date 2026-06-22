# Week 1 WMS Login Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Week 1 homework demo: a Vue3 + Element Plus login page connected to a Spring Boot auth API, followed by a WMS-style admin layout with side-menu and top-tab linkage.

**Architecture:** The repository will contain two independent projects: `backend/` for the Spring Boot API and `frontend/` for the Vite/Vue application. The backend keeps one in-memory demo account and exposes `/api/auth/login` plus `/api/auth/me`; the frontend stores the returned demo token in `localStorage`, protects admin routes with a router guard, and manages open tabs with Pinia.

**Tech Stack:** Java 17, Spring Boot Web, Spring Boot Validation, Maven, Vue 3, Vite, Element Plus, Vue Router, Pinia, Axios, Vitest, Vue Test Utils.

---

## Scope Check

The referenced spec covers one small but complete homework demo. It has two parts, backend and frontend, but they are coupled by one login flow and can be implemented as one plan because the final acceptance requires both services running together.

The large-project PDF in `res/0.华南理工大学授课(2).pdf` is intentionally excluded from this plan.

## File Structure

### Backend

- Create: `backend/pom.xml` - Maven project definition.
- Create: `backend/src/main/java/com/scut/wms/WmsApplication.java` - Spring Boot entry point.
- Create: `backend/src/main/java/com/scut/wms/auth/AuthController.java` - `/api/auth` HTTP endpoints.
- Create: `backend/src/main/java/com/scut/wms/auth/AuthService.java` - demo account and token validation.
- Create: `backend/src/main/java/com/scut/wms/auth/LoginRequest.java` - login request DTO with validation.
- Create: `backend/src/main/java/com/scut/wms/auth/LoginResponse.java` - login response DTO.
- Create: `backend/src/main/java/com/scut/wms/auth/UserResponse.java` - current-user response DTO.
- Create: `backend/src/main/java/com/scut/wms/auth/ErrorResponse.java` - error response DTO.
- Create: `backend/src/main/java/com/scut/wms/auth/InvalidCredentialsException.java` - auth failure exception.
- Create: `backend/src/main/java/com/scut/wms/config/CorsConfig.java` - Vite dev-server CORS configuration.
- Create: `backend/src/main/java/com/scut/wms/config/GlobalExceptionHandler.java` - validation and auth error responses.
- Create: `backend/src/test/java/com/scut/wms/auth/AuthControllerTest.java` - controller tests with MockMvc.

### Frontend

- Create: `frontend/package.json` - npm scripts and dependencies.
- Create: `frontend/index.html` - Vite HTML entry.
- Create: `frontend/vite.config.js` - Vue plugin, Vitest jsdom environment, backend proxy.
- Create: `frontend/src/main.js` - Vue app bootstrap.
- Create: `frontend/src/App.vue` - root router view.
- Create: `frontend/src/menu.js` - WMS menu/page metadata shared by routes and tabs.
- Create: `frontend/src/api/http.js` - Axios instance and token injection.
- Create: `frontend/src/api/auth.js` - auth API wrapper.
- Create: `frontend/src/stores/auth.js` - login state, localStorage persistence, logout.
- Create: `frontend/src/stores/tabs.js` - open tabs and active tab behavior.
- Create: `frontend/src/router/index.js` - route table and auth guard.
- Create: `frontend/src/views/LoginView.vue` - login form.
- Create: `frontend/src/views/MainLayout.vue` - admin shell.
- Create: `frontend/src/views/PlaceholderPage.vue` - reusable WMS placeholder page.
- Create: `frontend/src/components/SideMenu.vue` - left menu.
- Create: `frontend/src/components/TabBar.vue` - top tabs.
- Create: `frontend/src/styles/main.css` - shared styling.
- Create: `frontend/src/stores/tabs.test.js` - tab behavior unit tests.
- Create: `frontend/src/stores/auth.test.js` - auth store unit tests.
- Modify: `.gitignore` - ignore generated dependency/build files.

---

### Task 1: Backend Maven Skeleton

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/scut/wms/WmsApplication.java`
- Create: `backend/src/test/java/com/scut/wms/WmsApplicationTest.java`
- Modify: `.gitignore`

- [ ] **Step 1: Create backend project file**

Create `backend/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.scut</groupId>
    <artifactId>wms-week1-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>wms-week1-backend</name>
    <description>Week 1 WMS login demo backend</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Spring Boot entry point**

Create `backend/src/main/java/com/scut/wms/WmsApplication.java`:

```java
package com.scut.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
```

- [ ] **Step 3: Write context-load test**

Create `backend/src/test/java/com/scut/wms/WmsApplicationTest.java`:

```java
package com.scut.wms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WmsApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Update root ignore rules**

Replace `.gitignore` with:

```gitignore
node_modules/
dist/
target/
.idea/
.vscode/
*.log
```

- [ ] **Step 5: Run backend skeleton test**

Run:

```bash
cd backend
mvn test
```

Expected: build succeeds and `WmsApplicationTest` passes.

- [ ] **Step 6: Commit backend skeleton**

Run:

```bash
git add .gitignore backend/pom.xml backend/src/main/java/com/scut/wms/WmsApplication.java backend/src/test/java/com/scut/wms/WmsApplicationTest.java
git commit -m "chore: scaffold Spring Boot backend"
```

---

### Task 2: Backend Auth API

**Files:**
- Create: `backend/src/main/java/com/scut/wms/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/scut/wms/auth/LoginResponse.java`
- Create: `backend/src/main/java/com/scut/wms/auth/UserResponse.java`
- Create: `backend/src/main/java/com/scut/wms/auth/ErrorResponse.java`
- Create: `backend/src/main/java/com/scut/wms/auth/InvalidCredentialsException.java`
- Create: `backend/src/main/java/com/scut/wms/auth/AuthService.java`
- Create: `backend/src/main/java/com/scut/wms/auth/AuthController.java`
- Create: `backend/src/main/java/com/scut/wms/config/CorsConfig.java`
- Create: `backend/src/main/java/com/scut/wms/config/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/scut/wms/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing auth controller tests**

Create `backend/src/test/java/com/scut/wms/auth/AuthControllerTest.java`:

```java
package com.scut.wms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturnsTokenForDemoAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("demo-token-admin"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.displayName").value("系统管理员"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void loginRejectsBlankUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
    }

    @Test
    void meReturnsUserForValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer demo-token-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.displayName").value("系统管理员"));
    }

    @Test
    void meRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录状态已失效"));
    }
}
```

- [ ] **Step 2: Run auth tests to verify failure**

Run:

```bash
cd backend
mvn test -Dtest=AuthControllerTest
```

Expected: FAIL because `AuthController` and related classes do not exist.

- [ ] **Step 3: Create auth DTOs and exception**

Create `backend/src/main/java/com/scut/wms/auth/LoginRequest.java`:

```java
package com.scut.wms.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
```

Create `backend/src/main/java/com/scut/wms/auth/LoginResponse.java`:

```java
package com.scut.wms.auth;

public record LoginResponse(String token, String username, String displayName) {
}
```

Create `backend/src/main/java/com/scut/wms/auth/UserResponse.java`:

```java
package com.scut.wms.auth;

public record UserResponse(String username, String displayName) {
}
```

Create `backend/src/main/java/com/scut/wms/auth/ErrorResponse.java`:

```java
package com.scut.wms.auth;

public record ErrorResponse(String message) {
}
```

Create `backend/src/main/java/com/scut/wms/auth/InvalidCredentialsException.java`:

```java
package com.scut.wms.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create auth service**

Create `backend/src/main/java/com/scut/wms/auth/AuthService.java`:

```java
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
```

- [ ] **Step 5: Create auth controller**

Create `backend/src/main/java/com/scut/wms/auth/AuthController.java`:

```java
package com.scut.wms.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.currentUser(authorization);
    }
}
```

- [ ] **Step 6: Create CORS and exception handling**

Create `backend/src/main/java/com/scut/wms/config/CorsConfig.java`:

```java
package com.scut.wms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
```

Create `backend/src/main/java/com/scut/wms/config/GlobalExceptionHandler.java`:

```java
package com.scut.wms.config;

import com.scut.wms.auth.ErrorResponse;
import com.scut.wms.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不正确" : error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(exception.getMessage()));
    }
}
```

- [ ] **Step 7: Run backend auth tests**

Run:

```bash
cd backend
mvn test
```

Expected: PASS. `AuthControllerTest` verifies successful login, failed login, validation, and `/api/auth/me`.

- [ ] **Step 8: Commit backend auth API**

Run:

```bash
git add backend/src/main/java/com/scut/wms/auth backend/src/main/java/com/scut/wms/config backend/src/test/java/com/scut/wms/auth/AuthControllerTest.java
git commit -m "feat: add demo auth API"
```

---

### Task 3: Frontend Vite Skeleton

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.js`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/styles/main.css`

- [ ] **Step 1: Create frontend package file**

Create `frontend/package.json`:

```json
{
  "name": "wms-week1-frontend",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0",
    "build": "vite build",
    "test": "vitest run"
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.7.7",
    "element-plus": "^2.8.8",
    "pinia": "^2.2.6",
    "vue": "^3.5.12",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^25.0.1",
    "vite": "^5.4.10",
    "vitest": "^2.1.4"
  }
}
```

- [ ] **Step 2: Create Vite entry files**

Create `frontend/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>汽车物流 WMS</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

Create `frontend/vite.config.js`:

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
})
```

- [ ] **Step 3: Create root app files**

Create `frontend/src/main.js`:

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

Create `frontend/src/App.vue`:

```vue
<template>
  <router-view />
</template>
```

Create `frontend/src/styles/main.css`:

```css
* {
  box-sizing: border-box;
}

html,
body,
#app {
  width: 100%;
  min-height: 100%;
  margin: 0;
}

body {
  font-family: Inter, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
  color: #1f2937;
  background: #f5f7fb;
}

button,
input {
  font: inherit;
}
```

- [ ] **Step 4: Install frontend dependencies**

Run:

```bash
cd frontend
npm install
```

Expected: `frontend/package-lock.json` is created and dependencies install successfully.

- [ ] **Step 5: Run frontend build to expose missing router**

Run:

```bash
cd frontend
npm run build
```

Expected: FAIL because `src/router/index.js` does not exist yet.

- [ ] **Step 6: Commit frontend skeleton**

Run:

```bash
git add frontend/package.json frontend/package-lock.json frontend/index.html frontend/vite.config.js frontend/src/main.js frontend/src/App.vue frontend/src/styles/main.css
git commit -m "chore: scaffold Vue frontend"
```

---

### Task 4: Frontend Auth API, Store, and Router Guard

**Files:**
- Create: `frontend/src/api/http.js`
- Create: `frontend/src/api/auth.js`
- Create: `frontend/src/stores/auth.js`
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/MainLayout.vue`
- Create: `frontend/src/views/PlaceholderPage.vue`
- Create: `frontend/src/stores/auth.test.js`

- [ ] **Step 1: Write failing auth store tests**

Create `frontend/src/stores/auth.test.js`:

```js
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import * as authApi from '../api/auth'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  fetchMe: vi.fn()
}))

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('stores token and user after login', async () => {
    authApi.login.mockResolvedValue({
      token: 'demo-token-admin',
      username: 'admin',
      displayName: '系统管理员'
    })

    const auth = useAuthStore()
    await auth.login('admin', '123456')

    expect(auth.token).toBe('demo-token-admin')
    expect(auth.user.displayName).toBe('系统管理员')
    expect(localStorage.getItem('wms-token')).toBe('demo-token-admin')
  })

  it('clears state on logout', () => {
    localStorage.setItem('wms-token', 'demo-token-admin')
    localStorage.setItem('wms-user', JSON.stringify({ username: 'admin', displayName: '系统管理员' }))

    const auth = useAuthStore()
    auth.logout()

    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('wms-token')).toBeNull()
  })
})
```

- [ ] **Step 2: Run auth store test to verify failure**

Run:

```bash
cd frontend
npm run test -- src/stores/auth.test.js
```

Expected: FAIL because `src/stores/auth.js` and `src/api/auth.js` do not exist.

- [ ] **Step 3: Create Axios API wrapper**

Create `frontend/src/api/http.js`:

```js
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 5000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wms-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

Create `frontend/src/api/auth.js`:

```js
import { http } from './http'

export async function login(username, password) {
  const response = await http.post('/auth/login', { username, password })
  return response.data
}

export async function fetchMe() {
  const response = await http.get('/auth/me')
  return response.data
}
```

- [ ] **Step 4: Create auth store**

Create `frontend/src/stores/auth.js`:

```js
import { defineStore } from 'pinia'
import { fetchMe, login as loginApi } from '../api/auth'

function readStoredUser() {
  const raw = localStorage.getItem('wms-user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem('wms-user')
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('wms-token') || '',
    user: readStoredUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token)
  },
  actions: {
    async login(username, password) {
      const data = await loginApi(username, password)
      this.token = data.token
      this.user = {
        username: data.username,
        displayName: data.displayName
      }
      localStorage.setItem('wms-token', data.token)
      localStorage.setItem('wms-user', JSON.stringify(this.user))
    },
    async loadCurrentUser() {
      const user = await fetchMe()
      this.user = user
      localStorage.setItem('wms-user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('wms-token')
      localStorage.removeItem('wms-user')
    }
  }
})
```

- [ ] **Step 5: Create temporary route components**

Create `frontend/src/views/LoginView.vue`:

```vue
<template>
  <main class="login-page">
    <section class="login-panel">
      <h1>汽车物流 WMS</h1>
      <p>第一周登录演示</p>
    </section>
  </main>
</template>
```

Create `frontend/src/views/MainLayout.vue`:

```vue
<template>
  <div class="layout-shell">
    <router-view />
  </div>
</template>
```

Create `frontend/src/views/PlaceholderPage.vue`:

```vue
<template>
  <section class="placeholder-page">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  }
})
</script>
```

- [ ] **Step 6: Create router with auth guard**

Create `frontend/src/router/index.js`:

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'

const pageMap = {
  dashboard: {
    title: '首页',
    description: '查看仓储运行概览、待办事项和基础统计。'
  },
  materials: {
    title: '物料信息',
    description: '维护汽车零部件物料编码、名称、规格和单位。'
  },
  inbound: {
    title: '入库管理',
    description: '跟踪采购到货、质检完成和上架入库流程。'
  },
  inventory: {
    title: '库存监控',
    description: '查看当前库存数量、安全库存和库位状态。'
  },
  outbound: {
    title: '出库管理',
    description: '处理销售出库、领料出库和发运状态。'
  }
}

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: Object.entries(pageMap).map(([key, page]) => ({
      path: key,
      name: key,
      component: PlaceholderPage,
      meta: { requiresAuth: true, tabKey: key, title: page.title },
      props: {
        title: page.title,
        description: page.description
      }
    }))
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
```

- [ ] **Step 7: Run frontend tests and build**

Run:

```bash
cd frontend
npm run test -- src/stores/auth.test.js
npm run build
```

Expected: test passes and build succeeds with a minimal placeholder UI.

- [ ] **Step 8: Commit frontend auth foundation**

Run:

```bash
git add frontend/src/api frontend/src/stores/auth.js frontend/src/stores/auth.test.js frontend/src/router frontend/src/views
git commit -m "feat: add frontend auth foundation"
```

---

### Task 5: Login View

**Files:**
- Modify: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: Replace login view with Element Plus form**

Replace `frontend/src/views/LoginView.vue` with:

```vue
<template>
  <main class="login-page">
    <section class="login-visual">
      <p class="eyebrow">SCUT 软件开发实训</p>
      <h1>汽车物流 WMS</h1>
      <p class="summary">完成 Vue3 与 Spring Boot 的登录打通，进入后台查看菜单与标签页联动。</p>
    </section>

    <section class="login-panel">
      <h2>系统登录</h2>
      <p class="hint">演示账号：admin / 123456</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submitLogin">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: 'admin',
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }]
}

async function submitLogin() {
  errorMessage.value = ''
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push(route.query.redirect || '/dashboard')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '登录请求失败，请确认后端服务已启动'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  background: linear-gradient(135deg, #eef6ff 0%, #f8fafc 52%, #ecfdf5 100%);
}

.login-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 72px;
}

.eyebrow {
  margin: 0 0 16px;
  color: #0f766e;
  font-weight: 700;
}

.login-visual h1 {
  margin: 0;
  color: #111827;
  font-size: 56px;
  letter-spacing: 0;
}

.summary {
  max-width: 560px;
  margin: 24px 0 0;
  color: #475569;
  font-size: 18px;
  line-height: 1.8;
}

.login-panel {
  align-self: center;
  margin-right: 72px;
  padding: 32px;
  background: #ffffff;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.12);
}

.login-panel h2 {
  margin: 0;
  font-size: 26px;
}

.hint {
  margin: 8px 0 24px;
  color: #64748b;
}

.login-button {
  width: 100%;
  margin-top: 18px;
}
</style>
```

- [ ] **Step 2: Build frontend login page**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS. The login page compiles and Element Plus components resolve.

- [ ] **Step 3: Commit login view**

Run:

```bash
git add frontend/src/views/LoginView.vue
git commit -m "feat: build login page"
```

---

### Task 6: Menu, Tabs, and Admin Layout

**Files:**
- Create: `frontend/src/menu.js`
- Create: `frontend/src/stores/tabs.js`
- Create: `frontend/src/stores/tabs.test.js`
- Create: `frontend/src/components/SideMenu.vue`
- Create: `frontend/src/components/TabBar.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/views/MainLayout.vue`
- Modify: `frontend/src/views/PlaceholderPage.vue`

- [ ] **Step 1: Write failing tabs store tests**

Create `frontend/src/stores/tabs.test.js`:

```js
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTabsStore } from './tabs'

describe('tabs store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('opens dashboard by default', () => {
    const tabs = useTabsStore()
    expect(tabs.openTabs).toEqual([{ key: 'dashboard', title: '首页', path: '/dashboard', closable: false }])
    expect(tabs.activeKey).toBe('dashboard')
  })

  it('adds a menu tab once and activates it', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })

    expect(tabs.openTabs).toHaveLength(2)
    expect(tabs.activeKey).toBe('materials')
  })

  it('switches to neighbor after closing active tab', () => {
    const tabs = useTabsStore()
    tabs.openTab({ key: 'materials', title: '物料信息', path: '/materials', closable: true })
    const next = tabs.closeTab('materials')

    expect(next.path).toBe('/dashboard')
    expect(tabs.activeKey).toBe('dashboard')
  })
})
```

- [ ] **Step 2: Run tabs test to verify failure**

Run:

```bash
cd frontend
npm run test -- src/stores/tabs.test.js
```

Expected: FAIL because `src/stores/tabs.js` does not exist.

- [ ] **Step 3: Create shared menu metadata**

Create `frontend/src/menu.js`:

```js
import { Box, DataAnalysis, HomeFilled, Tickets, Van } from '@element-plus/icons-vue'

export const menuItems = [
  {
    key: 'dashboard',
    title: '首页',
    path: '/dashboard',
    icon: HomeFilled,
    description: '查看仓储运行概览、待办事项和基础统计。',
    fields: ['今日入库：12 单', '今日出库：8 单', '库存预警：3 项']
  },
  {
    key: 'materials',
    title: '物料信息',
    path: '/materials',
    icon: Box,
    description: '维护汽车零部件物料编码、名称、规格和单位。',
    fields: ['物料编码', '物料名称', '规格型号', '计量单位']
  },
  {
    key: 'inbound',
    title: '入库管理',
    path: '/inbound',
    icon: Tickets,
    description: '跟踪采购到货、质检完成和上架入库流程。',
    fields: ['入库单号', '供应商', '到货数量', '质检状态']
  },
  {
    key: 'inventory',
    title: '库存监控',
    path: '/inventory',
    icon: DataAnalysis,
    description: '查看当前库存数量、安全库存和库位状态。',
    fields: ['当前库存', '安全库存', '库位编号', '预警状态']
  },
  {
    key: 'outbound',
    title: '出库管理',
    path: '/outbound',
    icon: Van,
    description: '处理销售出库、领料出库和发运状态。',
    fields: ['出库单号', '客户名称', '拣货状态', '发运状态']
  }
]

export function findMenuItem(key) {
  return menuItems.find((item) => item.key === key)
}
```

- [ ] **Step 4: Create tabs store**

Create `frontend/src/stores/tabs.js`:

```js
import { defineStore } from 'pinia'

const dashboardTab = {
  key: 'dashboard',
  title: '首页',
  path: '/dashboard',
  closable: false
}

export const useTabsStore = defineStore('tabs', {
  state: () => ({
    openTabs: [dashboardTab],
    activeKey: dashboardTab.key
  }),
  actions: {
    openTab(tab) {
      if (!this.openTabs.some((item) => item.key === tab.key)) {
        this.openTabs.push(tab)
      }
      this.activeKey = tab.key
    },
    activateTab(key) {
      if (this.openTabs.some((item) => item.key === key)) {
        this.activeKey = key
      }
    },
    closeTab(key) {
      const index = this.openTabs.findIndex((item) => item.key === key)
      if (index === -1 || !this.openTabs[index].closable) {
        return this.openTabs.find((item) => item.key === this.activeKey)
      }

      const wasActive = this.activeKey === key
      this.openTabs.splice(index, 1)

      if (wasActive) {
        const nextIndex = Math.min(index, this.openTabs.length - 1)
        const nextTab = this.openTabs[nextIndex] || dashboardTab
        this.activeKey = nextTab.key
        return nextTab
      }

      return this.openTabs.find((item) => item.key === this.activeKey)
    },
    resetTabs() {
      this.openTabs = [dashboardTab]
      this.activeKey = dashboardTab.key
    }
  }
})
```

- [ ] **Step 5: Create side menu**

Create `frontend/src/components/SideMenu.vue`:

```vue
<template>
  <el-menu class="side-menu" :default-active="activeKey" @select="handleSelect">
    <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.title }}</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuItems, findMenuItem } from '../menu'
import { useTabsStore } from '../stores/tabs'

const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const activeKey = computed(() => route.meta.tabKey || tabs.activeKey)

function handleSelect(key) {
  const item = findMenuItem(key)
  if (!item) {
    return
  }
  tabs.openTab({
    key: item.key,
    title: item.title,
    path: item.path,
    closable: item.key !== 'dashboard'
  })
  router.push(item.path)
}
</script>

<style scoped>
.side-menu {
  height: 100%;
  border-right: 0;
}
</style>
```

- [ ] **Step 6: Create tab bar**

Create `frontend/src/components/TabBar.vue`:

```vue
<template>
  <el-tabs
    class="tab-bar"
    type="card"
    :model-value="tabs.activeKey"
    @tab-change="handleChange"
    @tab-remove="handleRemove"
  >
    <el-tab-pane
      v-for="tab in tabs.openTabs"
      :key="tab.key"
      :label="tab.title"
      :name="tab.key"
      :closable="tab.closable"
    />
  </el-tabs>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useTabsStore } from '../stores/tabs'

const router = useRouter()
const tabs = useTabsStore()

function handleChange(key) {
  tabs.activateTab(key)
  const tab = tabs.openTabs.find((item) => item.key === key)
  if (tab) {
    router.push(tab.path)
  }
}

function handleRemove(key) {
  const nextTab = tabs.closeTab(key)
  if (nextTab) {
    router.push(nextTab.path)
  }
}
</script>

<style scoped>
.tab-bar {
  padding: 8px 16px 0;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}
</style>
```

- [ ] **Step 7: Replace router to use shared menu**

Replace `frontend/src/router/index.js` with:

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import { menuItems } from '../menu'
import LoginView from '../views/LoginView.vue'
import MainLayout from '../views/MainLayout.vue'
import PlaceholderPage from '../views/PlaceholderPage.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: menuItems.map((item) => ({
      path: item.path.slice(1),
      name: item.key,
      component: PlaceholderPage,
      meta: { requiresAuth: true, tabKey: item.key, title: item.title },
      props: {
        title: item.title,
        description: item.description,
        fields: item.fields
      }
    }))
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  return true
})

router.afterEach((to) => {
  if (!to.meta.tabKey) {
    return
  }
  const tabs = useTabsStore()
  tabs.openTab({
    key: to.meta.tabKey,
    title: to.meta.title,
    path: to.path,
    closable: to.meta.tabKey !== 'dashboard'
  })
})

export default router
```

- [ ] **Step 8: Replace admin layout**

Replace `frontend/src/views/MainLayout.vue` with:

```vue
<template>
  <el-container class="admin-layout">
    <el-header class="admin-header">
      <div>
        <strong>汽车物流 WMS</strong>
        <span>第一周作业演示</span>
      </div>
      <div class="header-user">
        <span>{{ auth.user?.displayName || '系统管理员' }}</span>
        <el-button size="small" @click="logout">退出登录</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="admin-aside">
        <SideMenu />
      </el-aside>
      <el-container>
        <TabBar />
        <el-main class="admin-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import SideMenu from '../components/SideMenu.vue'
import TabBar from '../components/TabBar.vue'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'

const router = useRouter()
const auth = useAuthStore()
const tabs = useTabsStore()

function logout() {
  auth.logout()
  tabs.resetTabs()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #ffffff;
  background: #0f172a;
}

.admin-header strong {
  margin-right: 16px;
  font-size: 18px;
}

.admin-header span {
  color: #cbd5e1;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-aside {
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.admin-main {
  padding: 24px;
}
</style>
```

- [ ] **Step 9: Replace placeholder page**

Replace `frontend/src/views/PlaceholderPage.vue` with:

```vue
<template>
  <section class="placeholder-page">
    <div class="page-heading">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>

    <el-descriptions title="示例字段" :column="2" border>
      <el-descriptions-item v-for="field in fields" :key="field" :label="field">
        演示数据
      </el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  fields: {
    type: Array,
    required: true
  }
})
</script>

<style scoped>
.placeholder-page {
  min-height: 360px;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.page-heading {
  margin-bottom: 24px;
}

.page-heading h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.page-heading p {
  margin: 0;
  color: #64748b;
}
</style>
```

- [ ] **Step 10: Run tab tests and frontend build**

Run:

```bash
cd frontend
npm run test -- src/stores/tabs.test.js
npm run build
```

Expected: PASS. The store behavior matches the spec and the layout compiles.

- [ ] **Step 11: Commit menu and tabs**

Run:

```bash
git add frontend/src/menu.js frontend/src/stores/tabs.js frontend/src/stores/tabs.test.js frontend/src/components frontend/src/router/index.js frontend/src/views/MainLayout.vue frontend/src/views/PlaceholderPage.vue
git commit -m "feat: add menu and tab linkage"
```

---

### Task 7: Integration Verification

**Files:**
- No source files should change in this task.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd backend
mvn test
```

Expected: PASS. Auth endpoint tests and Spring context test pass.

- [ ] **Step 2: Run frontend tests**

Run:

```bash
cd frontend
npm run test
```

Expected: PASS. Auth store and tab store tests pass.

- [ ] **Step 3: Build frontend**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS. Vite creates `frontend/dist/`.

- [ ] **Step 4: Start backend service**

Run:

```bash
cd backend
mvn spring-boot:run
```

Expected: Spring Boot starts on `http://localhost:8080`.

- [ ] **Step 5: Verify backend login API manually**

In another terminal, run:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
```

Expected: HTTP 200 and JSON body:

```json
{"token":"demo-token-admin","username":"admin","displayName":"系统管理员"}
```

- [ ] **Step 6: Verify backend rejects bad login manually**

Run:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"wrong"}'
```

Expected: HTTP 401 and JSON body:

```json
{"message":"用户名或密码错误"}
```

- [ ] **Step 7: Start frontend service**

Run:

```bash
cd frontend
npm run dev
```

Expected: Vite starts on `http://localhost:5173`.

- [ ] **Step 8: Manual browser acceptance**

Open `http://localhost:5173` through the VM SSH tunnel and verify:

1. Unauthenticated visit redirects to `/login`.
2. Blank username or password shows Element Plus form validation.
3. Wrong password shows `用户名或密码错误`.
4. `admin / 123456` logs in and redirects to `/dashboard`.
5. Browser developer tools show a real `POST /api/auth/login` request.
6. Clicking `物料信息`, `入库管理`, `库存监控`, and `出库管理` opens or activates matching tabs.
7. Closing the active tab switches to a neighboring tab.
8. The `首页` tab is visible and cannot be closed.
9. Refreshing the page keeps the user logged in because the token is in `localStorage`.
10. Clicking `退出登录` clears login state and returns to `/login`.

- [ ] **Step 9: Commit final verified state**

If no source files changed during verification, skip this commit. If small fixes were made during verification, run:

```bash
git add backend frontend .gitignore
git commit -m "fix: polish week1 demo verification"
```

---

## Self-Review

### Spec Coverage

- Vue3 + Vite + Element Plus frontend: covered by Tasks 3 through 6.
- Spring Boot backend: covered by Tasks 1 and 2.
- Real login request: covered by Tasks 2, 4, 5, and Task 7 manual API/browser verification.
- Successful login to admin layout: covered by Tasks 4, 5, and 6.
- Side menu and top tabs: covered by Task 6.
- Add/switch/close tab behavior: covered by Task 6 tests and Task 7 browser acceptance.
- WMS placeholder pages: covered by Task 6 `menu.js` and `PlaceholderPage.vue`.
- No database, no role system, no full JWT: preserved by the in-memory `AuthService` and demo token.
- CORS for Vite dev address: covered by Task 2 `CorsConfig`.
- Ports `8080` and `5173`: covered by Task 7 service startup steps.

### Consistency Check

- Backend token is consistently `demo-token-admin`.
- Demo account is consistently `admin / 123456`.
- Frontend storage keys are consistently `wms-token` and `wms-user`.
- Route tab keys match `menuItems` keys and `tabs` store keys.
- The dashboard tab is consistently non-closable.

### Plan Boundaries

This plan avoids the large-project PDF scope, MySQL, CRUD modules, permission roles, and mobile adaptation. The result is sufficient for Week 1 screenshots and classroom explanation.
