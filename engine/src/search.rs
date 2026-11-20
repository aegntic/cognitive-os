use crate::config::Config;
use crate::db::Brain;
use crate::embed::{cosine, unpacking, Embedder};
use anyhow::{Context, Result};
use rusqlite::params;
use serde_json::{json, Value};
use std::collections::HashMap;
use std::path::Path;
use std::time::{Duration, Instant};

pub fn search(home: &Path, cfg: &Config, query: &str, k: usize) -> Result<Value> {
    search_with_timeout(home, cfg, query, k, Duration::from_secs(30))
}

fn remaining(deadline: Instant) -> Result<Duration> {
    deadline
        .checked_duration_since(Instant::now())
        .filter(|d| !d.is_zero())
        .context("memory search deadline exceeded")
}

fn fts_ranks(
    stmt: &mut rusqlite::Statement<'_>,
    query: &str,
    limit: i64,
    deadline: Instant,
) -> Result<HashMap<i64, usize>> {
    let rows = stmt.query_map(params![query, limit], |row| row.get::<_, i64>(0))?;
    let mut ranks = HashMap::new();
    for (rank, row) in rows.enumerate() {
        remaining(deadline)?;
        ranks.insert(row?, rank);
    }
    Ok(ranks)
}

fn literal_fts_query(query: &str) -> String {
    query
        .split_whitespace()
        .filter(|term| term.chars().any(char::is_alphanumeric))
        .map(|term| format!("\"{}\"", term.replace('"', "\"\"")))
        .collect::<Vec<_>>()
        .join(" ")
}

