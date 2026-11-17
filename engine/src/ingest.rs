use anyhow::Result;
use sha2::{Digest, Sha256};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

#[derive(Debug, Clone)]
pub struct Discovered {
    pub path: PathBuf,
    pub rel: String,
    pub title: String,
    pub body: String,
    pub mtime: i64,
    pub hash: String,
    pub chunks: Vec<String>,
}

pub fn walk_vault(root: &Path) -> Result<Vec<Discovered>> {
    let mut out = Vec::new();
    walk_inner(root, root, &mut out)?;
    out.sort_by(|a, b| a.rel.cmp(&b.rel));
    Ok(out)
}

fn walk_inner(root: &Path, dir: &Path, out: &mut Vec<Discovered>) -> Result<()> {
    let entries = fs::read_dir(dir)?;
    for ent in entries {
        let ent = ent?;
        let path = ent.path();
        let name = ent.file_name();
        let name = name.to_string_lossy();
        if name.starts_with('.') || name == "gbrain" {
            continue;
        }
        if path.is_dir() {
            walk_inner(root, &path, out)?;
            continue;
        }
        if path.extension().and_then(|s| s.to_str()) != Some("md") {
            continue;
        }
        let body = fs::read_to_string(&path)?;
        let rel = path
            .strip_prefix(root)
            .unwrap_or(&path)
            .to_string_lossy()
            .replace('\\', "/");
        let title = title_of(&body, &rel);
        let mtime = fs::metadata(&path)
            .ok()
            .and_then(|m| m.modified().ok())
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0);
        let hash = sha256(&body);
        let chunks = chunk_markdown(&body);
        out.push(Discovered {
            path,
            rel,
            title,
            body,
            mtime,
            hash,
            chunks,
        });
    }
    Ok(())
}

fn title_of(body: &str, rel: &str) -> String {
    for line in body.lines() {
        let t = line.trim();
        if let Some(rest) = t.strip_prefix("# ") {
            return rest.trim().to_string();
        }
    }
    Path::new(rel)
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| rel.to_string())
}

pub fn chunk_markdown(body: &str) -> Vec<String> {
    let mut chunks = Vec::new();
    let mut buf = String::new();
    for line in body.lines() {
        if line.starts_with("## ") && !buf.trim().is_empty() {
            chunks.push(buf.trim().to_string());
            buf.clear();
        }
        buf.push_str(line);
        buf.push('\n');
        if buf.len() > 3000 {
            chunks.push(buf.trim().to_string());
            buf.clear();
        }
    }
    if !buf.trim().is_empty() {
        chunks.push(buf.trim().to_string());
    }
    if chunks.is_empty() {
        chunks.push(body.trim().to_string());
    }
    chunks
}

pub fn sha256(s: &str) -> String {
    let mut h = Sha256::new();
    h.update(s.as_bytes());
    format!("{:x}", h.finalize())
}
