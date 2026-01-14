//! Transport abstraction module
//!
//! Provides a unified interface for TCP and WebSocket connections, allowing
//! the server to handle both native desktop clients (TCP) and browser clients
//! (WebSocket) using the same protocol handling code.

use std::pin::Pin;
use std::task::{Context, Poll};

use crate::error::Result;
use bytes::BytesMut;
use futures_util::{SinkExt, StreamExt};
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt, ReadBuf};
use tokio::net::TcpStream;
use tokio_tungstenite::{tungstenite::Message, WebSocketStream};
use tracing::{debug, trace, warn};

use crate::error::{NetworkError, RustscapeError};

/// Maximum read buffer size (64KB)
const MAX_BUFFER_SIZE: usize = 65536;

/// Transport trait for abstracting over TCP and WebSocket connections
pub trait Transport: Send + Sync {
    /// Read data into the buffer, returning the number of bytes read
    fn poll_read(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<std::io::Result<()>>;

    /// Write data from the buffer
    fn poll_write(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<std::io::Result<usize>>;

    /// Flush any buffered data
    fn poll_flush(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>>;

    /// Shutdown the transport
    fn poll_shutdown(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>>;

    /// Check if this is a WebSocket transport
    fn is_websocket(&self) -> bool;
}

/// TCP transport for native desktop clients
pub struct TcpTransport {
    stream: TcpStream,
}

impl TcpTransport {
    /// Create a new TCP transport
    pub fn new(stream: TcpStream) -> Self {
        Self { stream }
    }

    /// Get a reference to the underlying TCP stream
    pub fn get_ref(&self) -> &TcpStream {
        &self.stream
    }

    /// Get a mutable reference to the underlying TCP stream
    pub fn get_mut(&mut self) -> &mut TcpStream {
        &mut self.stream
    }

    /// Consume the transport and return the underlying stream
    pub fn into_inner(self) -> TcpStream {
        self.stream
    }

    /// Read data from the stream
    pub async fn read(&mut self, buf: &mut [u8]) -> Result<usize> {
        self.stream
            .read(buf)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::ReadError(e.to_string())))
    }

    /// Write data to the stream
    pub async fn write(&mut self, buf: &[u8]) -> Result<usize> {
        self.stream
            .write(buf)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WriteError(e.to_string())))
    }

    /// Write all data to the stream
    pub async fn write_all(&mut self, buf: &[u8]) -> Result<()> {
        self.stream
            .write_all(buf)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WriteError(e.to_string())))
    }

    /// Flush the stream
    pub async fn flush(&mut self) -> Result<()> {
        self.stream
            .flush()
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WriteError(e.to_string())))
    }

    /// Shutdown the stream
    pub async fn shutdown(&mut self) -> Result<()> {
        self.stream
            .shutdown()
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WriteError(e.to_string())))
    }
}

impl AsyncRead for TcpTransport {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<std::io::Result<()>> {
        Pin::new(&mut self.stream).poll_read(cx, buf)
    }
}

impl AsyncWrite for TcpTransport {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<std::io::Result<usize>> {
        Pin::new(&mut self.stream).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(&mut self.stream).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(&mut self.stream).poll_shutdown(cx)
    }
}

/// WebSocket transport for browser clients
pub struct WebSocketTransport {
    stream: WebSocketStream<TcpStream>,
    /// Buffer for incoming data (WebSocket messages may contain multiple packets)
    read_buffer: BytesMut,
    /// Buffer for outgoing data
    write_buffer: BytesMut,
}

impl WebSocketTransport {
    /// Create a new WebSocket transport from an already-upgraded WebSocket stream
    pub fn new(stream: WebSocketStream<TcpStream>) -> Self {
        Self {
            stream,
            read_buffer: BytesMut::with_capacity(MAX_BUFFER_SIZE),
            write_buffer: BytesMut::with_capacity(MAX_BUFFER_SIZE),
        }
    }

