mod config;
mod cycle;
mod db;
mod embed;
mod health;
mod ingest;
mod search;

use anyhow::Result;
use clap::{Parser, Subcommand};
use config::{Config, Vault};
use std::path::PathBuf;
use std::process::ExitCode;

#[derive(Parser)]
#[command(
    name = "cos-engine",
    about = "Cognitive OS memory engine (M0 walking skeleton)"
)]
struct Cli {
    #[arg(long, global = true)]
    home: Option<PathBuf>,
    #[command(subcommand)]
    cmd: Cmd,
}

#[derive(Subcommand)]
enum Cmd {
    Init {
        #[arg(long)]
        vaults: Vec<PathBuf>,
        #[arg(long, default_value = "ollama:nomic-embed-text")]
        embedder: String,
        #[arg(long)]
        name: Option<String>,
        #[arg(long, default_value = "http://127.0.0.1:11434")]
        embedder_url: String,
    },
    Cycle {
        #[arg(long)]
        json: bool,
    },
    Search {
        query: String,
        #[arg(long)]
        json: bool,
        #[arg(long, default_value_t = 8)]
        k: usize,
    },
    Health {
        #[command(subcommand)]
        what: HealthCmd,
    },
}

#[derive(Subcommand)]
enum HealthCmd {
    Log,
    Last,
}

fn main() -> ExitCode {
    match run() {
        Ok(code) => code,
        Err(e) => {
            eprintln!("error: {e:#}");
            ExitCode::from(1)
        }
    }
}

fn run() -> Result<ExitCode> {
    let cli = Cli::parse();
    let home = cli.home.unwrap_or_else(Config::default_home);
    match cli.cmd {
        Cmd::Init {
            vaults,
            embedder,
            name,
            embedder_url,
        } => {
            if vaults.is_empty() {
                anyhow::bail!("pass at least one --vaults PATH");
            }
            let name = name.unwrap_or_else(|| "default".into());
            let (_model, dims) = Config::parse_embedder(&embedder);
            let cfg = Config {
                name: name.clone(),
                embedder,
                embedder_url,
                embed_dims: dims,
                vaults: vaults
                    .into_iter()
                    .enumerate()
                    .map(|(i, p)| Vault {
                        id: if i == 0 {
                            "fixture".into()
                        } else {
                            format!("vault-{i}")
                        },
                        path: p.canonicalize().unwrap_or(p).to_string_lossy().into_owned(),
                    })
                    .collect(),
            };
            cfg.save(&home)?;
            let brain = Config::brain_path(&home, &cfg.name);
            db::Brain::open(&brain)?;
            println!("initialized brain {} at {}", cfg.name, brain.display());
            println!("config {}", home.join("config.toml").display());
            Ok(ExitCode::SUCCESS)
        }
        Cmd::Cycle { json } => {
            let cfg = Config::load(&home)?;
            let report = cycle::run(&home, &cfg)?;
            if json {
                println!("{}", serde_json::to_string_pretty(&report.to_json())?);
            } else {
                println!(
                    "{} discovered={} imported={} skipped={} embedded={} gate={:?}",
                    report.status,
                    report.discovered,
                    report.imported,
                    report.skipped,
                    report.embedded,
                    report.failed_gate
                );
            }
            Ok(ExitCode::from(cycle::exit_code(&report) as u8))
        }
        Cmd::Search { query, json, k } => {
            let cfg = Config::load(&home)?;
            let out = search::search(&home, &cfg, &query, k)?;
            if json {
                println!("{}", serde_json::to_string_pretty(&out)?);
            } else {
                println!("{}", out);
            }
            Ok(ExitCode::SUCCESS)
        }
        Cmd::Health { what } => {
            let cfg = Config::load(&home)?;
            let out = match what {
                HealthCmd::Log => health::log(&home, &cfg)?,
                HealthCmd::Last => health::last(&home, &cfg)?,
            };
            println!("{}", serde_json::to_string_pretty(&out)?);
            Ok(ExitCode::SUCCESS)
        }
    }
}
