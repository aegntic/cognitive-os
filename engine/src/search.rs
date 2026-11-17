use crate::config::Config;
use crate::db::Brain;
use crate::embed::{cosine, unpacking, Embedder};
use anyhow::Result;
use rusqlite::params;
use serde_json::{json, Value};
use std::collections::HashMap;
use std::path::Path;
use std::time::Instant;

pub fn search(home: &Path, cfg: &Config, query: &str, k: usize) -> Result<Value> {
    let t0 = Instant::now();
    let brain = Brain::open(&Config::brain_path(home, &cfg.name))?;
    let embedder = Embedder::new(&cfg.embedder, &cfg.embedder_url, cfg.embed_dims);

    let mut fts_rank: HashMap<i64, usize> = HashMap::new();
    {
        let mut stmt = brain
            .conn
            .prepare("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?1 LIMIT ?2")?;
        let rows = stmt.query_map(params![query, (k * 8) as i64], |r| r.get::<_, i64>(0))?;
        for (i, id) in rows.enumerate() {
            if let Ok(id) = id {
                fts_rank.insert(id, i);
            }
        }
    }

    let qvec = embedder.embed(query)?;
    let mut vec_scores: Vec<(i64, f32)> = Vec::new();
    {
        let mut stmt = brain
            .conn
            .prepare("SELECT id, embedding FROM chunks WHERE embedding IS NOT NULL")?;
        let rows = stmt.query_map([], |r| Ok((r.get::<_, i64>(0)?, r.get::<_, Vec<u8>>(1)?)))?;
        for row in rows {
            let (id, blob) = row?;
            let v = unpacking(&blob);
            vec_scores.push((id, cosine(&qvec, &v)));
        }
    }
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
        let row: (String, String, String) = brain.conn.query_row(
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
