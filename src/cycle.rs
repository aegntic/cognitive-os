use crate::config::Config;
use crate::db::Brain;
use crate::embed::{packing, Embedder};
use crate::ingest::{sha256, walk_vault};
use anyhow::{bail, Result};
use serde_json::{json, Value};
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone)]
pub struct CycleReport {
    pub status: String,
    pub discovered: i64,
    pub imported: i64,
    pub skipped: i64,
    pub embedded: i64,
    pub exported: i64,
    pub failed_gate: Option<String>,
    pub detail: Value,
}

impl CycleReport {
    pub fn to_json(&self) -> Value {
        json!({
            "status": self.status,
            "discovered": self.discovered,
            "imported": self.imported,
            "skipped": self.skipped,
            "embedded": self.embedded,
            "exported": self.exported,
            "failed_gate": self.failed_gate,
            "gates": self.detail.get("gates"),
            "detail": self.detail,
        })
    }
}

pub fn run(home: &Path, cfg: &Config) -> Result<CycleReport> {
    let brain = Brain::open(&Config::brain_path(home, &cfg.name))?;
    let started = now();
    let tx = brain.conn.unchecked_transaction()?;
    let mut gate = "discovered";
    let result = run_attempt(cfg, &brain, &mut gate);
    match result {
        Ok(report) if report.status == "green" => {
            tx.commit()?;
            Ok(report)
        }
        result => {
            tx.rollback()?;
            let (failed_gate, detail) = match result {
                Ok(report) => (
                    report.failed_gate.unwrap_or_else(|| gate.into()),
                    report.detail,
                ),
                Err(error) => (gate.into(), json!({"error": format!("{error:#}")})),
            };
            // No page/chunk changes from a failed cycle are committed. Record the
            // failure outside that transaction so retries never skip partial pages.
            fail(
                started,
                &brain,
                &cfg.vaults.iter().map(|v| v.id.clone()).collect::<Vec<_>>(),
                0,
                0,
                0,
                0,
                &failed_gate,
                json!({"gates": {failed_gate.clone(): "failed"}, "rolled_back": true, "cause": detail}),
            )
        }
    }
}

fn run_attempt(cfg: &Config, brain: &Brain, gate: &mut &str) -> Result<CycleReport> {
    let started = now();
    let embedder = Embedder::new(&cfg.embedder, &cfg.embedder_url, cfg.embed_dims);

    let mut discovered = 0i64;
    let mut imported = 0i64;
    let mut skipped = 0i64;
    let mut embedded = 0i64;
    let mut vault_ids = Vec::new();

    for vault in &cfg.vaults {
        let root = Path::new(&vault.path);
        vault_ids.push(vault.id.clone());
        if !root.is_dir() {
            let report = fail(
                started,
                &brain,
                &vault_ids,
                discovered,
                imported,
                skipped,
                embedded,
                "discovered",
                json!({"gates": {"discovered": "failed"}, "error": format!("vault missing: {}", root.display())}),
            )?;
            return Ok(report);
        }
        *gate = "discovered";
        let files = walk_vault(root)?;
        if files.is_empty() {
            let report = fail(
                started,
                &brain,
                &vault_ids,
                discovered,
                imported,
                skipped,
                embedded,
                "discovered",
                json!({"gates": {"discovered": "failed"}, "error": format!("no markdown in {}", root.display())}),
            )?;
            return Ok(report);
        }
        discovered += files.len() as i64;

        *gate = "embed";
        if let Err(e) = embedder.probe() {
            let report = fail(
                started,
                &brain,
                &vault_ids,
                discovered,
                0,
                0,
                0,
                "embed",
                json!({
                    "gates": {"discovered": "ok", "import": "skipped", "embed": "failed"},
                    "error": format!("embedder unreachable: {e:#}")
                }),
            )?;
            return Ok(report);
        }

        for file in files {
            let page_path = format!("{}:{}", vault.id, file.rel);
            *gate = "import";
            let (page_id, changed) = brain.upsert_page(
                &page_path,
                &vault.id,
                &file.title,
                file.mtime,
                &file.hash,
                now(),
            )?;
            if !changed {
                skipped += 1;
                continue;
            }
            imported += 1;
            for (ord, chunk) in file.chunks.iter().enumerate() {
                *gate = "embed";
                let vec = embedder.embed(chunk)?;
                let packed = packing(&vec);
                *gate = "import";
                brain.insert_chunk(page_id, ord as i32, chunk, &sha256(chunk), Some(&packed))?;
                embedded += 1;
            }
        }
    }

    if imported != discovered - skipped {
        let report = fail(
            started,
            &brain,
            &vault_ids,
            discovered,
            imported,
            skipped,
            embedded,
            "import",
            json!({"gates": {"import": "failed"}, "error": "imported != discovered - skipped"}),
        )?;
        return Ok(report);
    }

    let report = CycleReport {
        status: "green".into(),
        discovered,
        imported,
        skipped,
        embedded,
        exported: 0,
        failed_gate: None,
        detail: json!({
            "gates": {
                "discovered": "ok",
                "import": "ok",
                "embed": "ok",
                "export": "skipped"
            }
        }),
    };
    brain.record_cycle(
        started,
        now(),
        &vault_ids.join(","),
        discovered,
        imported,
        skipped,
        embedded,
        0,
        "green",
        &report.detail,
    )?;
    Ok(report)
}

