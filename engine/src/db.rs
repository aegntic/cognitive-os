use anyhow::{Context, Result};
use rusqlite::{params, Connection};
use std::path::Path;

pub struct Brain {
    pub conn: Connection,
}

impl Brain {
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

    pub fn cycles(&self) -> Result<Vec<CycleRow>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, started, ended, vault, discovered, imported, skipped,
                    embedded, exported, status, detail_json
             FROM cycles ORDER BY id ASC",
        )?;
        let rows = stmt
            .query_map([], |r| {
                Ok(CycleRow {
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
            })?
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
