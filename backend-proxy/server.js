// backend-proxy/server.js
// Node.js Express implementation for Study Mentor AI Proxy
//
// Complies with P-09 & P-12 Performance & Security Standards:
// 1. Accepts both application/json and multipart/form-data.
// 2. Transmits binary image from mobile client directly, converting to Base64 on server.
// 3. Pipes SSE tokens with low latency to the Android client.

const express = require('express');
const cors = require('cors');
const multer = require('multer');
require('dotenv').config();

const app = express();
const upload = multer({ limits: { fileSize: 15 * 1024 * 1024 } }); // 15MB limit
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

app.post('/v1/chat/completions', upload.single('image'), async (req, res) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid Firebase ID token' });
  }

  const apiKey = process.env.AI_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ error: 'Server misconfiguration: AI_API_KEY missing' });
  }

  const upstreamUrl = process.env.UPSTREAM_AI_URL || 'https://api.vilao.ai/v1/chat/completions';

  try {
    let upstreamPayload;

    if (req.file) {
      // Multipart upload with binary image file
      const prompt = req.body.prompt || 'Guide me through solving this problem step-by-step using Socratic hints.';
      const systemPrompt = req.body.systemPrompt || 'You are a senior Socratic Study Mentor.';
      const maxTokens = parseInt(req.body.maxTokens || '2500', 10);
      const serverBase64 = req.file.buffer.toString('base64');

      upstreamPayload = {
        model: 'vgpt/gpt-6.1-sol',
        stream: true,
        max_tokens: maxTokens,
        messages: [
          { role: 'system', content: systemPrompt },
          {
            role: 'user',
            content: [
              { type: 'text', text: prompt },
              {
                type: 'image_url',
                image_url: { url: `data:image/jpeg;base64,${serverBase64}` }
              }
            ]
          }
        ]
      };
    } else {
      // Standard JSON request body
      upstreamPayload = {
        model: req.body.model || 'vgpt/gpt-6.1-sol',
        stream: true,
        max_tokens: req.body.max_tokens || 2500,
        response_format: req.body.response_format,
        messages: req.body.messages || [],
      };
    }

    const upstreamResponse = await fetch(upstreamUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`,
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify(upstreamPayload),
    });

    if (!upstreamResponse.ok) {
      const errText = await upstreamResponse.text();
      return res.status(upstreamResponse.status).send(errText);
    }

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    // Pipe upstream SSE response directly to Android client
    const reader = upstreamResponse.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(decoder.decode(value, { stream: true }));
    }

    res.end();
  } catch (error) {
    console.error('Proxy Error:', error);
    res.status(500).json({ error: 'Failed to proxy request', message: error.message });
  }
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'Study Mentor AI Proxy' });
});

app.listen(PORT, () => {
  console.log(`Study Mentor AI Proxy running on port ${PORT}`);
});