    /// Upgrade a TCP stream to a WebSocket connection
    pub async fn accept(stream: TcpStream) -> Result<Self> {
        let ws_stream = tokio_tungstenite::accept_async(stream)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WebSocket(e.to_string())))?;

        Ok(Self::new(ws_stream))
    }

    /// Read the next message from the WebSocket
    pub async fn read_message(&mut self) -> Result<Option<Vec<u8>>> {
        // First, check if we have buffered data
        if !self.read_buffer.is_empty() {
            let data = self.read_buffer.split().to_vec();
            return Ok(Some(data));
        }

        // Read from the WebSocket in a loop to handle control frames
        loop {
            match self.stream.next().await {
                Some(Ok(message)) => match message {
                    Message::Binary(data) => {
                        trace!(len = data.len(), "Received binary WebSocket message");
                        return Ok(Some(data));
                    }
                    Message::Close(_) => {
                        debug!("WebSocket close message received");
                        return Ok(None);
                    }
                    Message::Ping(data) => {
                        // Respond to ping with pong
                        if let Err(e) = self.stream.send(Message::Pong(data)).await {
                            warn!("Failed to send pong: {}", e);
                        }
                        // Continue reading (loop again)
                    }
                    Message::Pong(_) => {
                        // Ignore pong messages, continue reading
                    }
                    Message::Text(text) => {
                        // Treat text as binary for protocol handling
                        return Ok(Some(text.into_bytes()));
                    }
                    Message::Frame(_) => {
                        // Raw frames shouldn't appear with tungstenite, continue reading
                    }
                },
                Some(Err(e)) => {
                    return Err(RustscapeError::Network(NetworkError::WebSocket(
                        e.to_string(),
                    )));
                }
                None => return Ok(None),
            }
        }
    }

    /// Write a binary message to the WebSocket
    pub async fn write_message(&mut self, data: &[u8]) -> Result<()> {
        trace!(len = data.len(), "Sending binary WebSocket message");
        self.stream
            .send(Message::Binary(data.to_vec()))
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WebSocket(e.to_string())))
    }

    /// Send queued data as a single message
    pub async fn flush(&mut self) -> Result<()> {
        if !self.write_buffer.is_empty() {
            let data = self.write_buffer.split().to_vec();
            self.write_message(&data).await?;
        }
        Ok(())
    }

    /// Queue data to be sent
    pub fn queue_write(&mut self, data: &[u8]) {
        self.write_buffer.extend_from_slice(data);
    }

    /// Close the WebSocket connection gracefully
    pub async fn close(&mut self) -> Result<()> {
        self.stream
            .close(None)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WebSocket(e.to_string())))
    }
}

/// Unified transport enum for handling both TCP and WebSocket connections
pub enum UnifiedTransport {
    Tcp(TcpTransport),
    WebSocket(WebSocketTransport),
}

impl UnifiedTransport {
    /// Create a TCP transport
    pub fn tcp(stream: TcpStream) -> Self {
        Self::Tcp(TcpTransport::new(stream))
    }

    /// Create a WebSocket transport
    pub fn websocket(stream: WebSocketStream<TcpStream>) -> Self {
        Self::WebSocket(WebSocketTransport::new(stream))
    }

    /// Check if this is a WebSocket transport
    pub fn is_websocket(&self) -> bool {
        matches!(self, Self::WebSocket(_))
    }

    /// Read data from the transport
    pub async fn read(&mut self, buf: &mut [u8]) -> Result<usize> {
        match self {
            Self::Tcp(tcp) => tcp.read(buf).await,
            Self::WebSocket(ws) => {
                match ws.read_message().await? {
                    Some(data) => {
                        let len = data.len().min(buf.len());
                        buf[..len].copy_from_slice(&data[..len]);
                        // If there's more data than the buffer can hold, store it
                        if data.len() > len {
                            ws.read_buffer.extend_from_slice(&data[len..]);
                        }
                        Ok(len)
                    }
                    None => Ok(0), // Connection closed
                }
            }
        }
    }

    /// Write data to the transport
    pub async fn write(&mut self, buf: &[u8]) -> Result<usize> {
        match self {
            Self::Tcp(tcp) => tcp.write(buf).await,
            Self::WebSocket(ws) => {
                ws.write_message(buf).await?;
                Ok(buf.len())
            }
        }
    }

    /// Write all data to the transport
    pub async fn write_all(&mut self, buf: &[u8]) -> Result<()> {
        match self {
            Self::Tcp(tcp) => tcp.write_all(buf).await,
            Self::WebSocket(ws) => ws.write_message(buf).await,
        }
    }

    /// Flush any buffered data
    pub async fn flush(&mut self) -> Result<()> {
        match self {
            Self::Tcp(tcp) => tcp.flush().await,
            Self::WebSocket(ws) => ws.flush().await,
        }
    }

    /// Shutdown the transport
    pub async fn shutdown(&mut self) -> Result<()> {
        match self {
            Self::Tcp(tcp) => tcp.shutdown().await,
            Self::WebSocket(ws) => ws.close().await,
        }
    }
}

