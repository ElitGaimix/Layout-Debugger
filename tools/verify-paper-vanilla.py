"""Opt-in local integration probe: launch the official client without Fabric or mods.

The Fabric client test writes readiness/completion markers under build/. This
helper joins only the disposable server on 127.0.0.1:25577 and closes its own JVM
after the multiplayer assertions. It never reads launcher accounts or tokens.
"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
READY = BUILD / "paper-client-ready-for-vanilla.txt"
FINISHED = BUILD / "paper-client-vanilla-finished.txt"


def library_paths(metadata):
    caches = [
        Path.home() / ".gradle/caches/modules-2/files-2.1",
        ROOT / ".gradle/local-home/caches/modules-2/files-2.1",
        ROOT / ".gradle/paper-home/caches/modules-2/files-2.1",
    ]
    result = []
    for library in metadata["libraries"]:
        rules = library.get("rules", [])
        allowed = not rules
        for rule in rules:
            platform = rule.get("os", {})
            if platform.get("name", "windows") == "windows" and platform.get(
                "arch", "amd64"
            ) in {"x86_64", "amd64"}:
                allowed = rule["action"] == "allow"
        if not allowed:
            continue
        artifact = library.get("downloads", {}).get("artifact")
        if not artifact:
            continue
        group, module, version, *_ = library["name"].split(":")
        filename = artifact["path"].rsplit("/", 1)[-1]
        candidates = [
            candidate
            for cache in caches
            for candidate in (cache / group / module / version).rglob(filename)
        ]
        if not candidates:
            raise RuntimeError(f"Run the Fabric development setup first: missing {filename}")
        jar = candidates[0].resolve()
        if hashlib.sha1(jar.read_bytes()).hexdigest() != artifact["sha1"]:
            raise RuntimeError(f"Cached vanilla library checksum mismatch: {filename}")
        result.append(jar)
    return result


def main():
    if os.name != "nt":
        raise RuntimeError("This helper selects the Windows x64 vanilla libraries")
    settings = dict(
        line.split("=", 1)
        for line in (BUILD / "paper-test/server.properties").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    if settings.get("server-ip") != "127.0.0.1" or settings.get("server-port") != "25577":
        raise RuntimeError("The development server must be explicitly bound to loopback:25577")
    cache = ROOT / ".gradle/local-home/caches/fabric-loom"
    metadata = json.loads((cache / "26.2/mojang_minecraft_info.json").read_text())
    client = cache / "26.2/minecraft-client.jar"
    if hashlib.sha1(client.read_bytes()).hexdigest() != metadata["downloads"]["client"]["sha1"]:
        raise RuntimeError("Client JAR differs from the official Mojang checksum")
    libraries = library_paths(metadata)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java.exe") if java_home else shutil.which("java")
    if not java:
        raise RuntimeError("JAVA_HOME or java on PATH is required")
    game = BUILD / "vanilla-probe"
    game.mkdir(exist_ok=True)
    (game / "options.txt").write_text(
        "renderDistance:3\nsimulationDistance:3\nmaxFps:30\nenableVsync:false\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n"
        "onboardAccessibility:false\ntutorialStep:none\n"
    )
    offline_id = uuid.UUID(bytes=hashlib.md5(b"OfflinePlayer:VanillaProbe").digest(), version=3)
    classpath = ";".join(str(path.resolve()) for path in [client, *libraries])
    arguments = [
        java, "-Xmx1G", "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
        metadata["mainClass"], "--username", "VanillaProbe", "--uuid", str(offline_id),
        "--accessToken", "0", "--version", "26.2", "--versionType", "release",
        "--gameDir", str(game), "--assetsDir", str(cache / "assets"),
        "--assetIndex", metadata["assetIndex"]["id"], "--width", "640", "--height", "360",
        "--quickPlayMultiplayer", "127.0.0.1:25577",
    ]
    deadline = time.monotonic() + 240
    print("Waiting for the Fabric test to capture the layout...", flush=True)
    while not READY.exists():
        if time.monotonic() > deadline:
            raise TimeoutError("No capture readiness marker; no vanilla JVM was started")
        time.sleep(0.2)
    with (game / "console.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(arguments, cwd=game, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        print(f"Official vanilla client started (PID {process.pid}); no Fabric or mod classpath.", flush=True)
        try:
            while not FINISHED.exists():
                if process.poll() is not None:
                    raise RuntimeError(f"Vanilla client exited early: {process.returncode}")
                if time.monotonic() > deadline:
                    raise TimeoutError("Multiplayer assertions did not complete; inspect both client logs")
                time.sleep(0.2)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=15)
        print("Vanilla probe closed; Fabric test can verify solo capabilities return.", flush=True)


if __name__ == "__main__":
    main()
