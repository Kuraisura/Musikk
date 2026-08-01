import os
import shutil
import subprocess
import uuid
import zipfile
import io
import json
import threading
import time
import glob
import logging
import signal
import sys
from concurrent.futures import ThreadPoolExecutor
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse, JSONResponse
from pydantic import BaseModel

app = FastAPI(title="Audio Source Separation API - Preset-Aware")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler("musikk_server.log", mode="a"),
    ],
)
logger = logging.getLogger("musikk")

UPLOAD_DIR = "uploads"
OUTPUT_DIR = "separated"
STATUS_DIR = "job_status"
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(STATUS_DIR, exist_ok=True)

# Model selection: htdemucs_6s produces 6 stems (vocals, drums, bass, guitar, piano, other)
# which is the best free model available. Extra stems beyond 6 are aliased to the nearest match.
PRESET_MODEL_MAP = {
    "vocals_only": "htdemucs",
    "karaoke_2": "htdemucs",
    "standard_4": "htdemucs",
    "instrumental": "htdemucs_ft",
    "band_5": "htdemucs_ft",
    "band_plus_6": "htdemucs_ft",
    "orchestral_7": "htdemucs_ft",
    "electronic_8": "htdemucs_ft",
    "rhythm_9": "htdemucs_ft",
    "studio_10": "htdemucs_ft",
}

PRESET_STEMS = {
    "vocals_only": ["vocals"],
    "instrumental": ["drums", "bass", "guitar", "piano", "other"],
    "karaoke_2": ["vocals", "other"],
    "standard_4": ["vocals", "drums", "bass", "other"],
    "band_5": ["vocals", "drums", "bass", "guitar", "other"],
    "band_plus_6": ["vocals", "drums", "bass", "guitar", "piano", "other"],
    "orchestral_7": ["vocals", "drums", "bass", "guitar", "piano", "other", "strings"],
    "electronic_8": ["vocals", "drums", "bass", "guitar", "piano", "other", "strings", "synth"],
    "rhythm_9": ["vocals", "drums", "bass", "guitar", "piano", "other", "strings", "synth", "percussion"],
    "studio_10": ["vocals", "drums", "bass", "guitar", "piano", "other", "strings", "synth", "percussion", "background"],
}

# Extra stems (beyond the 6 Demucs can produce) are aliased to the closest real stem.
# The file is copied/symlinked so each stem key has its own download URL.
STEM_ALIAS = {
    "strings": "other",
    "synth": "other",
    "percussion": "drums",
    "background": "vocals",
}

# htdemucs_6s output filenames (lowercase, no extension)
KNOWN_STEMS = ["vocals", "drums", "bass", "guitar", "piano", "other"]


class SplitResponse(BaseModel):
    status: str
    original_filename: str
    job_id: str
    preset_id: str
    model_used: str
    stems: list[str]
    download_base_url: str


# ─── Job status helpers ──────────────────────────────────────────────

def write_status(job_id: str, data: dict):
    path = os.path.join(STATUS_DIR, f"{job_id}.json")
    with open(path, "w") as f:
        json.dump(data, f)


def read_status(job_id: str) -> dict:
    path = os.path.join(STATUS_DIR, f"{job_id}.json")
    if not os.path.isfile(path):
        return None
    with open(path) as f:
        return json.load(f)


# Thread pool for background demucs jobs (limits unbounded threads for 24/7 stability)
executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="demucs_")

# Graceful shutdown handlers
shutdown_requested = False

def handle_signal(signum, frame):
    global shutdown_requested
    logger.info(f"Received signal {signum}, initiating graceful shutdown...")
    shutdown_requested = True
    sys.exit(0)

signal.signal(signal.SIGINT, handle_signal)
signal.signal(signal.SIGTERM, handle_signal)

# Periodic cleanup every 30 minutes to prevent disk bloat during 24/7 operation
import atexit

