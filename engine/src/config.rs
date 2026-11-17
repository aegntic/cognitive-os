use anyhow::{bail, Context, Result};
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
        let config: Self =
            toml::from_str(&raw).map_err(|_| anyhow::anyhow!("invalid engine configuration"))?;
        validate_brain_name(&config.name)?;
        Ok(config)
    }

    pub fn save(&self, home: &Path) -> Result<()> {
        validate_brain_name(&self.name)?;
        fs::create_dir_all(home)?;
        fs::write(home.join("config.toml"), toml::to_string_pretty(self)?)?;
        Ok(())
    }

    pub fn brain_path(home: &Path, name: &str) -> PathBuf {
        home.join("brains").join(name).join("brain.sqlite")
    }

    pub fn checked_brain_path(home: &Path, name: &str) -> Result<PathBuf> {
        validate_brain_name(name)?;
        let root = home.canonicalize().context("resolve selected brain home")?;
        let mut path = root.clone();
        for component in ["brains", name, "brain.sqlite"] {
            path.push(component);
            if std::fs::symlink_metadata(&path)?.file_type().is_symlink() {
                bail!("symlinks below the selected brain home are not supported");
            }
        }
        let resolved = path.canonicalize()?;
        if !resolved.starts_with(&root) {
            bail!("brain path escapes selected home");
        }
        Ok(resolved)
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

fn validate_brain_name(name: &str) -> Result<()> {
    let mut components = Path::new(name).components();
    let single_normal = matches!(components.next(), Some(std::path::Component::Normal(_)))
        && components.next().is_none();
    if !single_normal
        || name.contains(['/', '\\', ':'])
        || name.chars().any(char::is_control)
        || name.ends_with(['.', ' '])
    {
        bail!(
            "brain name must be a single non-empty path component, without traversal or separators"
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_names_that_escape_or_alias_the_selected_home() {
        for name in [
            "",
            ".",
            "..",
            "../other",
            "/tmp/other",
            "a/b",
            "a\\b",
            "C:other",
            "bad\n",
            "name.",
            "name ",
        ] {
            assert!(validate_brain_name(name).is_err(), "accepted {name:?}");
        }
        for name in ["default", "my-brain", "project_2", "notes.v2"] {
            assert!(validate_brain_name(name).is_ok());
        }
    }

    #[test]
    fn invalid_saved_name_is_rejected_before_creating_home() {
        let dir = tempfile::tempdir().unwrap();
        let home = dir.path().join("not-created");
        let cfg = Config {
            name: "../escape".into(),
            embedder: "mock:hash".into(),
            embedder_url: String::new(),
            embed_dims: 3,
            vaults: vec![],
        };
        assert!(cfg.save(&home).is_err());
        assert!(!home.exists());
        std::fs::create_dir(&home).unwrap();
        std::fs::write(home.join("config.toml"), toml::to_string(&cfg).unwrap()).unwrap();
        assert!(Config::load(&home).is_err());
    }
    #[cfg(unix)]
    #[test]
    fn read_paths_reject_nested_symlinks_but_allow_linked_home() {
        use std::os::unix::fs::symlink;
        let dir = tempfile::tempdir().unwrap();
        let home = dir.path().join("home");
        let outside = dir.path().join("outside");
        std::fs::create_dir_all(home.join("brains")).unwrap();
        std::fs::create_dir_all(&outside).unwrap();
        std::fs::write(outside.join("brain.sqlite"), "fixture").unwrap();
        symlink(&outside, home.join("brains/default")).unwrap();
        assert!(Config::checked_brain_path(&home, "default").is_err());
        std::fs::remove_file(home.join("brains/default")).unwrap();
        std::fs::create_dir(home.join("brains/default")).unwrap();
        symlink(
            outside.join("brain.sqlite"),
            home.join("brains/default/brain.sqlite"),
        )
        .unwrap();
        assert!(Config::checked_brain_path(&home, "default").is_err());
        std::fs::remove_file(home.join("brains/default/brain.sqlite")).unwrap();
        std::fs::write(home.join("brains/default/brain.sqlite"), "fixture").unwrap();
        let linked = dir.path().join("linked-home");
        symlink(&home, &linked).unwrap();
        assert!(Config::checked_brain_path(&linked, "default").is_ok());
    }
}
