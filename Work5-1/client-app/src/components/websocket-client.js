import WebSocket from "ws";

const ws = new WebSocket("ws://localhost:3000");

ws.on("open", () => {
  console.log("WebSocket connected");
});

ws.on("message", (data) => {
  try {
    const parsedData = JSON.parse(data);
    console.log("Received JSON data:", parsedData);
  } catch {
    console.log("Received non-JSON data:", data.toString());
  }
});

ws.on("error", (error) => {
  console.error("WebSocket error:", error);
});

ws.on("close", () => {
  console.log("WebSocket disconnected");
});