def periodic_cleanup():
    while not shutdown_requested:
        try:
            time.sleep(1800)  # 30 min
            if shutdown_requested:
                break
            logger.info("Running periodic cleanup...")
            cut_off = time.time() - (24 * 3600 * 7)  # 7 days
            for root, dirs, files in os.walk(STATUS_DIR):
                for fname in files:
                    fpath = os.path.join(root, fname)
                    try:
                        if os.path.getmtime(fpath) < cut_off:
                            os.remove(fpath)
                    except Exception:
                        pass
        except Exception as e:
            logger.error(f"Periodic cleanup error: {e}")

threading.Thread(target=periodic_cleanup, daemon=True, name="cleanup_timer").start()


def run_demucs_background(job_id: str, file_path: str, model: str, preset_id: str, retry_on_fail: bool = True):
    write_status(job_id, {
        "status": "processing",
        "progress": 0,
        "step": "Starting Demucs separation...",
        "preset_id": preset_id,
        "model": model,
    })
    max_attempts = 2 if retry_on_fail else 1
    last_exception = None
    for attempt in range(max_attempts):
        try:
            # ... (existing code with shorter timeout) ...
            # (We keep the Popen logic from previous edit)
            process = subprocess.Popen(
                ["demucs", "-n", model, "--out", OUTPUT_DIR, file_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            start_time = time.time()
            ret = None
            while True:
                try:
                    ret = process.wait(timeout=15)
                    break
                except subprocess.TimeoutExpired:
                    elapsed = int(time.time() - start_time)
                    progress_pct = min(65, 10 + int(elapsed / 20 * 55))
                    write_status(job_id, {
                        "status": "processing",
                        "progress": progress_pct,
                        "step": f"Separating... (elapsed: {elapsed}s, attempt {attempt+1})",
                        "preset_id": preset_id,
                        "model": model,
                    })
                    if elapsed > 600:
                        logger.error(f"Demucs timeout for job {job_id} after 600s — killing process (attempt {attempt+1})")
                        process.kill()
                        process.wait()
                        raise subprocess.TimeoutExpired(cmd="demucs", timeout=300)
            stdout_data = process.stdout.read() if process.stdout else ""
            if ret != 0:
                raise subprocess.CalledProcessError(ret, "demucs", output=stdout_data)
            # Success: break out of retry loop
            break
        except Exception as exc:
            last_exception = exc
            logger.error(f"Demucs attempt {attempt+1} failed for job {job_id}: {exc}")
            if attempt < max_attempts - 1:
                # Wait briefly before retry
                time.sleep(5)
                write_status(job_id, {
                    "status": "processing",
                    "progress": 5,
                    "step": f"Retrying separation (attempt {attempt+2}/2)...",
                    "preset_id": preset_id,
                    "model": model,
                })
            else:
                write_status(job_id, {
                    "status": "error",
                    "step": f"Failed after {max_attempts} attempts: {str(exc)}",
                    "preset_id": preset_id,
                    "model": model,
                })
                return
    # If all retries exhausted, return after writing error status
    # (already handled in except block above)
    # Proceed with file organization only if we broke out via success
    if last_exception is not None and attempt == max_attempts - 1:
        return  # Already wrote error status

    # Success path: continue with organization
    # (The retry loop above already handles Popen; if we reach here, separation succeeded)

        write_status(job_id, {
            "status": "processing",
            "progress": 70,
            "step": "Separation complete, organizing stems...",
            "preset_id": preset_id,
            "model": model,
        })

        # Locate the demucs output directory
        safe_name = os.path.basename(file_path)
        demucs_out_dir = os.path.join(OUTPUT_DIR, model, safe_name)
        if not os.path.isdir(demucs_out_dir):
            demucs_out_dir = os.path.join(OUTPUT_DIR, model, os.path.splitext(safe_name)[0])

        produced_files = {}
        if os.path.isdir(demucs_out_dir):
            for fname in os.listdir(demucs_out_dir):
                stem_key = os.path.splitext(fname)[0].lower()
                produced_files[stem_key] = os.path.join(demucs_out_dir, fname)

        # Build a mapping from requested stem -> actual file path
        requested_stems = PRESET_STEMS.get(preset_id, ["vocals", "drums", "bass", "other"])
        stem_to_file = {}  # {requested_stem_name: absolute_wav_path}

        for stem in requested_stems:
            stem_lower = stem.lower()
            # Direct hit?
            if stem_lower in produced_files:
                stem_to_file[stem] = produced_files[stem_lower]
                continue
            # Aliased stem?
            alias = STEM_ALIAS.get(stem_lower)
            if alias and alias in produced_files:
                stem_to_file[stem] = produced_files[alias]
                continue
            # Fuzzy match
            for key, fpath in produced_files.items():
                if stem_lower in key or key in stem_lower:
                    stem_to_file[stem] = fpath
                    break
            # Last resort: map to "other"
            if stem not in stem_to_file and "other" in produced_files:
                stem_to_file[stem] = produced_files["other"]

        # Create the per-stem files in a flat output directory for this job
        job_out_dir = os.path.join(OUTPUT_DIR, f"job_{job_id}")
        os.makedirs(job_out_dir, exist_ok=True)

        produced_for_response = {}
        for stem_name, src_path in stem_to_file.items():
            ext = os.path.splitext(src_path)[1] or ".wav"
            dest_name = f"{stem_name}{ext}"
            dest_path = os.path.join(job_out_dir, dest_name)
            if os.path.abspath(dest_path) != os.path.abspath(src_path):
                shutil.copy2(src_path, dest_path)
            produced_for_response[stem_name] = dest_name

        write_status(job_id, {
            "status": "completed",
            "progress": 100,
            "step": "Done",
            "preset_id": preset_id,
            "model": model,
            "stems": list(stem_to_file.keys()),
            "produced_files": produced_for_response,
        })


# ─── Endpoints ───────────────────────────────────────────────────────


@app.get("/health")
async def health():
    demucs_ok = shutil.which("demucs") is not None
    return {"status": "ok", "demucs_available": demucs_ok}


@app.get("/presets")
async def list_presets():
    return {
        pid: {
            "stems": PRESET_STEMS.get(pid, ["vocals"]),
            "model": PRESET_MODEL_MAP.get(pid, "htdemucs"),
        }
        for pid in PRESET_MODEL_MAP
    }


@app.post("/split", response_model=SplitResponse)
async def split_audio(
    file: UploadFile = File(...),
    preset_id: str = Form(default="standard_4"),
):
    job_id = str(uuid.uuid4())
    ext = os.path.splitext(file.filename)[1] or ".wav"
    safe_name = f"{job_id}{ext}"
    file_path = os.path.join(UPLOAD_DIR, safe_name)

    model = PRESET_MODEL_MAP.get(preset_id, "htdemucs")
    requested_stems = PRESET_STEMS.get(preset_id, ["vocals", "drums", "bass", "other"])

    try:
        with open(file_path, "wb") as buffer:
            # Stream upload to disk in 256KB chunks (faster, lower memory for large audio)
            chunk_size = 256 * 1024
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                buffer.write(chunk)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save file: {e}")

    future = executor.submit(run_demucs_background, job_id, file_path, model, preset_id)

    return SplitResponse(
        status="processing",
        original_filename=file.filename,
        job_id=job_id,
        preset_id=preset_id,
        model_used=model,
        stems=requested_stems,
        download_base_url=f"http://192.168.1.2:8000/download/{job_id}",
    )


@app.get("/progress/{job_id}")
async def get_progress(job_id: str):
    status = read_status(job_id)
    if status is None:
        return JSONResponse(content={
            "status": "unknown",
            "progress": 0,
            "step": "Job not found",
        })
    return JSONResponse(content=status)


@app.get("/download/{job_id}/{stem_filename}")
async def download_stem(job_id: str, stem_filename: str):
    def serve_file(path: str):
        media_type = None
        if stem_filename.endswith(".wav"):
            media_type = "audio/wav"
        elif stem_filename.endswith(".mp3"):
            media_type = "audio/mpeg"
        elif stem_filename.endswith(".flac"):
            media_type = "audio/flac"
        elif stem_filename.endswith(".m4a"):
            media_type = "audio/mp4"
        elif stem_filename.endswith(".ogg"):
            media_type = "audio/ogg"
        return FileResponse(path, media_type=media_type)

    # Try flat job output dir first
    job_dir = os.path.join(OUTPUT_DIR, f"job_{job_id}")
    if os.path.isdir(job_dir):
        target = os.path.join(job_dir, stem_filename)
        if os.path.isfile(target):
            return serve_file(target)
        # Also try matching by stem key prefix
        for fname in os.listdir(job_dir):
            stem_key = os.path.splitext(fname)[0].lower()
            requested = os.path.splitext(stem_filename)[0].lower()
            if stem_key == requested:
                return serve_file(os.path.join(job_dir, fname))

    # Fallback: search model output dirs
    for model_dir in ["htdemucs", "htdemucs_ft"]:
        pattern = os.path.join(OUTPUT_DIR, model_dir, f"{job_id}.*", stem_filename)
        for match in glob.glob(pattern):
            if os.path.isfile(match):
                return serve_file(match)
        out_dirs = glob.glob(os.path.join(OUTPUT_DIR, model_dir, f"{job_id}.*"))
        for out_dir in out_dirs:
            if not os.path.isdir(out_dir):
                continue
            for fname in os.listdir(out_dir):
                if stem_filename.lower() in fname.lower():
                    return serve_file(os.path.join(out_dir, fname))

    raise HTTPException(status_code=404, detail=f"Stem '{stem_filename}' not found for job '{job_id}'")


@app.get(
    "/download-zip/{job_id}/{preset_id}",
    responses={200: {"content": {"application/zip": {}}, "description": "ZIP of stems"}},
)
async def download_stems_zip(job_id: str, preset_id: str):
    requested = PRESET_STEMS.get(preset_id, ["vocals", "drums", "bass", "other"])
    buf = io.BytesIO()
    found_any = False
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        # Try flat job dir first
        job_dir = os.path.join(OUTPUT_DIR, f"job_{job_id}")
        if os.path.isdir(job_dir):
            for stem in requested:
                for fname in os.listdir(job_dir):
                    if stem.lower() in fname.lower():
                        zf.write(os.path.join(job_dir, fname), arcname=f"{stem}.wav")
                        found_any = True
                        break
        else:
            for model_dir in ["htdemucs", "htdemucs_ft"]:
                dirs = glob.glob(os.path.join(OUTPUT_DIR, model_dir, f"{job_id}.*"))
                if not dirs:
                    continue
                out_dir = dirs[0]
                for stem in requested:
                    mapped = STEM_ALIAS.get(stem.lower(), stem.lower())
                    for fname in os.listdir(out_dir):
                        if mapped in fname.lower():
                            zf.write(os.path.join(out_dir, fname), arcname=f"{stem}.wav")
                            found_any = True
                            break
    if not found_any:
        raise HTTPException(status_code=404, detail="No stems found for this job")
    buf.seek(0)
    return StreamingResponse(
        buf,
        media_type="application/zip",
        headers={"Content-Disposition": f"attachment; filename={job_id}_{preset_id}.zip"},
    )


@app.delete("/cleanup/{job_id}")
async def cleanup(job_id: str):
    for model_dir in ["htdemucs", "htdemucs_ft", "htdemucs_6s"]:
        for d in glob.glob(os.path.join(OUTPUT_DIR, model_dir, f"{job_id}.*")):
            shutil.rmtree(d, ignore_errors=True)
    job_dir = os.path.join(OUTPUT_DIR, f"job_{job_id}")
    if os.path.isdir(job_dir):
        shutil.rmtree(job_dir, ignore_errors=True)
    for f in glob.glob(os.path.join(UPLOAD_DIR, f"{job_id}*")):
        os.remove(f)
    status_path = os.path.join(STATUS_DIR, f"{job_id}.json")
    if os.path.isfile(status_path):
        os.remove(status_path)
    return {"status": "cleaned"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        timeout_keep_alive=30,
        access_log=True,
    )