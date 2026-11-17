use crate::{config::Config, db::Brain, health, search};
use anyhow::Result;
use rmcp::{
    handler::server::wrapper::Parameters,
    model::{CallToolResult, ContentBlock},
    schemars, tool, tool_router, ErrorData, ServiceExt,
};
use serde_json::Value;
use std::{path::PathBuf, sync::Arc, time::Duration};
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
    health_slot: Arc<Semaphore>,
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
        self.execute(self.query_slot.clone(), Duration::from_secs(6), move || {
            search::search_with_timeout(&home, &cfg, &request.query, k, Duration::from_secs(5))
        })
        .await
    }

    #[tool(
        description = "Read the latest recorded cycle of this configured Cognitive OS brain. Null means no recorded cycle, not healthy.",
        annotations(read_only_hint = true, destructive_hint = false)
    )]
    async fn health(&self) -> Result<CallToolResult, ErrorData> {
        let home = self.home.clone();
        let cfg = self.config.clone();
        self.execute(
            self.health_slot.clone(),
            Duration::from_secs(6),
            move || health::last(&home, &cfg),
        )
        .await
    }
}

impl MemoryServer {
    async fn execute(
        &self,
        slot: Arc<Semaphore>,
        timeout: Duration,
        operation: impl FnOnce() -> Result<Value> + Send + 'static,
    ) -> Result<CallToolResult, ErrorData> {
        // No waiting queue: an occupied lane rejects immediately. Keep its permit
        // inside the worker even after timeout, preventing orphan worker growth.
        let permit = slot.try_acquire_owned().map_err(|_| {
            ErrorData::internal_error(
                "memory tool busy; retry after the active request finishes",
                None,
            )
        })?;
        let worker = tokio::task::spawn_blocking(move || {
            let _permit = permit;
            operation()
        });
        let result = match tokio::time::timeout(timeout, worker).await {
            Ok(joined) => {
                joined.map_err(|_| ErrorData::internal_error("memory worker failed", None))?
            }
            Err(_) => {
                return Ok(CallToolResult::error(vec![ContentBlock::text(
                    "Memory query deadline exceeded. Retry after the active operation finishes.",
                )]))
            }
        };
        match result {
            Ok(value) => Ok(CallToolResult::success(vec![ContentBlock::text(
                value.to_string(),
            )])),
            Err(_) => {
                eprintln!(
                    "memory tool failed; detailed error suppressed to protect configuration data"
                );
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
        health_slot: Arc::new(Semaphore::new(1)),
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicBool, Ordering};

    fn server() -> MemoryServer {
        MemoryServer {
            home: PathBuf::new(),
            config: Config {
                name: "test".into(),
                embedder: "mock:hash".into(),
                embedder_url: String::new(),
                embed_dims: 3,
                vaults: vec![],
            },
            query_slot: Arc::new(Semaphore::new(1)),
            health_slot: Arc::new(Semaphore::new(1)),
        }
    }

    #[tokio::test]
    async fn occupied_lane_rejects_without_starting_or_queuing_work() {
        let server = server();
        let _permit = server.query_slot.clone().acquire_owned().await.unwrap();
        let ran = Arc::new(AtomicBool::new(false));
        let marker = ran.clone();
        let result = server
            .execute(
                server.query_slot.clone(),
                Duration::from_secs(1),
                move || {
                    marker.store(true, Ordering::SeqCst);
                    Ok(Value::Null)
                },
            )
            .await;
        assert!(result.is_err());
        assert!(!ran.load(Ordering::SeqCst));
    }

    #[tokio::test]
    async fn timeout_returns_promptly_without_spawning_unbounded_orphans() {
        let server = server();
        let (release, receive) = std::sync::mpsc::channel();
        let result = tokio::time::timeout(
            Duration::from_secs(1),
            server.execute(
                server.query_slot.clone(),
                Duration::from_millis(20),
                move || {
                    receive.recv_timeout(Duration::from_secs(2)).unwrap();
                    Ok(Value::Null)
                },
            ),
        )
        .await
        .unwrap()
        .unwrap();
        assert_eq!(result.is_error, Some(true));
        assert!(server.query_slot.clone().try_acquire_owned().is_err());
        let health = server
            .execute(server.health_slot.clone(), Duration::from_secs(1), || {
                Ok(Value::Null)
            })
            .await
            .unwrap();
        assert_eq!(health.is_error, Some(false));
        release.send(()).unwrap();
        tokio::time::timeout(Duration::from_secs(1), async {
            loop {
                if server.query_slot.available_permits() == 1 {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(1)).await;
            }
        })
        .await
        .unwrap();
        assert!(server.query_slot.clone().try_acquire_owned().is_ok());
    }
}
