use serde_json::Value;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager};

const SIDECAR: &str = "http://127.0.0.1:8765";

pub struct Engine {
    child: Mutex<Option<Child>>,
}

impl Engine {
    pub fn new() -> Self {
        Self {
            child: Mutex::new(None),
        }
    }
}

fn log_path() -> PathBuf {
    let base = std::env::var_os("LOCALAPPDATA")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."));
    let dir = base.join("GrowUrDhan");
    let _ = std::fs::create_dir_all(&dir);
    dir.join("engine.log")
}

fn log_line(msg: &str) {
    if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(log_path()) {
        let _ = writeln!(f, "{}", msg);
    }
}

fn engine_name() -> &'static str {
    if cfg!(windows) {
        "gud-engine.exe"
    } else {
        "gud-engine"
    }
}

pub fn find_engine(app: &AppHandle) -> Option<PathBuf> {
    let name = engine_name();
    let mut dirs: Vec<PathBuf> = Vec::new();
    if let Ok(r) = app.path().resource_dir() {
        dirs.push(r.clone());
        dirs.push(r.join("resources"));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(p) = exe.parent() {
            dirs.push(p.to_path_buf());
            dirs.push(p.join("resources"));
        }
    }
    for d in dirs {
        let p = d.join(name);
        if p.exists() {
            log_line(&format!("engine found: {}", p.display()));
            return Some(p);
        }
    }
    log_line("engine not found next to the app or in resources");
    None
}

fn child_alive(child: &mut Child) -> bool {
    match child.try_wait() {
        Ok(None) => true,
        Ok(Some(st)) => {
            log_line(&format!("engine exited: {st}"));
            false
        }
        Err(e) => {
            log_line(&format!("engine wait error: {e}"));
            false
        }
    }
}

pub fn sidecar_up() -> bool {
    ureq::get(&format!("{SIDECAR}/api/cas/health"))
        .timeout(Duration::from_secs(2))
        .call()
        .ok()
        .and_then(|r| r.into_json::<Value>().ok())
        .and_then(|v| v.get("ok").and_then(|x| x.as_bool()))
        .unwrap_or(false)
}

pub fn spawn(app: &AppHandle, engine: &Engine) -> Result<(), String> {
    if sidecar_up() {
        return Ok(());
    }
    {
        let mut slot = engine.child.lock().map_err(|e| e.to_string())?;
        if let Some(child) = slot.as_mut() {
            if child_alive(child) {
                return Ok(());
            }
            *slot = None;
        }
        let path = find_engine(app).ok_or_else(|| {
            "Statement reader (gud-engine.exe) is missing. Reinstall GrowUrDhan.".to_string()
        })?;
        let log = log_path();
        let stdout = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log)
            .ok()
            .map(Stdio::from)
            .unwrap_or_else(Stdio::null);
        let stderr = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log)
            .ok()
            .map(Stdio::from)
            .unwrap_or_else(Stdio::null);
        log_line(&format!("starting {}", path.display()));
        let work = log_path().parent().unwrap().to_path_buf();
        let child = Command::new(&path)
            .current_dir(&work)
            .stdin(Stdio::null())
            .stdout(stdout)
            .stderr(stderr)
            .spawn()
            .map_err(|e| format!("Could not start statement reader: {e}"))?;
        *slot = Some(child);
    }
    Ok(())
}

pub fn ensure(app: &AppHandle, engine: &Engine) -> Result<(), String> {
    spawn(app, engine)?;
    let start = Instant::now();
    while start.elapsed() < Duration::from_secs(25) {
        if sidecar_up() {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(400));
    }
    Err(
        "The statement reader did not start. See %LOCALAPPDATA%\\GrowUrDhan\\engine.log".into(),
    )
}

pub fn parse(body: Value) -> Result<Value, String> {
    let resp = ureq::post(&format!("{SIDECAR}/api/cas/parse"))
        .timeout(Duration::from_secs(180))
        .send_json(body)
        .map_err(|e| format!("Statement reader error: {e}"))?;
    resp.into_json::<Value>()
        .map_err(|e| format!("Could not read statement result: {e}"))
}

pub fn prices() -> Result<Value, String> {
    let resp = ureq::post(&format!("{SIDECAR}/api/cas/prices/refresh"))
        .timeout(Duration::from_secs(180))
        .call()
        .map_err(|e| format!("Price reader error: {e}"))?;
    resp.into_json::<Value>()
        .map_err(|e| format!("Could not read prices: {e}"))
}

pub fn kill(engine: &Engine) {
    if let Ok(mut slot) = engine.child.lock() {
        if let Some(mut child) = slot.take() {
            let _ = child.kill();
        }
    }
}
