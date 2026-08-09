import express from "express";
import path from "path";
import {Readable} from "node:stream";
import { createServer as createViteServer } from "vite";
import dotenv from "dotenv";

dotenv.config();

async function startServer() {
  const app = express();
  const PORT = 3000;

  const reportAiTarget = (process.env.REPORT_AI_API_TARGET || "http://127.0.0.1:18080").replace(/\/$/, "");

  // Keep the browser same-origin in both development and production. This is
  // intentionally a narrow API proxy: it forwards only ReportAI's versioned
  // API and never exposes arbitrary server-side URL fetching.
  app.use("/api/v1", async (req, res) => {
    try {
      const headers = new Headers();
      Object.entries(req.headers).forEach(([name, value]) => {
        if (["host", "connection", "content-length"].includes(name)) return;
        if (typeof value === "string") headers.set(name, value);
      });
      const method = req.method.toUpperCase();
      const init = {
        method,
        headers,
        body: ["GET", "HEAD"].includes(method) ? undefined : req,
        duplex: "half" as const,
      } as unknown as RequestInit & { duplex: "half" };
      const upstream = await fetch(`${reportAiTarget}${req.originalUrl}`, init);
      res.status(upstream.status);
      upstream.headers.forEach((value, name) => {
        if (name !== "transfer-encoding") res.setHeader(name, value);
      });
      if (!upstream.body) {
        res.end();
        return;
      }
      const upstreamStream = Readable.fromWeb(
        upstream.body as import("node:stream/web").ReadableStream
      );
      upstreamStream.on("error", (streamError) => {
        // A client closing an SSE/download connection or the backend resetting
        // an idle connection must not become an uncaught Node error that kills
        // the entire Mac for Web process.
        console.warn("ReportAI API proxy stream error:", streamError instanceof Error
          ? streamError.message : streamError);
        if (!res.headersSent && !res.writableEnded) {
          res.status(502).json({error: "ReportAI API stream interrupted"});
        } else if (!res.destroyed) {
          res.destroy();
        }
      });
      res.on("close", () => {
        if (!res.writableFinished && !upstreamStream.destroyed) upstreamStream.destroy();
      });
      upstreamStream.pipe(res);
    } catch (error) {
      console.error("ReportAI API proxy error:", error);
      if (!res.headersSent) res.status(502).json({error: "ReportAI backend is unavailable"});
      else res.end();
    }
  });

  app.use(express.json());

  // Health Check
  app.get("/api/health", (_req, res) => {
    res.json({ status: "ok", timestamp: new Date().toISOString() });
  });

  const portfolioApplications = [
    { mount: "/react-ecommerce-store", directory: "react-ecommerce-store" },
    { mount: "/typescript-reddit-clone", directory: "typescript-reddit-clone" },
  ];

  for (const application of portfolioApplications) {
    const buildDirectory = process.env.NODE_ENV === "production"
      ? path.join(process.cwd(), "dist", application.directory)
      : path.join(process.cwd(), "..", application.directory, "build");
    app.use(application.mount, express.static(buildDirectory));
    app.get(`${application.mount}/*`, (_req, res) => {
      res.sendFile(path.join(buildDirectory, "index.html"));
    });
  }

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Mac for Web server running on http://localhost:${PORT}`);
  });
}

startServer();
