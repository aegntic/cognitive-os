use anyhow::{Context, Result};
use rusqlite::{params, Connection, OptionalExtension};
use std::path::Path;
use std::time::{Duration, Instant};

pub struct Brain {
    pub conn: Connection,
}

impl Brain {
    pub fn open_read_only(path: &Path) -> Result<Self> {
        Self::open_read_only_with_timeout(path, Duration::from_secs(1))
    }

    pub fn open_read_only_with_timeout(path: &Path, timeout: Duration) -> Result<Self> {
        let deadline = Instant::now()
            .checked_add(timeout)
            .context("invalid database deadline")?;
        let remaining = || {
            deadline
                .checked_duration_since(Instant::now())
                .filter(|left| !left.is_zero())
                .context("read-only brain deadline exceeded")
        };
        remaining()?;
        let conn = Connection::open_with_flags(
            path,
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NOFOLLOW,
        )
        .with_context(|| format!("open existing brain {}", path.display()))?;
        conn.busy_timeout(remaining()?)?;
        conn.progress_handler(1000, Some(move || Instant::now() >= deadline));
        // Opening alone may defer WAL shared-memory access until the first query.
        // Do not use immutable=1: another process can legitimately run cycles.
        conn.query_row("SELECT COUNT(*) FROM sqlite_schema", [], |row| row.get::<_, i64>(0))
            .context("read brain schema; a WAL database needs accessible -wal/-shm sidecars or a writable containing directory; checkpoint an offline copy before serving it from read-only storage")?;
        for query in [
            "SELECT id, path, source_id, title, mtime, hash, confidence, archived, first_seen, last_access FROM pages LIMIT 0",
            "SELECT id, page_id, ord, content, hash, embedding FROM chunks LIMIT 0",
            "SELECT rowid, content FROM chunks_fts LIMIT 0",
            "SELECT id, started, ended, vault, discovered, imported, skipped, embedded, exported, status, detail_json FROM cycles LIMIT 0",
        ] {
            conn.busy_timeout(remaining()?)?;
            conn.prepare(query).context("existing database is not an initialized Cognitive OS brain")?;
        }
        remaining()?;
        conn.progress_handler(0, None::<fn() -> bool>);
        Ok(Self { conn })
    }

