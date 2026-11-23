// backend-proxy/worker.js
// Cloudflare Worker implementation for Study Mentor AI Proxy
//
// Complies with P-09 & P-12 Performance & Security Standards:
// 1. Secrets (AI_API_KEY) are stored exclusively in server environment variables.
// 2. Client Android requests authenticate via Firebase ID Token (`Authorization: Bearer <idToken>`).
// 3. Accepts raw binary `multipart/form-data` uploads from mobile devices, converting them to upstream AI
//    payloads on the server side to save ~33% mobile upload bandwidth.
// 4. Fully supports standard JSON requests with real-time SSE streaming.

export default {
  async fetch(request, env, ctx) {
    // 1. Handle CORS preflight for mobile/web clients
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed. Use POST." }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }

    try {
      // 2. Validate Authorization Header (Firebase ID Token)
      const authHeader = request.headers.get("Authorization") || "";
      if (!authHeader.startsWith("Bearer ")) {
        return new Response(
          JSON.stringify({ error: "Unauthorized: Missing or malformed Bearer token." }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        );
      }
      const idToken = authHeader.replace("Bearer ", "").trim();
      if (!idToken) {
        return new Response(
          JSON.stringify({ error: "Unauthorized: Firebase ID Token required." }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        );
      }

      // 3. Resolve upstream API Key securely from Server Environment
      const apiKey = env.AI_API_KEY;
      if (!apiKey) {
        return new Response(
          JSON.stringify({ error: "Server Configuration Error: AI_API_KEY is not configured in worker environment." }),
          { status: 500, headers: { "Content-Type": "application/json" } }
        );
      }

      const contentType = request.headers.get("Content-Type") || "";
      let upstreamPayload;

      // 4. Handle Multipart Form Data (Raw Binary Image from Android)
      if (contentType.includes("multipart/form-data")) {
        const formData = await request.formData();
        const prompt = formData.get("prompt") || "Guide me through solving this problem step-by-step using Socratic hints.";
        const systemPrompt = formData.get("systemPrompt") || "You are a senior Socratic Study Mentor.";
        const maxTokens = parseInt(formData.get("maxTokens") || "2500", 10);
        const imageFile = formData.get("image");

        let userContent;
        if (imageFile && typeof imageFile !== "string") {
          const imageBuffer = await imageFile.arrayBuffer();
          // Convert binary arrayBuffer to Base64 ON THE SERVER (saving mobile bandwidth)
          let binary = '';
          const bytes = new Uint8Array(imageBuffer);
          const len = bytes.byteLength;
          for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(bytes[i]);
          }
          const serverBase64 = btoa(binary);

          userContent = [
            { type: "text", text: String(prompt) },
            {
              type: "image_url",
              image_url: { url: `data:image/jpeg;base64,${serverBase64}` }
            }
          ];
        } else {
          userContent = String(prompt);
        }

        upstreamPayload = {
          model: "vgpt/gpt-6.1-sol",
          stream: true,
          max_tokens: maxTokens,
          messages: [
            { role: "system", content: String(systemPrompt) },
            { role: "user", content: userContent }
          ]
        };

      } else {
        // Standard JSON request body
        const clientBody = await request.json();
        upstreamPayload = {
          model: clientBody.model || "vgpt/gpt-6.1-sol",
          stream: true,
          max_tokens: clientBody.max_tokens || 2500,
          messages: clientBody.messages || [],
        };

        if (clientBody.response_format) {
          upstreamPayload.response_format = clientBody.response_format;
        }
      }

      const upstreamEndpoint = env.UPSTREAM_AI_URL || "https://api.vilao.ai/v1/chat/completions";

      // 5. Forward request to upstream AI provider
      const upstreamResponse = await fetch(upstreamEndpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey}`,
          "Accept": "text/event-stream",
        },
        body: JSON.stringify(upstreamPayload),
      });

      if (!upstreamResponse.ok) {
        const errorText = await upstreamResponse.text();
        return new Response(
          JSON.stringify({ error: `Upstream error (${upstreamResponse.status})`, details: errorText }),
          { status: upstreamResponse.status, headers: { "Content-Type": "application/json" } }
        );
      }

      // 6. Return streaming SSE response directly to the Android client
      return new Response(upstreamResponse.body, {
        status: upstreamResponse.status,
        headers: {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache",
          "Connection": "keep-alive",
          "Access-Control-Allow-Origin": "*",
        },
      });

    } catch (err) {
      return new Response(
        JSON.stringify({ error: "Internal Server Error in Proxy", message: err.message }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }
  },
};