/// Buffered transport wrapper that provides higher-level read operations
pub struct BufferedTransport {
    transport: UnifiedTransport,
    read_buffer: BytesMut,
    write_buffer: BytesMut,
}

impl BufferedTransport {
    /// Create a new buffered transport
    pub fn new(transport: UnifiedTransport) -> Self {
        Self {
            transport,
            read_buffer: BytesMut::with_capacity(MAX_BUFFER_SIZE),
            write_buffer: BytesMut::with_capacity(MAX_BUFFER_SIZE),
        }
    }

    /// Check if this is a WebSocket transport
    pub fn is_websocket(&self) -> bool {
        self.transport.is_websocket()
    }

    /// Read exactly n bytes from the transport
    pub async fn read_exact(&mut self, n: usize) -> Result<Vec<u8>> {
        // Read until we have enough data
        while self.read_buffer.len() < n {
            let mut temp = vec![0u8; MAX_BUFFER_SIZE];
            let bytes_read = self.transport.read(&mut temp).await?;
            if bytes_read == 0 {
                return Err(RustscapeError::Network(NetworkError::ConnectionClosed));
            }
            self.read_buffer.extend_from_slice(&temp[..bytes_read]);
        }

        // Extract exactly n bytes
        let data = self.read_buffer.split_to(n).to_vec();
        Ok(data)
    }

    /// Read up to n bytes from the transport
    pub async fn read(&mut self, n: usize) -> Result<Vec<u8>> {
        // Return buffered data if available
        if !self.read_buffer.is_empty() {
            let len = self.read_buffer.len().min(n);
            let data = self.read_buffer.split_to(len).to_vec();
            return Ok(data);
        }

        // Read from transport
        let mut temp = vec![0u8; n.min(MAX_BUFFER_SIZE)];
        let bytes_read = self.transport.read(&mut temp).await?;
        if bytes_read == 0 {
            return Err(RustscapeError::Network(NetworkError::ConnectionClosed));
        }
        Ok(temp[..bytes_read].to_vec())
    }

    /// Read a single byte
    pub async fn read_byte(&mut self) -> Result<u8> {
        let data = self.read_exact(1).await?;
        Ok(data[0])
    }

    /// Peek at the next byte without consuming it
    pub async fn peek_byte(&mut self) -> Result<u8> {
        if self.read_buffer.is_empty() {
            let mut temp = vec![0u8; 1];
            let bytes_read = self.transport.read(&mut temp).await?;
            if bytes_read == 0 {
                return Err(RustscapeError::Network(NetworkError::ConnectionClosed));
            }
            self.read_buffer.extend_from_slice(&temp[..bytes_read]);
        }
        Ok(self.read_buffer[0])
    }

    /// Check if there's data available to read
    pub fn has_data(&self) -> bool {
        !self.read_buffer.is_empty()
    }

    /// Get the number of buffered bytes available to read
    pub fn buffered_len(&self) -> usize {
        self.read_buffer.len()
    }

    /// Queue data to be written
    pub fn queue_write(&mut self, data: &[u8]) {
        self.write_buffer.extend_from_slice(data);
    }

    /// Write data immediately
    pub async fn write(&mut self, data: &[u8]) -> Result<()> {
        self.transport.write_all(data).await
    }

    /// Flush all queued writes
    pub async fn flush(&mut self) -> Result<()> {
        if !self.write_buffer.is_empty() {
            let data = self.write_buffer.split().to_vec();
            self.transport.write_all(&data).await?;
        }
        self.transport.flush().await
    }

    /// Shutdown the transport
    pub async fn shutdown(&mut self) -> Result<()> {
        self.flush().await?;
        self.transport.shutdown().await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // Note: test_tcp_transport_is_not_websocket removed because we cannot safely
    // create a TcpStream without a real connection, and zeroing it is UB.

    #[test]
    fn test_buffered_transport_queue() {
        // Test that the write buffer works correctly
        let mut buffer = BytesMut::with_capacity(1024);
        buffer.extend_from_slice(b"Hello");
        buffer.extend_from_slice(b" World");
        assert_eq!(&buffer[..], b"Hello World");
    }

    #[test]
    fn test_read_buffer() {
        let mut buffer = BytesMut::with_capacity(1024);
        buffer.extend_from_slice(b"Test data");

        // Split off first 4 bytes
        let first = buffer.split_to(4);
        assert_eq!(&first[..], b"Test");
        assert_eq!(&buffer[..], b" data");
    }
}
