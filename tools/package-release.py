"""Assemble publication files from verified builds; never include server worlds or player data."""
import hashlib
import json
from pathlib import Path
import re
import zipfile
import importlib.util
spec=importlib.util.spec_from_file_location("paper_package",Path(__file__).with_name("package-paper.py"))
paper=importlib.util.module_from_spec(spec)
spec.loader.exec_module(paper)
ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/"out"
def write(relative,data):
    target=OUT/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
def copy(source,target):write(target,(ROOT/source).read_bytes())
def archive(name,paths):
    target=OUT/"archives"/name;target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as z:
        for path in sorted(paths):
            if path.is_file():z.write(path,path.relative_to(OUT).as_posix())
def main():
    # Performs class-version, license and test-exclusion checks before copying any release JAR.
    paper.main()
    mod_version=re.search(r"^version=(.+)$",(ROOT/"gradle.properties").read_text(),re.M)[1]
    plugin_version=re.search(r"^version = '([^']+)'",(ROOT/"paper/build.gradle").read_text(),re.M)[1]
    mod=f"layout-debugger-{mod_version}.jar"
    plugin=f"layout-debugger-paper-{plugin_version}.jar"
    copy(f"build/libs/{mod}",f"client/mods/{mod}")
    copy(f"build/libs/{mod}",f"serveur-fabric/mods/{mod}")
    copy(f"paper/build/libs/{plugin}",f"serveur-paper/plugins/{plugin}")
    copy("paper/agent/build/libs/layout-debugger-agent.jar","serveur-paper/layout-debugger-agent.jar")
    write("serveur-paper/start-paper-example.bat",b'@echo off\r\ncd /d "%~dp0"\r\njava -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui\r\n')
    write("serveur-paper/start-paper-example.sh",b'#!/bin/sh\nset -eu\ncd "$(dirname "$0")"\nexec java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui\n')
    for p in (ROOT/"docs").glob("*.md"):copy(p.relative_to(ROOT),"documentation/"+p.name)
    copy("paper/VERIFICATION.md","documentation/VERIFICATION.md")
    copy("docs/USAGE.md","documentation/GUIDE-FABRIC.md")
    copy("paper/README.md","documentation/GUIDE-PAPER.md")
    # Normalize links for the flattened documentation folder.
    for p in (OUT/"documentation").glob("*.md"):
        p.write_text(p.read_text(encoding="utf-8").replace("../paper/README.md","GUIDE-PAPER.md").replace("../paper/VERIFICATION.md","VERIFICATION.md").replace("../docs/","").replace("docs/","").replace("paper/README.md","GUIDE-PAPER.md").replace("paper/VERIFICATION.md","VERIFICATION.md"),encoding="utf-8")
    copy("LICENSE","LICENSE")
    copy("paper/agent/THIRD-PARTY-ASM-LICENSE.txt","THIRD-PARTY-ASM-LICENSE.txt")
    write("README-FIRST.md",("# Layout Debugger — Minecraft 26.2\n\n"
        "Start with the [installation guide](documentation/INSTALLATION.md): client first, then server.\n\n"
        "- `client/mods/`: Fabric client mod for single-player or an equipped server; Fabric API is required.\n"
        "- `serveur-fabric/mods/`: the same mod for a Fabric server.\n"
        "- `serveur-paper/`: Paper plugin, required startup Java agent and example launch scripts. Targets Paper 26.2 build 135 only.\n"
        "- `documentation/`: installation, usage, migration and verification guides.\n"
        "- `sources/`: project sources without worlds, player data or caches.\n\n"
        "Java 25 or newer is required. Minecraft, Paper, Fabric Loader and Fabric API are not bundled. "
        "This distribution is an alpha. Back up your worlds. Update the client mod, Paper plugin and startup agent together.\n").encode("utf-8"))
    write("LIRE-EN-PREMIER.md",("# Layout Debugger — distribution alpha 26.2\n\n"
        "[Installation selon votre usage](documentation/INSTALLATION.md) · "
        "[Animations privées / export / migration](documentation/OWNERSHIP-EXPORT.md) · "
        "[Vérifications et limites](documentation/VERIFICATION.md) · "
        "[Texte de publication](documentation/RELEASE-NOTES.md)\n\n"
        "- `client/` : solo ou joueur d’un serveur équipé ; Fabric et Fabric API requis.\n"
        "- `serveur-fabric/` : mod pour serveur Fabric.\n"
        "- `serveur-paper/` : plugin + agent de démarrage, Paper 26.2 build 135 uniquement.\n"
        "- `archives/` : ZIP séparés et bundle complet prêts à joindre.\n"
        "- `sources/` : sources du projet sans cache, monde ni donnée de joueur.\n\n"
        "Minecraft, Paper, Fabric Loader et Fabric API ne sont pas inclus. Mettre à jour "
        "client, plugin et agent ensemble (protocole 3). Les anciens documents sans créateur "
        "doivent être attribués via `ld admin assign`. Aucune publication en ligne effectuée.\n").encode("utf-8"))
    metadata={"minecraft":"26.2","javaMinimum":25,"testedJava":27,"fabricLoader":"0.20.1","fabricApi":"0.161.0+26.2","paperBuild":135,"paperCommit":"e4d6dfd","modVersion":mod_version,"pluginVersion":plugin_version,"protocolVersion":3,"storageSchema":4,"exportFormatVersion":1,"includesWorldBlocks":False,"status":"alpha","builtForPublicationOn":"2026-11-21"}
    write("manifest.json",(json.dumps(metadata,ensure_ascii=False,indent=2)+"\n").encode("utf-8"))
    # Explicit allowlist: no accidental inclusion of .gradle/, run/, worlds, exports or logs.
    source_roots=["src","paper/src","paper/agent/src","gradle/wrapper","docs",".github"]
    source_files=["tools/package-release.py","tools/package-paper.py","tools/verify-paper-vanilla.py","tools/bootstrap-paper.ps1","tools/paper-bootstrap/build.gradle","tools/paper-bootstrap/settings.gradle","build.gradle","settings.gradle","gradle.properties","gradlew","gradlew.bat","LICENSE","README.md","paper/build.gradle","paper/settings.gradle","paper/README.md","paper/VERIFICATION.md","paper/agent/build.gradle","paper/agent/settings.gradle","paper/agent/THIRD-PARTY-ASM-LICENSE.txt"]
    source_files += ["README.fr.md", ".gitignore", ".gitattributes"]
    source_paths=[ROOT/p for p in source_files if (ROOT/p).is_file()]
    for root in source_roots:
        source_paths.extend(p for p in (ROOT/root).rglob("*") if p.is_file() and "__pycache__" not in p.parts and p.suffix!=".pyc")
    source_zip=OUT/"sources/layout-debugger-sources.zip";source_zip.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(source_zip,"w",zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(source_paths)):z.write(p,p.relative_to(ROOT).as_posix())
    common=[p for folder in ["documentation"] for p in (OUT/folder).rglob("*") if p.is_file()]+[OUT/n for n in ["LICENSE","THIRD-PARTY-ASM-LICENSE.txt","README-FIRST.md","LIRE-EN-PREMIER.md","manifest.json"]]
    for kind in ["client","serveur-fabric","serveur-paper"]:archive(f"layout-debugger-26.2-{kind}.zip",common+list((OUT/kind).rglob("*")))
    archive("layout-debugger-26.2-complet.zip",common+[p for kind in ["client","serveur-fabric","serveur-paper","sources"] for p in (OUT/kind).rglob("*")])
    paths=sorted(p for p in OUT.rglob("*") if p.is_file() and p.name!="SHA256SUMS.txt")
    write("SHA256SUMS.txt","".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(OUT).as_posix()}\n" for p in paths).encode("ascii"))
    print(f"Publication folder: {OUT} ({len(paths)} files + checksums)")
if __name__=="__main__":main()