fn fail(
    started: i64,
    brain: &Brain,
    vaults: &[String],
    discovered: i64,
    imported: i64,
    skipped: i64,
    embedded: i64,
    gate: &str,
    detail: Value,
) -> Result<CycleReport> {
    brain.record_cycle(
        started,
        now(),
        &vaults.join(","),
        discovered,
        imported,
        skipped,
        embedded,
        0,
        "failed",
        &detail,
    )?;
    Ok(CycleReport {
        status: "failed".into(),
        discovered,
        imported,
        skipped,
        embedded,
        exported: 0,
        failed_gate: Some(gate.into()),
        detail,
    })
}

fn now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

pub fn exit_code(r: &CycleReport) -> i32 {
    if r.status == "green" {
        0
    } else {
        1
    }
}

pub fn ensure_green(r: &CycleReport) -> Result<()> {
    if r.status != "green" {
        bail!("cycle failed at gate {:?}", r.failed_gate);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::Vault;
    use std::io::{Read, Write};
    use std::net::TcpListener;

    fn fixture() -> (tempfile::TempDir, Config) {
        let dir = tempfile::tempdir().unwrap();
        let vault = dir.path().join("vault");
        std::fs::create_dir(&vault).unwrap();
        std::fs::write(vault.join("note.md"), "# Original\nold evidence").unwrap();
        let cfg = Config {
            name: "test".into(),
            embedder: "mock:hash".into(),
            embedder_url: String::new(),
            embed_dims: 3,
            vaults: vec![Vault {
                id: "fixture".into(),
                path: vault.to_string_lossy().into(),
            }],
        };
        (dir, cfg)
    }

    fn server(responses: Vec<serde_json::Value>) -> (String, std::thread::JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let handle = std::thread::spawn(move || {
            for body in responses {
                let (mut stream, _) = listener.accept().unwrap();
                stream
                    .set_read_timeout(Some(std::time::Duration::from_secs(5)))
                    .unwrap();
                let mut buffer = [0; 8192];
                stream.read(&mut buffer).unwrap();
                let body = body.to_string();
                write!(stream, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.len(), body).unwrap();
            }
        });
        (url, handle)
    }

    #[test]
    fn failed_replacement_rolls_back_and_retry_reimports() {
        let (dir, mut cfg) = fixture();
        assert_eq!(run(dir.path(), &cfg).unwrap().status, "green");
        std::fs::write(
            std::path::Path::new(&cfg.vaults[0].path).join("note.md"),
            "# Changed\nnew evidence",
        )
        .unwrap();
        let (url, worker) = server(vec![
            json!({"embedding": [1, 2, 3]}),
            json!({"embedding": [1]}),
        ]);
        cfg.embedder = "ollama:test".into();
        cfg.embedder_url = url;
        let report = run(dir.path(), &cfg).unwrap();
        worker.join().unwrap();
        assert_eq!(report.failed_gate.as_deref(), Some("embed"));
        assert_eq!(exit_code(&report), 1);
        let brain = Brain::open(&Config::brain_path(dir.path(), &cfg.name)).unwrap();
        let content: String = brain
            .conn
            .query_row("SELECT content FROM chunks", [], |r| r.get(0))
            .unwrap();
        assert!(content.contains("old evidence"));
        assert_eq!(brain.cycles().unwrap().last().unwrap().status, "failed");
        cfg.embedder = "mock:hash".into();
        let retry = run(dir.path(), &cfg).unwrap();
        assert_eq!((retry.imported, retry.skipped, retry.embedded), (1, 0, 1));
        assert_eq!(run(dir.path(), &cfg).unwrap().skipped, 1);
    }

    #[test]
    fn failed_new_page_does_not_leave_hash_or_chunks() {
        let (dir, mut cfg) = fixture();
        let (url, worker) = server(vec![
            json!({"embedding": [1, 2, 3]}),
            json!({"embedding": [1, "bad", 3]}),
        ]);
        cfg.embedder = "ollama:test".into();
        cfg.embedder_url = url;
        assert_eq!(run(dir.path(), &cfg).unwrap().status, "failed");
        worker.join().unwrap();
        let brain = Brain::open(&Config::brain_path(dir.path(), &cfg.name)).unwrap();
        let pages: i64 = brain
            .conn
            .query_row("SELECT count(*) FROM pages", [], |r| r.get(0))
            .unwrap();
        assert_eq!(pages, 0);
        assert_eq!(brain.cycles().unwrap().len(), 1);
        cfg.embedder = "mock:hash".into();
        assert_eq!(run(dir.path(), &cfg).unwrap().imported, 1);
    }

    #[test]
    fn unreadable_markdown_is_a_recorded_discovery_failure() {
        let (dir, cfg) = fixture();
        std::fs::write(
            std::path::Path::new(&cfg.vaults[0].path).join("bad.md"),
            [0xff, 0xfe],
        )
        .unwrap();
        let report = run(dir.path(), &cfg).unwrap();
        assert_eq!(report.failed_gate.as_deref(), Some("discovered"));
        assert_eq!(report.status, "failed");
    }
}
