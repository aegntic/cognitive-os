use anyhow::{bail, Context, Result};
use sha2::{Digest, Sha256};
use std::time::{Duration, Instant};

pub struct Embedder {
    pub spec: String,
    pub url: String,
    pub model: String,
    pub dims: usize,
    timeout: Duration,
}

impl Embedder {
    pub fn new(spec: &str, url: &str, dims: u32) -> Self {
        let model = spec.split(':').nth(1).unwrap_or(spec).to_string();
        Self {
            spec: spec.to_string(),
            url: url.trim_end_matches('/').to_string(),
            model,
            dims: dims as usize,
            timeout: Duration::from_secs(30),
        }
    }

    pub fn with_timeout(self, timeout: Duration) -> Self {
        Self { timeout, ..self }
    }

    pub fn is_mock(&self) -> bool {
        self.spec.starts_with("mock:")
    }

    pub fn probe(&self) -> Result<()> {
        if self.is_mock() {
            return Ok(());
        }
        self.embed("ping").map(|_| ())
    }

    pub fn embed(&self, text: &str) -> Result<Vec<f32>> {
        if self.is_mock() {
            return Ok(mock_vec(text, self.dims));
        }
        let vector = self.embed_ollama(text)?;
        if vector.len() != self.dims || vector.iter().any(|v| !v.is_finite()) {
            bail!(
                "invalid embedding: expected {} finite values, got {}",
                self.dims,
                vector.len()
            );
        }
        Ok(vector)
    }

    fn embed_ollama(&self, text: &str) -> Result<Vec<f32>> {
        let deadline = Instant::now()
            .checked_add(self.timeout)
            .context("invalid embedding request timeout")?;
        let agent = ureq::AgentBuilder::new().build();
        let embed_url = format!("{}/api/embed", self.url);
        let body = serde_json::json!({ "model": self.model, "input": text });
        let response = agent
            .post(&embed_url)
            .timeout(remaining(deadline)?)
            .send_json(body);
        let value = match response {
            Ok(response) => response_json(response)?,
            // Only an unsupported endpoint permits the legacy retry. Network
            // errors and server failures must not double the timeout budget.
            Err(ureq::Error::Status(404 | 405, _)) => {
                let legacy = format!("{}/api/embeddings", self.url);
                let body = serde_json::json!({ "model": self.model, "prompt": text });
                let response = agent
                    .post(&legacy)
                    .timeout(remaining(deadline)?)
                    .send_json(body)
                    .map_err(safe_request_error)?;
                response_json(response)?
            }
            Err(error) => return Err(safe_request_error(error)),
        };
        if let Some(first) = value
            .get("embeddings")
            .and_then(|v| v.as_array())
            .and_then(|v| v.first())
        {
            return parse_f32_arr(first);
        }
        parse_f32_arr(value.get("embedding").unwrap_or(&serde_json::Value::Null))
    }
}

fn remaining(deadline: Instant) -> Result<Duration> {
    deadline
        .checked_duration_since(Instant::now())
        .filter(|duration| !duration.is_zero())
        .context("embedding request deadline exceeded")
}

// Never retain the ureq error as a source: its Display may include URL userinfo,
// query tokens or redirect destinations, which callers can write to host logs.
fn safe_request_error(error: ureq::Error) -> anyhow::Error {
    match error {
        ureq::Error::Status(status, _) => {
            anyhow::anyhow!("embedding request failed with HTTP status {status}")
        }
        ureq::Error::Transport(_) => {
            anyhow::anyhow!("embedding request failed or timed out (transport error)")
        }
    }
}

fn response_json(response: ureq::Response) -> Result<serde_json::Value> {
    response
        .into_json()
        .map_err(|_| anyhow::anyhow!("embedding response unreadable or invalid JSON"))
}

fn parse_f32_arr(v: &serde_json::Value) -> Result<Vec<f32>> {
    let arr = v.as_array().with_context(|| "embedding is not an array")?;
    Ok(arr
        .iter()
        .map(|x| {
            x.as_f64()
                .map(|v| v as f32)
                .context("embedding contains a non-number")
        })
        .collect::<Result<Vec<_>>>()?)
}

fn mock_vec(text: &str, dims: usize) -> Vec<f32> {
    let mut h = Sha256::new();
    h.update(text.as_bytes());
    let digest = h.finalize();
    let mut out = vec![0.0f32; dims];
    for (i, slot) in out.iter_mut().enumerate() {
        *slot = (digest[i % 32] as f32 - 128.0) / 128.0;
    }
    let norm: f32 = out.iter().map(|x| x * x).sum::<f32>().sqrt();
    if norm > 0.0 {
        for x in &mut out {
            *x /= norm;
        }
    }
    out
}

pub fn packing(v: &[f32]) -> Vec<u8> {
    let mut b = Vec::with_capacity(v.len() * 4);
    for x in v {
        b.extend_from_slice(&x.to_le_bytes());
    }
    b
}