pub fn search_with_timeout(
    home: &Path,
    cfg: &Config,
    query: &str,
    k: usize,
    timeout: Duration,
) -> Result<Value> {
    let t0 = Instant::now();
    let deadline = t0 + timeout;
    remaining(deadline)?;
    let brain_path = Config::checked_brain_path(home, &cfg.name)?;
    // Reject absent/unrelated databases before sending a query to any embedder.
    // Drop the preflight connection so network waits cannot retain a WAL snapshot.
    drop(Brain::open_read_only_with_timeout(
        &brain_path,
        remaining(deadline)?,
    )?);
    // Do not retain a WAL snapshot while the configured network embedder waits.
    let qvec = Embedder::new(&cfg.embedder, &cfg.embedder_url, cfg.embed_dims)
        .with_timeout(remaining(deadline)?)
        .embed(query)?;
    remaining(deadline)?;
    let mut brain = Brain::open_read_only_with_timeout(&brain_path, remaining(deadline)?)?;
    brain.conn.busy_timeout(remaining(deadline)?)?;
    brain
        .conn
        .progress_handler(1000, Some(move || Instant::now() >= deadline));
    // Keep FTS IDs, vectors and final chunk reads in one WAL snapshot.
    let snapshot = brain.conn.transaction()?;

    let fts_rank = {
        let mut stmt =
            snapshot.prepare("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?1 LIMIT ?2")?;
        let limit = (k * 8) as i64;
        match fts_ranks(&mut stmt, query, limit, deadline) {
            // Keep valid FTS expressions intact. Retry only parser errors, not DB failures.
            Err(error)
                if matches!(
                    error.downcast_ref::<rusqlite::Error>(),
                    Some(rusqlite::Error::SqliteFailure(_, Some(message)))
                        if message.starts_with("fts5: syntax error")
                ) =>
            {
                remaining(deadline)?;
                fts_ranks(&mut stmt, &literal_fts_query(query), limit, deadline)?
            }
            result => result?,
        }
    };

    remaining(deadline)?;
    let mut vec_scores: Vec<(i64, f32)> = Vec::new();
    {
        let mut stmt =
            snapshot.prepare("SELECT id, embedding FROM chunks WHERE embedding IS NOT NULL")?;
        let rows = stmt.query_map([], |r| Ok((r.get::<_, i64>(0)?, r.get::<_, Vec<u8>>(1)?)))?;
        for row in rows {
            remaining(deadline)?;
            let (id, blob) = row?;
            let v = unpacking(&blob);
            vec_scores.push((id, cosine(&qvec, &v)));
        }
    }
    remaining(deadline)?;
    vec_scores.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    let mut vec_rank: HashMap<i64, usize> = HashMap::new();
    for (i, (id, _)) in vec_scores.iter().take(k * 8).enumerate() {
        vec_rank.insert(*id, i);
    }

    let mut fused: HashMap<i64, f32> = HashMap::new();
    for (id, r) in &fts_rank {
        *fused.entry(*id).or_insert(0.0) += 1.0 / (60.0 + *r as f32);
    }
    for (id, r) in &vec_rank {
        *fused.entry(*id).or_insert(0.0) += 1.0 / (60.0 + *r as f32);
    }
    let mut ranked: Vec<(i64, f32)> = fused.into_iter().collect();
    ranked.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    ranked.truncate(k);

    let mut hits = Vec::new();
    for (id, score) in ranked {
        remaining(deadline)?;
        let row: (String, String, String) = snapshot.query_row(
            "SELECT p.path, p.title, c.content FROM chunks c JOIN pages p ON p.id = c.page_id WHERE c.id = ?1",
            params![id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )?;
        let snippet: String = row.2.chars().take(220).collect();
        hits.push(json!({
            "path": row.0,
            "title": row.1,
            "score": score,
            "snippet": snippet,
        }));
    }

    remaining(deadline)?;
    let ms = t0.elapsed().as_millis();
    Ok(json!({
        "hits": hits,
        "_meta": {
            "hits": hits.len(),
            "ms": ms,
            "fused_from": { "fts": fts_rank.len(), "vec": vec_rank.len() }
        }
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::{accept_request, reply_json};
    use crate::{config::Vault, cycle};
    use std::net::TcpListener;

    fn fixture() -> (tempfile::TempDir, Config) {
        let home = tempfile::tempdir().unwrap();
        let vault = home.path().join("vault");
        std::fs::create_dir(&vault).unwrap();
        std::fs::write(vault.join("note.md"), "# Original\nneedle original").unwrap();
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
        assert_eq!(cycle::run(home.path(), &cfg).unwrap().status, "green");
        (home, cfg)
    }

    #[test]
    fn unquoted_dotted_version_preserves_fts_evidence() {
        let (home, cfg) = fixture();
        let vault = Path::new(&cfg.vaults[0].path);
        std::fs::write(
            vault.join("historical.md"),
            "# Release 0.2\nBuild command: bun run build",
        )
        .unwrap();
        std::fs::write(
            vault.join("current.md"),
            "# Release 0.3\nBuild command: npm run build",
        )
        .unwrap();
        assert_eq!(cycle::run(home.path(), &cfg).unwrap().status, "green");
        for query in ["0.2", "build command 0.2?", "build command \"0.2\""] {
            let result = search(home.path(), &cfg, query, 1).unwrap();
            assert_eq!(result["_meta"]["fused_from"]["fts"], 1, "{query}");
            let hit = &result["hits"][0];
            assert!(
                hit["path"].as_str().unwrap().ends_with("historical.md"),
                "{query}"
            );
            assert!(
                hit["snippet"].as_str().unwrap().contains("bun run build"),
                "{query}"
            );
        }
        assert_eq!(
            search(home.path(), &cfg, "bun OR npm", 8).unwrap()["_meta"]["fused_from"]["fts"],
            2
        );
    }

    #[test]
    fn embedding_does_not_pin_an_old_snapshot() {
        let (home, mut cfg) = fixture();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let writer_cfg = cfg.clone();
        let writer_home = home.path().to_path_buf();
        let writer = std::thread::spawn(move || {
            let mut stream = accept_request(&listener);
            // Changes committed while embedding must appear in the later snapshot.
            std::fs::write(
                Path::new(&writer_cfg.vaults[0].path).join("note.md"),
                "# Replacement\nneedle replacement",
            )
            .unwrap();
            assert_eq!(
                cycle::run(&writer_home, &writer_cfg).unwrap().status,
                "green"
            );
            let body = r#"{"embedding":[1,0,0]}"#;
            reply_json(&mut stream, body);
        });
        cfg.embedder = "ollama:test".into();
        cfg.embedder_url = url;
        let result = search(home.path(), &cfg, "needle", 8);
        writer.join().unwrap();
        let result = result.unwrap();
        assert!(result["hits"][0]["snippet"]
            .as_str()
            .unwrap()
            .contains("needle replacement"));
        cfg.embedder = "mock:hash".into();
        assert!(
            search(home.path(), &cfg, "needle", 8).unwrap()["hits"][0]["snippet"]
                .as_str()
                .unwrap()
                .contains("needle replacement")
        );
    }

    #[test]
    fn read_snapshot_retains_original_chunks_during_concurrent_cycle() {
        let (home, cfg) = fixture();
        let mut reader =
            Brain::open_read_only(&Config::brain_path(home.path(), &cfg.name)).unwrap();
        let snapshot = reader.conn.transaction().unwrap();
        let id: i64 = snapshot
            .query_row(
                "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH 'needle' LIMIT 1",
                [],
                |row| row.get(0),
            )
            .unwrap();
        let writer_cfg = cfg.clone();
        let writer_home = home.path().to_path_buf();
        std::thread::spawn(move || {
            std::fs::write(
                Path::new(&writer_cfg.vaults[0].path).join("note.md"),
                "# Replacement\nneedle replacement",
            )
            .unwrap();
            assert_eq!(
                cycle::run(&writer_home, &writer_cfg).unwrap().status,
                "green"
            );
        })
        .join()
        .unwrap();
        let original: String = snapshot
            .query_row("SELECT content FROM chunks WHERE id = ?1", [id], |row| {
                row.get(0)
            })
            .unwrap();
        assert!(original.contains("needle original"));
        drop(snapshot);
        assert!(
            search(home.path(), &cfg, "needle", 8).unwrap()["hits"][0]["snippet"]
                .as_str()
                .unwrap()
                .contains("needle replacement")
        );
    }

    #[test]
    fn absent_database_does_not_contact_remote_embedder() {
        let (home, mut cfg) = fixture();
        std::fs::remove_file(Config::brain_path(home.path(), &cfg.name)).unwrap();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        listener.set_nonblocking(true).unwrap();
        cfg.embedder = "ollama:test".into();
        cfg.embedder_url = format!("http://{}", listener.local_addr().unwrap());
        assert!(search_with_timeout(
            home.path(),
            &cfg,
            "private query",
            8,
            Duration::from_millis(100)
        )
        .is_err());
        assert!(
            matches!(listener.accept(), Err(error) if error.kind() == std::io::ErrorKind::WouldBlock)
        );
    }

    #[test]
    fn expired_deadline_fails_without_querying() {
        let (home, cfg) = fixture();
        let error =
            search_with_timeout(home.path(), &cfg, "needle", 8, Duration::ZERO).unwrap_err();
        assert!(error.to_string().contains("deadline"));
    }
}
