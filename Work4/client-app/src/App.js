// src/App.js
import React, { useEffect, useState } from 'react';

function App() {
  const [messages, setMessages] = useState([]);

  useEffect(() => {
    const ws = new WebSocket('ws://localhost:5000');
    ws.onmessage = (event) => {
      setMessages(prev => [...prev, event.data]);
    };
    ws.onopen = () => {
      console.log('WebSocket connected');
    };
    ws.onerror = (err) => {
      console.error('WebSocket error', err);
    };
    return () => ws.close();
  }, []);

  return (
    <div>
      <h1>WebSocket Messages</h1>
      <ul>
        {messages.map((msg, idx) => <li key={idx}>{msg}</li>)}
      </ul>
    </div>
  );
}

export default App;