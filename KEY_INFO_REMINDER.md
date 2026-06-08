# Key Info Reminder

## Current Stack

- Backend: Java 17, Spring Boot 3.3.5, Maven.
- Frontend: Vue 3, Vite, Element Plus, Pinia, Vue Router, Axios, Vitest.
- Current branch observed during harness init: `dev/iter1`.

## Local Commands

Backend:

```bash
cd backend
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm test
npm run build
npm run dev
```

Combined helper:

```bash
scripts/start.sh
```

`scripts/start.sh` starts backend on `http://localhost:8080` and frontend on `http://localhost:5173`; logs go to `/tmp/backend.log` and `/tmp/frontend.log`.

## Demo Auth

- Demo username: `admin`
- Demo password: `123456`
- Demo token: `demo-token-admin`

These are Week 1 demonstration facts, not production security design.

## Product Background Source

- Primary WMS background: `res/WMS仓储管理系统--产品介绍资料.pdf`
- The PDF frames the product as Web frontend + server + Android handheld app.
- It lists inbound, barcode, outbound, inventory monitoring, error prevention/FIFO, master data and user management as product areas.

