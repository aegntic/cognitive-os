use crate::config::Config;
use crate::db::{Brain, CycleRow};
use anyhow::Result;
use serde_json::{json, Value};
use std::path::Path;

fn cycle_json(r: &CycleRow) -> Value {
    json!({
        "id": r.id, "status": r.status, "discovered": r.discovered,
        "imported": r.imported, "skipped": r.skipped, "embedded": r.embedded,
        "exported": r.exported, "vault": r.vault, "started": r.started,
        "ended": r.ended,
        "detail": serde_json::from_str::<Value>(&r.detail_json).unwrap_or(Value::Null),
    })
}

pub fn log(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open_read_only(&Config::brain_path(home, &cfg.name))?;
    let items: Vec<Value> = brain.cycles()?.iter().map(cycle_json).collect();
    Ok(json!({ "cycles": items, "count": items.len() }))
}

pub fn last(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open_read_only(&Config::brain_path(home, &cfg.name))?;
    Ok(brain
        .latest_cycle()?
        .as_ref()
        .map(cycle_json)
        .unwrap_or(Value::Null))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn latest_health_does_not_materialize_older_cycles() {
        let home = tempfile::tempdir().unwrap();
        let cfg = Config {
            name: "test".into(),
            embedder: "mock:hash".into(),
            embedder_url: String::new(),
            embed_dims: 3,
            vaults: vec![],
        };
        let brain = Brain::open(&Config::brain_path(home.path(), &cfg.name)).unwrap();
        assert_eq!(last(home.path(), &cfg).unwrap(), Value::Null);
        for n in 0..1000 {
            brain
                .record_cycle(n, n, "fixture", 1, 1, 0, 1, 0, "green", &json!({"n":n}))
                .unwrap();
        }
        // The latest query must not decode this malformed historical row.
        brain
            .conn
            .execute("UPDATE cycles SET imported = 'invalid' WHERE id = 1", [])
            .unwrap();
        let latest = last(home.path(), &cfg).unwrap();
        assert_eq!(latest["id"], 1000);
        assert_eq!(latest["detail"]["n"], 999);
        assert!(log(home.path(), &cfg).is_err());
    }
}
