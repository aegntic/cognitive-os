use crate::config::Config;
use crate::db::{Brain, CycleRow};
use anyhow::Result;
use serde_json::{json, Value};
use std::path::Path;

fn cycle_summary_json(r: &CycleRow) -> Value {
    json!({
        "id": r.id, "status": r.status, "discovered": r.discovered,
        "imported": r.imported, "skipped": r.skipped, "embedded": r.embedded,
        "exported": r.exported, "vault": r.vault, "started": r.started,
        "ended": r.ended,
    })
}

fn cycle_json(r: &CycleRow) -> Value {
    let summary = cycle_summary_json(r);
    let detail = serde_json::from_str::<Value>(&r.detail_json).unwrap_or(Value::Null);
    json!({
        "id": summary["id"], "status": summary["status"], "discovered": summary["discovered"],
        "imported": summary["imported"], "skipped": summary["skipped"], "embedded": summary["embedded"],
        "exported": summary["exported"], "vault": summary["vault"], "started": summary["started"],
        "ended": summary["ended"], "detail": detail,
    })
}

pub fn last_for_mcp(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open_read_only(&Config::checked_brain_path(home, &cfg.name)?)?;
    Ok(brain
        .latest_cycle_summary()?
        .as_ref()
        .map(cycle_summary_json)
        .unwrap_or(Value::Null))
}

pub fn log(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open_read_only(&Config::checked_brain_path(home, &cfg.name)?)?;
    let items: Vec<Value> = brain.cycles()?.iter().map(cycle_json).collect();
    Ok(json!({ "cycles": items, "count": items.len() }))
}

pub fn last(home: &Path, cfg: &Config) -> Result<Value> {
    let brain = Brain::open_read_only(&Config::checked_brain_path(home, &cfg.name)?)?;
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
    fn mcp_health_omits_historical_error_credentials_but_cli_preserves_details() {
        let home = tempfile::tempdir().unwrap();
        let cfg = Config {
            name: "test".into(),
            embedder: "mock:hash".into(),
            embedder_url: String::new(),
            embed_dims: 3,
            vaults: vec![],
        };
        let brain = Brain::open(&Config::brain_path(home.path(), &cfg.name)).unwrap();
        assert_eq!(last_for_mcp(home.path(), &cfg).unwrap(), Value::Null);
        let detail = json!({"error":"embedder unreachable at http://sentinel-user:sentinel-secret@localhost"});
        brain
            .record_cycle(1, 1, "fixture", 1, 0, 0, 0, 0, "failed", &detail)
            .unwrap();
        let summary = last_for_mcp(home.path(), &cfg).unwrap();
        assert_eq!(summary["status"], "failed");
        assert!(summary.get("detail").is_none());
        assert!(!summary.to_string().contains("sentinel"));
        assert_eq!(last(home.path(), &cfg).unwrap()["detail"], detail);
    }

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