    pub fn open(path: &Path) -> Result<Self> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let conn =
            Connection::open(path).with_context(|| format!("open brain {}", path.display()))?;
        conn.execute_batch(
            "
            PRAGMA journal_mode=WAL;
            PRAGMA foreign_keys=ON;
            CREATE TABLE IF NOT EXISTS pages (
              id INTEGER PRIMARY KEY,
              path TEXT UNIQUE NOT NULL,
              source_id TEXT NOT NULL,
              title TEXT NOT NULL,
              mtime INTEGER NOT NULL,
              hash TEXT NOT NULL,
              confidence REAL NOT NULL DEFAULT 1.0,
              archived INTEGER NOT NULL DEFAULT 0,
              first_seen INTEGER NOT NULL,
              last_access INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS chunks (
              id INTEGER PRIMARY KEY,
              page_id INTEGER NOT NULL REFERENCES pages(id) ON DELETE CASCADE,
              ord INTEGER NOT NULL,
              content TEXT NOT NULL,
              hash TEXT NOT NULL,
              embedding BLOB
            );
            CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
              content, content='chunks', content_rowid='id'
            );
            CREATE TABLE IF NOT EXISTS cycles (
              id INTEGER PRIMARY KEY,
              started INTEGER NOT NULL,
              ended INTEGER NOT NULL,
              vault TEXT NOT NULL,
              discovered INTEGER NOT NULL,
              imported INTEGER NOT NULL,
              skipped INTEGER NOT NULL,
              embedded INTEGER NOT NULL,
              exported INTEGER NOT NULL,
              status TEXT NOT NULL CHECK(status IN ('green','failed')),
              detail_json TEXT NOT NULL
            );
            CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
              INSERT INTO chunks_fts(rowid, content) VALUES (new.id, new.content);
            END;
            CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
              INSERT INTO chunks_fts(chunks_fts, rowid, content) VALUES('delete', old.id, old.content);
            END;
            CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE ON chunks BEGIN
              INSERT INTO chunks_fts(chunks_fts, rowid, content) VALUES('delete', old.id, old.content);
              INSERT INTO chunks_fts(rowid, content) VALUES (new.id, new.content);
            END;
            ",
        )?;
        Ok(Self { conn })
    }

    pub fn upsert_page(
        &self,
        path: &str,
        source_id: &str,
        title: &str,
        mtime: i64,
        hash: &str,
        now: i64,
    ) -> Result<(i64, bool)> {
        let existing: Option<(i64, String)> = self
            .conn
            .query_row(
                "SELECT id, hash FROM pages WHERE path = ?1",
                params![path],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .ok();
        if let Some((id, old_hash)) = existing {
            if old_hash == hash {
                return Ok((id, false));
            }
            self.conn.execute(
                "UPDATE pages SET title=?1, mtime=?2, hash=?3 WHERE id=?4",
                params![title, mtime, hash, id],
            )?;
            self.conn
                .execute("DELETE FROM chunks WHERE page_id=?1", params![id])?;
            return Ok((id, true));
        }
        self.conn.execute(
            "INSERT INTO pages (path, source_id, title, mtime, hash, first_seen)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![path, source_id, title, mtime, hash, now],
        )?;
        Ok((self.conn.last_insert_rowid(), true))
    }

    pub fn insert_chunk(
        &self,
        page_id: i64,
        ord: i32,
        content: &str,
        hash: &str,
        embedding: Option<&[u8]>,
    ) -> Result<i64> {
        self.conn.execute(
            "INSERT INTO chunks (page_id, ord, content, hash, embedding)
             VALUES (?1, ?2, ?3, ?4, ?5)",
            params![page_id, ord, content, hash, embedding],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    pub fn record_cycle(
        &self,
        started: i64,
        ended: i64,
        vault: &str,
        discovered: i64,
        imported: i64,
        skipped: i64,
        embedded: i64,
        exported: i64,
        status: &str,
        detail: &serde_json::Value,
    ) -> Result<()> {
        self.conn.execute(
            "INSERT INTO cycles (started, ended, vault, discovered, imported, skipped,
                                 embedded, exported, status, detail_json)
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10)",
            params![
                started,
                ended,
                vault,
                discovered,
                imported,
                skipped,
                embedded,
                exported,
                status,
                detail.to_string()
            ],
        )?;
        Ok(())
    }

    pub fn latest_cycle_summary(&self) -> Result<Option<CycleRow>> {
        // Do not even materialize historical free-form diagnostics for MCP.
        Ok(self
            .conn
            .query_row(
                "SELECT id, started, ended, vault, discovered, imported, skipped,
                    embedded, exported, status, '{}' AS detail_json
             FROM cycles ORDER BY id DESC LIMIT 1",
                [],
                CycleRow::from_row,
            )
            .optional()?)
    }

    pub fn latest_cycle(&self) -> Result<Option<CycleRow>> {
        Ok(self
            .conn
            .query_row(
                "SELECT id, started, ended, vault, discovered, imported, skipped,
                    embedded, exported, status, detail_json
             FROM cycles ORDER BY id DESC LIMIT 1",
                [],
                CycleRow::from_row,
            )
            .optional()?)
    }

    pub fn cycles(&self) -> Result<Vec<CycleRow>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, started, ended, vault, discovered, imported, skipped,
                    embedded, exported, status, detail_json
             FROM cycles ORDER BY id ASC",
        )?;
        let rows = stmt
            .query_map([], CycleRow::from_row)?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct CycleRow {
    pub id: i64,
    pub started: i64,
    pub ended: i64,
    pub vault: String,
    pub discovered: i64,
    pub imported: i64,
    pub skipped: i64,
    pub embedded: i64,
    pub exported: i64,
    pub status: String,
    pub detail_json: String,
}

