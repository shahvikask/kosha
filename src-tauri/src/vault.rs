use rusqlite::{params_from_iter, types::ValueRef, Connection};
use serde_json::{Map, Value};
use std::path::PathBuf;
use std::sync::Mutex;

pub struct Vault {
    pub conn: Mutex<Option<Connection>>,
    pub path: Mutex<PathBuf>,
}

impl Vault {
    pub fn new() -> Self {
        Self {
            conn: Mutex::new(None),
            path: Mutex::new(PathBuf::new()),
        }
    }
}

/// Program files = the .exe (shared). Vault stays per Windows user so
/// a guest login cannot read the family books.
pub fn vault_file() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
        .ok_or_else(|| "Could not resolve %LOCALAPPDATA%.".to_string())?;
    let dir = base.join("GrowUrDhan");
    std::fs::create_dir_all(&dir).map_err(|e| format!("Could not create {}: {e}", dir.display()))?;
    Ok(dir.join("vault.db"))
}

fn bind(v: &Value) -> rusqlite::Result<Box<dyn rusqlite::ToSql>> {
    Ok(match v {
        Value::Null => Box::new(rusqlite::types::Null),
        Value::Bool(b) => Box::new(*b),
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                Box::new(i)
            } else if let Some(u) = n.as_u64() {
                Box::new(u as i64)
            } else {
                Box::new(n.as_f64().unwrap_or(0.0))
            }
        }
        Value::String(s) => Box::new(s.clone()),
        other => Box::new(other.to_string()),
    })
}

fn cell(v: ValueRef<'_>) -> Value {
    match v {
        ValueRef::Null => Value::Null,
        ValueRef::Integer(i) => Value::from(i),
        ValueRef::Real(f) => Value::from(f),
        ValueRef::Text(t) => Value::String(String::from_utf8_lossy(t).into_owned()),
        ValueRef::Blob(_) => Value::Null,
    }
}

pub fn open(vault: &Vault) -> Result<String, String> {
    let path = vault_file()?;
    let conn = Connection::open(&path).map_err(|e| format!("Could not open {}: {e}", path.display()))?;
    conn.busy_timeout(std::time::Duration::from_secs(5))
        .map_err(|e| e.to_string())?;
    let _ = conn.execute_batch(
        "PRAGMA foreign_keys = ON;
         PRAGMA journal_mode = WAL;
         PRAGMA synchronous = NORMAL;",
    );
    *vault.path.lock().map_err(|e| e.to_string())? = path.clone();
    *vault.conn.lock().map_err(|e| e.to_string())? = Some(conn);
    Ok(path.to_string_lossy().into_owned())
}

pub fn exec(vault: &Vault, sql: &str, params: &[Value]) -> Result<(), String> {
    let mut guard = vault.conn.lock().map_err(|e| e.to_string())?;
    let conn = guard.as_mut().ok_or("Vault is not open.")?;
    if params.is_empty() {
        conn.execute_batch(sql).map_err(|e| format!("{e} [{sql}]"))?;
        return Ok(());
    }
    let bound: Vec<Box<dyn rusqlite::ToSql>> = params.iter().map(|p| bind(p).unwrap()).collect();
    conn.execute(sql, params_from_iter(bound.iter().map(|b| b.as_ref())))
        .map_err(|e| format!("{e} [{sql}]"))?;
    Ok(())
}

pub fn query(vault: &Vault, sql: &str, params: &[Value]) -> Result<Vec<Map<String, Value>>, String> {
    let mut guard = vault.conn.lock().map_err(|e| e.to_string())?;
    let conn = guard.as_mut().ok_or("Vault is not open.")?;
    let bound: Vec<Box<dyn rusqlite::ToSql>> = params.iter().map(|p| bind(p).unwrap()).collect();
    let mut stmt = conn.prepare(sql).map_err(|e| format!("{e} [{sql}]"))?;
    let names: Vec<String> = stmt.column_names().iter().map(|s| (*s).to_string()).collect();
    let mut rows = stmt
        .query(params_from_iter(bound.iter().map(|b| b.as_ref())))
        .map_err(|e| format!("{e} [{sql}]"))?;
    let mut out = Vec::new();
    while let Some(row) = rows.next().map_err(|e| e.to_string())? {
        let mut map = Map::new();
        for (i, name) in names.iter().enumerate() {
            let v = row.get_ref(i).map_err(|e| e.to_string())?;
            map.insert(name.clone(), cell(v));
        }
        out.push(map);
    }
    Ok(out)
}

pub fn close(vault: &Vault) -> Result<(), String> {
    *vault.conn.lock().map_err(|e| e.to_string())? = None;
    Ok(())
}
