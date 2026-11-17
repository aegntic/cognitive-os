use crate::config::Config;
use crate::db::Brain;
use anyhow::Result;
use serde_json::{json, Value};
use std::path::Path;

pub fn log(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open(&Config::brain_path(home, &cfg.name))?;
    let rows = brain.cycles()?;
    let items: Vec<Value> = rows
        .iter()
        .map(|r| {
            json!({
                "id": r.id,
                "status": r.status,
                "discovered": r.discovered,
                "imported": r.imported,
                "skipped": r.skipped,
                "embedded": r.embedded,
                "exported": r.exported,
                "vault": r.vault,
                "started": r.started,
                "ended": r.ended,
                "detail": serde_json::from_str::<Value>(&r.detail_json).unwrap_or(Value::Null),
            })
        })
        .collect();
    Ok(json!({ "cycles": items, "count": items.len() }))
}

pub fn last(home: &Path, cfg: &Config) -> Result<Value> {
    let all = log(home, cfg)?;
    let cycles = all
        .get("cycles")
        .and_then(|c| c.as_array())
        .cloned()
        .unwrap_or_default();
    Ok(cycles.last().cloned().unwrap_or(json!(null)))
}
