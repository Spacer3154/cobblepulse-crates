#!/usr/bin/env python3
"""CI : test de bout en bout. Demarre le serveur (run-server, prepare par smoke.py) puis un vrai client
(run-prod-client, prepare par client.py) qui s'y connecte : clic sur une vraie caisse, ouverture, hologramme,
menu Voeu ADN et editeur avec de vraies donnees. Captures dans out/live."""
import glob, os, queue, shutil, subprocess, sys, threading, time

OUT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "out")
DST = os.path.join(OUT, "live")
SRV = os.path.abspath("run-server")
MC = os.path.abspath("run-prod-client")
os.makedirs(DST, exist_ok=True)
props = dict(l.strip().split("=", 1) for l in open("gradle.properties") if "=" in l and not l.startswith("#"))
steps = open(os.path.join(DST, "steps.txt"), "w")


def log(msg):
    print(msg, flush=True)
    steps.write(msg + "\n")
    steps.flush()


if not os.path.exists(os.path.join(SRV, "server.jar")) or not os.path.isdir(os.path.join(MC, "mods")):
    log("prerequis absents (smoke.py / client.py)")
    sys.exit(0)

with open(os.path.join(SRV, "server.properties"), "a") as fh:
    fh.write("enforce-secure-profile=false\n")
for f in glob.glob(os.path.join(MC, "screenshots", "*.png")):
    os.remove(f)

srv = subprocess.Popen(["java", "-Xmx4G", "-Dcobblepulse_crates.selftest=true", "-jar", "server.jar", "nogui"], cwd=SRV,
                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
slog = open(os.path.join(DST, "server.log"), "w")


def reader():
    for line in srv.stdout:
        slog.write(line)
        slog.flush()
        lines.put(line)
    lines.put(None)


threading.Thread(target=reader, daemon=True).start()


def wait_for(token, timeout):
    end = time.time() + timeout
    while time.time() < end:
        try:
            line = lines.get(timeout=1)
        except queue.Empty:
            continue
        if line is None:
            return False
        if token in line:
            return True
    return False


def cmd(c):
    log("> " + c)
    srv.stdin.write(c + "\n")
    srv.stdin.flush()
    time.sleep(1)


ok = wait_for("Done (", 900)
log("serveur pret : %s" % ok)
if ok:
    for c in ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set 6000", "weather clear",
              "lootbox selftest live"]:
        cmd(c)
    version = "fabric:1.21.1:" + props.get("loader_version", "0.19.5")
    base = [sys.executable, "-m", "portablemc", "--main-dir", MC, "--work-dir", MC]
    java = shutil.which("java") or "java"
    env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1", MESA_GL_VERSION_OVERRIDE="4.5")
    clog = open(os.path.join(DST, "client.log"), "w")
    cl = ["xvfb-run", "-n", "98", "-s", "-screen 0 1280x720x24"] + base + ["start", "-u", "CratesTest", "--resolution", "1280x720", "--jvm", java,
          "--jvm-args=-Xmx3G -Dcobblepulse_crates.livetest=true", "-s", "127.0.0.1", "-p", "25565", version]
    log("$ " + " ".join(cl))
    client = subprocess.Popen(cl, stdout=clog, stderr=subprocess.STDOUT, env=env)
    t0 = time.time()
    while client.poll() is None and time.time() - t0 < 1100:
        time.sleep(10)
    if client.poll() is None:
        log("!! client : delai depasse")
        client.kill()
        subprocess.run("pkill -f net.fabricmc.loader.impl.launch.knot.KnotClient", shell=True)
    log("client termine (%.0f s)" % (time.time() - t0))
    clog.close()
    cmd("lootbox selftest report")
    time.sleep(3)
try:
    srv.stdin.write("stop\n")
    srv.stdin.flush()
    srv.wait(timeout=180)
except Exception:
    srv.kill()
slog.close()
for f in glob.glob(os.path.join(MC, "screenshots", "*.png")):
    shutil.copy(f, DST)
for f in glob.glob(os.path.join(MC, "logs", "latest.log")):
    shutil.copy(f, os.path.join(DST, "client-latest.log"))
for f in glob.glob(os.path.join(MC, "crash-reports", "*")) + glob.glob(os.path.join(SRV, "crash-reports", "*")):
    shutil.copy(f, DST)
log("captures : %s" % sorted(os.listdir(DST)))