impl CycleRow {
    fn from_row(r: &rusqlite::Row<'_>) -> rusqlite::Result<Self> {
        Ok(Self {
            id: r.get(0)?,
            started: r.get(1)?,
            ended: r.get(2)?,
            vault: r.get(3)?,
            discovered: r.get(4)?,
            imported: r.get(5)?,
            skipped: r.get(6)?,
            embedded: r.get(7)?,
            exported: r.get(8)?,
            status: r.get(9)?,
            detail_json: r.get(10)?,
        })
    }
}

#[cfg(test)]
mod readonly_tests {
    use super::*;

    #[test]
    fn readonly_open_validates_the_schema_before_serving() {
        let home = tempfile::tempdir().unwrap();
        let path = home.path().join("brain.sqlite");
        std::fs::write(&path, "not a database").unwrap();
        let error = Brain::open_read_only(&path)
            .err()
            .expect("invalid database must fail");
        assert!(error.to_string().contains("read brain schema"));
    }

    #[test]
    fn readonly_open_rejects_unrelated_and_incomplete_engine_schemas() {
        let home = tempfile::tempdir().unwrap();
        let path = home.path().join("unrelated.sqlite");
        let unrelated = Connection::open(&path).unwrap();
        unrelated
            .execute("CREATE TABLE unrelated (id INTEGER)", [])
            .unwrap();
        assert!(Brain::open_read_only(&path)
            .err()
            .unwrap()
            .to_string()
            .contains("initialized Cognitive OS brain"));
        let path = home.path().join("incomplete.sqlite");
        let brain = Brain::open(&path).unwrap();
        brain
            .conn
            .execute("ALTER TABLE cycles DROP COLUMN detail_json", [])
            .unwrap();
        assert!(Brain::open_read_only(&path).is_err());
    }

    #[test]
    fn readonly_schema_probe_obeys_remaining_busy_budget() {
        let home = tempfile::tempdir().unwrap();
        let path = home.path().join("brain.sqlite");
        let writer = Brain::open(&path).unwrap();
        writer
            .conn
            .execute_batch("PRAGMA journal_mode=DELETE; BEGIN EXCLUSIVE;")
            .unwrap();
        let start = Instant::now();
        let result = Brain::open_read_only_with_timeout(&path, Duration::from_millis(50));
        let elapsed = start.elapsed();
        writer.conn.execute_batch("ROLLBACK;").unwrap();
        assert!(result.is_err());
        assert!(elapsed < Duration::from_millis(500), "elapsed: {elapsed:?}");
    }

    #[cfg(unix)]
    #[test]
    fn readonly_wal_reports_missing_sidecar_permissions() {
        use std::os::unix::fs::PermissionsExt;
        let home = tempfile::tempdir().unwrap();
        let path = home.path().join("brain.sqlite");
        drop(Brain::open(&path).unwrap());
        assert!(!home.path().join("brain.sqlite-wal").exists());
        std::fs::set_permissions(home.path(), std::fs::Permissions::from_mode(0o500)).unwrap();
        // Privileged/container root can bypass mode bits. Restore before any assertions.
        let probe = home.path().join("permission-probe");
        let bypasses_permissions = std::fs::File::create(&probe).is_ok();
        let result = Brain::open_read_only(&path);
        std::fs::set_permissions(home.path(), std::fs::Permissions::from_mode(0o700)).unwrap();
        if bypasses_permissions {
            std::fs::remove_file(probe).unwrap();
            eprintln!("permission-denial assertion skipped: process bypasses directory mode bits");
            return;
        }
        let error = result
            .err()
            .expect("WAL without sidecars needs a writable directory");
        assert!(error.to_string().contains("WAL database needs accessible"));
    }
}
