# Study Mentor Backend AI Proxy

This directory contains lightweight, production-ready server implementations to eliminate hardcoded AI API keys from the Android APK in compliance with security criterion **P-09**.

## Architecture & Data Flow

```
Android Client (No AI Keys)
   │
   │  HTTPS POST with Student Firebase ID Token:
   │  Authorization: Bearer <Firebase_ID_Token>
   ▼
Backend Proxy (Cloudflare Worker or Express)
   │
   │  1. Validates Firebase Auth
   │  2. Injects secret AI_API_KEY from environment variables
   ▼
Upstream AI Service (OpenAI / Claude / Gemini)
   │
   │  Real-Time SSE Streaming (text/event-stream)
   ▼
Android Client (Renders Socratic chunks via Flow<String>)
```

## Options for Deployment

### Option A: Cloudflare Worker (`worker.js`) - Recommended (Zero Server Management)
1. Install Wrangler CLI: `npm install -g wrangler`
2. Create `wrangler.toml`:
   ```toml
   name = "study-mentor-proxy"
   main = "worker.js"
   compatibility_date = "2024-01-01"
   ```
3. Set secret API key:
   ```bash
   wrangler secret put AI_API_KEY
   ```
4. Deploy:
   ```bash
   wrangler deploy
   ```
5. Set the generated URL in `local.properties`:
   ```properties
   AI_PROXY_BASE_URL=https://study-mentor-proxy.your-subdomain.workers.dev/v1/chat/completions
   ```

---

### Option B: Node.js Express (`server.js`)
1. In `backend-proxy/`:
   ```bash
   npm init -y
   npm install express cors dotenv node-fetch
   ```
2. Create `.env`:
   ```env
   PORT=8080
   AI_API_KEY=sk-your-actual-api-key
   UPSTREAM_AI_URL=https://api.vilao.ai/v1/chat/completions
   ```
3. Run:
   ```bash
   node server.js
   ```