pub fn unpacking(b: &[u8]) -> Vec<f32> {
    b.chunks_exact(4)
        .map(|c| f32::from_le_bytes(c.try_into().unwrap()))
        .collect()
}

pub fn cosine(a: &[f32], b: &[f32]) -> f32 {
    let n = a.len().min(b.len());
    let mut dot = 0.0;
    let mut na = 0.0;
    let mut nb = 0.0;
    for i in 0..n {
        dot += a[i] * b[i];
        na += a[i] * a[i];
        nb += b[i] * b[i];
    }
    if na == 0.0 || nb == 0.0 {
        0.0
    } else {
        dot / (na.sqrt() * nb.sqrt())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::{TcpListener, TcpStream};
    use std::thread;

    fn read_request(stream: &mut TcpStream) {
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .unwrap();
        let mut byte = [0];
        let mut header = Vec::new();
        while !header.ends_with(b"\r\n\r\n") {
            stream.read_exact(&mut byte).unwrap();
            header.push(byte[0]);
            assert!(header.len() < 16_384);
        }
        let header = String::from_utf8(header).unwrap();
        let length = header
            .lines()
            .find_map(|line| {
                let (key, value) = line.split_once(':')?;
                key.eq_ignore_ascii_case("content-length")
                    .then(|| value.trim().parse::<usize>().unwrap())
            })
            .unwrap_or(0);
        let mut body = vec![0; length];
        stream.read_exact(&mut body).unwrap();
    }

    fn reply(stream: &mut TcpStream, status: u16, body: &str) {
        write!(stream, "HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len()).unwrap();
    }

    #[test]
    fn network_and_legacy_errors_do_not_disclose_url_credentials() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let worker = thread::spawn(move || {
            for status in [404, 503] {
                let (mut stream, _) = listener.accept().unwrap();
                read_request(&mut stream);
                reply(&mut stream, status, "{}");
            }
        });
        let url = format!("http://sentinel-user:sentinel-password@{address}");
        let embedder = Embedder::new("ollama:test", &url, 2).with_timeout(Duration::from_secs(2));
        let error = format!("{:#}", embedder.embed("test").unwrap_err());
        worker.join().unwrap();
        assert!(error.contains("503"));
        assert!(!error.contains("sentinel"));
        assert!(!error.contains(&url));
        // The listener is now closed: the transport failure must also be safe.
        let error = format!("{:#}", embedder.embed("test").unwrap_err());
        assert!(error.contains("transport error"));
        assert!(!error.contains("sentinel"));
        assert!(!error.contains(&url));
    }

    #[test]
    fn unsupported_endpoint_falls_back_successfully() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let worker = thread::spawn(move || {
            for (status, body) in [(405, "{}"), (200, "{\"embedding\":[1,2]}")] {
                let (mut stream, _) = listener.accept().unwrap();
                read_request(&mut stream);
                reply(&mut stream, status, body);
            }
        });
        let result = Embedder::new("ollama:test", &url, 2).embed("test").unwrap();
        worker.join().unwrap();
        assert_eq!(result, vec![1.0, 2.0]);
    }

    #[test]
    fn stalled_current_endpoint_does_not_attempt_legacy_retry() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let worker = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            read_request(&mut stream);
            thread::sleep(Duration::from_millis(350));
            listener.set_nonblocking(true).unwrap();
            assert!(
                matches!(listener.accept(), Err(error) if error.kind() == std::io::ErrorKind::WouldBlock)
            );
        });
        let start = Instant::now();
        let error = Embedder::new("ollama:test", &url, 2)
            .with_timeout(Duration::from_millis(100))
            .embed("test")
            .unwrap_err();
        assert!(start.elapsed() < Duration::from_millis(300));
        assert!(format!("{error:#}").contains("transport error"));
        worker.join().unwrap();
    }

    #[test]
    fn legacy_retry_uses_only_remaining_budget() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let worker = thread::spawn(move || {
            let (mut current, _) = listener.accept().unwrap();
            read_request(&mut current);
            thread::sleep(Duration::from_millis(250));
            reply(&mut current, 404, "{}");
            let (mut legacy, _) = listener.accept().unwrap();
            read_request(&mut legacy);
            thread::sleep(Duration::from_millis(600));
        });
        let start = Instant::now();
        let result = Embedder::new("ollama:test", &url, 2)
            .with_timeout(Duration::from_millis(400))
            .embed("test");
        let elapsed = start.elapsed();
        assert!(result.is_err());
        assert!(elapsed < Duration::from_millis(600), "elapsed: {elapsed:?}");
        worker.join().unwrap();
    }

    #[test]
    fn response_body_read_obeys_request_deadline() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let worker = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            read_request(&mut stream);
            write!(stream, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\n{{").unwrap();
            thread::sleep(Duration::from_millis(350));
        });
        let start = Instant::now();
        let result = Embedder::new("ollama:test", &url, 2)
            .with_timeout(Duration::from_millis(100))
            .embed("test");
        assert!(result.is_err());
        assert!(start.elapsed() < Duration::from_millis(300));
        worker.join().unwrap();
    }
}
