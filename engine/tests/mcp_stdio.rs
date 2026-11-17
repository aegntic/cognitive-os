use anyhow::{Context, Result};
use rmcp::{model::CallToolRequestParams, transport::TokioChildProcess, ServiceExt};
use serde_json::{json, Value};
use std::{path::Path, process::Output, time::Duration};
use tempfile::TempDir;
use tokio::{process::Command, time::timeout};

const LIMIT: Duration = Duration::from_secs(10);
const BIN: &str = env!("CARGO_BIN_EXE_cos-engine");

async fn cli(home: &Path, args: &[&str]) -> Result<Output> {
    let mut command = Command::new(BIN);
    command
        .arg("--home")
        .arg(home)
        .args(args)
        .kill_on_drop(true);
    timeout(LIMIT, command.output())
        .await
        .context("CLI timeout")?
        .context("run CLI")
}

async fn fixture() -> Result<TempDir> {
    let home = tempfile::tempdir()?;
    let vault = Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/vault");
    let output = cli(
        home.path(),
        &[
            "init",
            "--vaults",
            vault.to_str().unwrap(),
            "--embedder",
            "mock:hash",
        ],
    )
    .await?;
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let output = cli(home.path(), &["cycle", "--json"]).await?;
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let cycle: Value = serde_json::from_slice(&output.stdout)?;
    assert_eq!(cycle["status"], "green");
    assert_eq!(cycle["imported"], 3);
    Ok(home)
}

fn request(name: &'static str, args: Value) -> CallToolRequestParams {
    CallToolRequestParams::new(name).with_arguments(args.as_object().unwrap().clone())
}

fn tool_json(result: &rmcp::model::CallToolResult) -> Result<Value> {
    assert_ne!(result.is_error, Some(true));
    let text = result
        .content
        .first()
        .and_then(|item| item.as_text())
        .context("tool result must contain JSON text")?;
    Ok(serde_json::from_str(&text.text)?)
}

// CLI and MCP perform independent hash-map ranking; normalize tied-hit order and
// elapsed time, but compare every returned hit and all other metadata.
fn normalized_search(mut value: Value) -> Value {
    value["_meta"].as_object_mut().unwrap().remove("ms");
    value["hits"]
        .as_array_mut()
        .unwrap()
        .sort_by_key(|hit| hit["path"].as_str().unwrap().to_owned());
    value
}

#[tokio::test]
async fn sdk_roundtrip_matches_cli_and_does_not_change_database() -> Result<()> {
    let home = fixture().await?;
    let db = home.path().join("brains/default/brain.sqlite");
    let before = std::fs::read(&db)?;
    let expected_search: Value = serde_json::from_slice(
        &cli(home.path(), &["search", "silent failure", "--json"])
            .await?
            .stdout,
    )?;
    let mut expected_health: Value =
        serde_json::from_slice(&cli(home.path(), &["health", "last"]).await?.stdout)?;
    if let Some(object) = expected_health.as_object_mut() {
        object.remove("detail");
    }
    let mut command = Command::new(BIN);
    command
        .arg("--home")
        .arg(home.path())
        .arg("serve")
        .kill_on_drop(true);
    let client = timeout(LIMIT, ().serve(TokioChildProcess::new(command)?)).await??;
    let tools = timeout(LIMIT, client.list_all_tools()).await??;
    let mut names: Vec<_> = tools.iter().map(|tool| tool.name.as_ref()).collect();
    names.sort_unstable();
    assert_eq!(names, ["health", "search"]);
    let result = timeout(
        LIMIT,
        client.call_tool(request("search", json!({"query":"silent failure"}))),
    )
    .await??;
    assert_eq!(
        normalized_search(tool_json(&result)?),
        normalized_search(expected_search)
    );
    let result = timeout(LIMIT, client.call_tool(request("health", json!({})))).await??;
    assert_eq!(tool_json(&result)?, expected_health);
    timeout(LIMIT, client.cancel()).await??;
    assert_eq!(std::fs::read(db)?, before, "MCP reads changed the database");
    Ok(())
}

#[tokio::test]
async fn invalid_requests_are_rejected_and_tool_failure_is_recoverable() -> Result<()> {
    let home = fixture().await?;
    let db = home.path().join("brains/default/brain.sqlite");
    let before = std::fs::read(&db)?;
    let mut command = Command::new(BIN);
    command
        .arg("--home")
        .arg(home.path())
        .arg("serve")
        .kill_on_drop(true);
    let client = timeout(LIMIT, ().serve(TokioChildProcess::new(command)?)).await??;
    for args in [
        json!({"query":""}),
        json!({"query":" "}),
        json!({"query":"x","k":0}),
        json!({"query":"x","k":101}),
        json!({"query":"x".repeat(4097)}),
        json!({"query":"x","unrecognized":true}),
        json!({"query":"x","home":"/some/other/brain"}),
        json!({"query":"x","brain":"other"}),
    ] {
        let result = timeout(LIMIT, client.call_tool(request("search", args))).await?;
        assert!(
            result
                .as_ref()
                .map_or(true, |reply| reply.is_error == Some(true)),
            "invalid search arguments were accepted: {result:?}"
        );
    }
    let unknown = timeout(LIMIT, client.call_tool(request("not_a_tool", json!({})))).await?;
    assert!(unknown
        .as_ref()
        .map_or(true, |reply| reply.is_error == Some(true)));
    let bad_fts = timeout(
        LIMIT,
        client.call_tool(request("search", json!({"query":"\""}))),
    )
    .await??;
    assert_eq!(bad_fts.is_error, Some(true));
    let recovered = timeout(LIMIT, client.call_tool(request("health", json!({})))).await??;
    assert_eq!(tool_json(&recovered)?["status"], "green");
    timeout(LIMIT, client.cancel()).await??;
    assert_eq!(
        std::fs::read(db)?,
        before,
        "failed queries changed the database"
    );
    Ok(())
}

#[tokio::test]
async fn serve_requires_explicit_existing_brain_without_creating_state() -> Result<()> {
    let root = tempfile::tempdir()?;
    let mut command = Command::new(BIN);
    command
        .arg("serve")
        .env("HOME", root.path())
        .env_remove("COS_HOME")
        .kill_on_drop(true);
    let output = timeout(LIMIT, command.output()).await??;
    assert!(!output.status.success());
    assert!(String::from_utf8_lossy(&output.stderr).contains("requires --home"));
    assert_eq!(std::fs::read_dir(root.path())?.count(), 0);

    let missing = root.path().join("absent");
    let output = cli(&missing, &["serve"]).await?;
    assert!(!output.status.success());
    assert!(!missing.exists());

    let home = fixture().await?;
    let db = home.path().join("brains/default/brain.sqlite");
    std::fs::remove_file(&db)?;
    let output = cli(home.path(), &["serve"]).await?;
    assert!(!output.status.success());
    assert!(!db.exists(), "server recreated a missing database");
    Ok(())
}
