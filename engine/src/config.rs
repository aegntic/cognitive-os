use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub name: String,
    pub embedder: String,
    pub embedder_url: String,
    pub embed_dims: u32,
    pub vaults: Vec<Vault>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Vault {
    pub id: String,
    pub path: String,
}

impl Config {
    pub fn default_home() -> PathBuf {
        std::env::var("COS_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| {
                dirs_home()
                    .map(|h| h.join(".cognitive-os"))
                    .unwrap_or_else(|| PathBuf::from(".cognitive-os"))
            })
    }

    pub fn load(home: &Path) -> Result<Self> {
        let path = home.join("config.toml");
        let raw = fs::read_to_string(&path)
            .with_context(|| format!("no config at {} — run `cos-engine init`", path.display()))?;
        Ok(toml::from_str(&raw)?)
    }

    pub fn save(&self, home: &Path) -> Result<()> {
        fs::create_dir_all(home)?;
        fs::write(home.join("config.toml"), toml::to_string_pretty(self)?)?;
        Ok(())
    }

    pub fn brain_path(home: &Path, name: &str) -> PathBuf {
        home.join("brains").join(name).join("brain.sqlite")
    }

    pub fn parse_embedder(spec: &str) -> (String, u32) {
        // "ollama:nomic-embed-text" or "mock:hash"
        let model = spec.split(':').nth(1).unwrap_or("nomic-embed-text");
        let dims = if model.contains("nomic") { 768 } else { 768 };
        (model.to_string(), dims)
    }
}

fn dirs_home() -> Option<PathBuf> {
    std::env::var_os("HOME").map(PathBuf::from)
}
