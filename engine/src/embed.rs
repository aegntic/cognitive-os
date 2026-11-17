use anyhow::{bail, Context, Result};
use sha2::{Digest, Sha256};

pub struct Embedder {
    pub spec: String,
    pub url: String,
    pub model: String,
    pub dims: usize,
}

impl Embedder {
    pub fn new(spec: &str, url: &str, dims: u32) -> Self {
        let model = spec.split(':').nth(1).unwrap_or(spec).to_string();
        Self {
            spec: spec.to_string(),
            url: url.trim_end_matches('/').to_string(),
            model,
            dims: dims as usize,
        }
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
        let agent = ureq::AgentBuilder::new()
            .timeout(std::time::Duration::from_secs(30))
            .build();
        let embed_url = format!("{}/api/embed", self.url);
        let body = serde_json::json!({ "model": self.model, "input": text });
        match agent.post(&embed_url).send_json(body.clone()) {
            Ok(resp) => {
                let v: serde_json::Value = resp.into_json()?;
                if let Some(arr) = v.get("embeddings").and_then(|e| e.as_array()) {
                    if let Some(first) = arr.first() {
                        return parse_f32_arr(first);
                    }
                }
                if let Some(arr) = v.get("embedding") {
                    return parse_f32_arr(arr);
                }
                bail!("ollama /api/embed returned no embedding");
            }
            Err(_) => {
                let legacy = format!("{}/api/embeddings", self.url);
                let body = serde_json::json!({ "model": self.model, "prompt": text });
                let resp = agent
                    .post(&legacy)
                    .send_json(body)
                    .with_context(|| format!("embedder unreachable at {}", self.url))?;
                let v: serde_json::Value = resp.into_json()?;
                parse_f32_arr(v.get("embedding").unwrap_or(&serde_json::Value::Null))
            }
        }
    }
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
