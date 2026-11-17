use crate::{config::Config, db::Brain, health, search};
use anyhow::Result;
use rmcp::{
    handler::server::wrapper::Parameters,
    model::{CallToolResult, ContentBlock},
    schemars, tool, tool_router, ErrorData, ServiceExt,
};
use serde_json::Value;
use std::{path::PathBuf, sync::Arc};
use tokio::sync::Semaphore;

#[derive(Debug, serde::Deserialize, schemars::JsonSchema)]
#[serde(deny_unknown_fields)]
struct SearchRequest {
    /// Search text, 1 to 4096 bytes. Uses the configured embedder.
    query: String,
    /// Maximum returned chunks, between 1 and 100 (default 8).
    k: Option<usize>,
}

#[derive(Clone)]
struct MemoryServer {
    home: PathBuf,
    config: Config,
    // Serialize expensive blocking queries without blocking the protocol runtime.
    query_slot: Arc<Semaphore>,
}

#[tool_router(server_handler)]
impl MemoryServer {
    #[tool(
        description = "Search this configured Cognitive OS brain. Returns indexed snippets; no vault or index writes.",
        annotations(read_only_hint = true, destructive_hint = false)
    )]
    async fn search(
        &self,
        Parameters(request): Parameters<SearchRequest>,
    ) -> Result<CallToolResult, ErrorData> {
        let k = request.k.unwrap_or(8);
        if request.query.trim().is_empty() || request.query.len() > 4096 || !(1..=100).contains(&k)
        {
            return Err(ErrorData::invalid_params(
                "query must be 1..4096 bytes and k must be 1..100",
                None,
            ));
        }
        let home = self.home.clone();
        let cfg = self.config.clone();
        self.execute(move || search::search(&home, &cfg, &request.query, k))
            .await
    }

    #[tool(
        description = "Read the latest recorded cycle of this configured Cognitive OS brain. Null means no recorded cycle, not healthy.",
        annotations(read_only_hint = true, destructive_hint = false)
    )]
    async fn health(&self) -> Result<CallToolResult, ErrorData> {
        let home = self.home.clone();
        let cfg = self.config.clone();
        self.execute(move || health::last(&home, &cfg)).await
    }
}

impl MemoryServer {
    async fn execute(
        &self,
        operation: impl FnOnce() -> Result<Value> + Send + 'static,
    ) -> Result<CallToolResult, ErrorData> {
        let permit = self
            .query_slot
            .clone()
            .acquire_owned()
            .await
            .map_err(|_| ErrorData::internal_error("memory service unavailable", None))?;
        let result = tokio::task::spawn_blocking(move || {
            let _permit = permit;
            operation()
        })
        .await
        .map_err(|_| ErrorData::internal_error("memory worker failed", None))?;
        match result {
            Ok(value) => Ok(CallToolResult::success(vec![ContentBlock::text(
                value.to_string(),
            )])),
            Err(error) => {
                eprintln!("memory tool failed: {error:#}");
                Ok(CallToolResult::error(vec![ContentBlock::text(
                    "Memory query failed. Check the server diagnostics and configured embedder.",
                )]))
            }
        }
    }
}

pub fn serve(home: PathBuf) -> Result<()> {
    let config = Config::load(&home)?;
    // Fail closed: serving never initializes or migrates an absent database.
    Brain::open_read_only(&Config::brain_path(&home, &config.name))?;
    let server = MemoryServer {
        home,
        config,
        query_slot: Arc::new(Semaphore::new(1)),
    };
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?
        .block_on(async {
            server
                .serve(rmcp::transport::stdio())
                .await?
                .waiting()
                .await?;
            Ok(())
        })
}
