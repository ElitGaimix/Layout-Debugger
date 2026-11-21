"""Package the already-built development artifacts. Does not build, publish or bundle Minecraft."""

import hashlib
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parent.parent


def main():
    mod_version = re.search(r"^version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)[1]
    paper_version = re.search(
        r"^version\s*=\s*['\"]([^'\"]+)", (ROOT / "paper/build.gradle").read_text(), re.M
    )[1]
    mod_name = f"layout-debugger-{mod_version}.jar"
    plugin_name = f"layout-debugger-paper-{paper_version}.jar"
    artifacts = {
        f"client/mods/{mod_name}": ROOT / f"build/libs/{mod_name}",
        f"server/plugins/{plugin_name}": ROOT / f"paper/build/libs/{plugin_name}",
        "server/layout-debugger-agent.jar": ROOT / "paper/agent/build/libs/layout-debugger-agent.jar",
    }
    for target, source in artifacts.items():
        with zipfile.ZipFile(source) as jar:
            names = jar.namelist()
            if any("gametest" in name.lower() or name.endswith(("NativeChecks.class", "HookChecks.class")) for name in names):
                raise RuntimeError(f"Test classes present in {source.name}")
            if source.name == "layout-debugger-agent.jar":
                if "THIRD-PARTY-ASM-LICENSE.txt" not in names:
                    raise RuntimeError("Missing ASM redistribution notice")
                if any(name.startswith("org/objectweb/asm/") for name in names):
                    raise RuntimeError("Agent ASM must be isolated from the server classpath")
                if "Premain-Class: fr.eligaimix.ltdr.agent.LayoutAgent" not in jar.read("META-INF/MANIFEST.MF").decode():
                    raise RuntimeError("Missing startup entry point")
            if "paper-" in source.name and "plugin.yml" not in names:
                raise RuntimeError("Missing Paper plugin descriptor")
            for name in names:
                if name.startswith("fr/eligaimix/") and name.endswith(".class"):
                    major = int.from_bytes(jar.read(name)[6:8], "big")
                    if major > 69:
                        raise RuntimeError(f"Class requires newer than Java 25: {name}")
    contents = {target: source.read_bytes() for target, source in artifacts.items()}
    for source, target in [
        ("paper/README.md", "README-PAPER.md"),
        ("paper/VERIFICATION.md", "VERIFICATION.md"),
        ("LICENSE", "LICENSE"),
        ("paper/agent/THIRD-PARTY-ASM-LICENSE.txt", "THIRD-PARTY-ASM-LICENSE.txt"),
    ]:
        contents[target] = (ROOT / source).read_bytes()
        if target == "README-PAPER.md":
            contents[target] = contents[target].replace(b"../docs/",b"docs/")
    for source in (ROOT / "docs").glob("*.md"):
        contents["docs/"+source.name] = source.read_bytes()
    contents["server/start-paper-example.bat"] = (
        "@echo off\r\n"
        "rem Put the official Paper 26.2 build 135 JAR here; it is not distributed in this archive.\r\n"
        "cd /d \"%~dp0\"\r\n"
        "java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui\r\n"
    ).encode("ascii")
    contents["SHA256SUMS.txt"] = "".join(
        f"{hashlib.sha256(data).hexdigest()}  {target}\n" for target, data in sorted(contents.items())
    ).encode("ascii")
    output = ROOT / f"build/distributions/layout-debugger-paper-26.2-{paper_version}.zip"
    output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for target, data in contents.items():
            archive.writestr(target, data)
    print(f"Development package: {output}")
    print(f"SHA-256: {hashlib.sha256(output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
