#!/usr/bin/env python3
"""Local Carpet/RCON smoke test. Uses only Python's standard library."""
import hashlib
import os
from pathlib import Path
import re
import socket
import signal
import struct
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'run'
PASSWORD = 'mobarena-local-smoke'
CARPET = 'fabric-carpet-26.3+v260915.jar'
URL = 'https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar'
SHA512 = '7a479ba69eb5049cb94365d5044bd58c9f970c50b3484861fe3675f1917f7c99c1b1f50a5e01bdd9ded940d76a995539009a3656e05be2f513f20ec14de991a7'
TIMES = dict(prepTimeSeconds=60, waveConfigTimeSeconds=30, waveCountdownSeconds=3,
             waveCooldownSeconds=3, waveMaxTimeSeconds=60, suddenDeathMaxTimeSeconds=60,
             arenaRespawnSeconds=3, mobGlowAfterSeconds=10, endScreenSeconds=5, maxAliveMobs=20)


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection(('127.0.0.1', 25575), timeout=120)
        self.request = 0
        self.send(3, PASSWORD)
        if self.receive()[0] == -1:
            raise RuntimeError('RCON authentication failed')

    def send(self, kind, text):
        self.request += 1
        payload = struct.pack('<ii', self.request, kind) + text.encode() + b'\0\0'
        self.sock.sendall(struct.pack('<i', len(payload)) + payload)

    def exact(self, size):
        data = b''
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                raise EOFError('RCON connection closed')
            data += chunk
        return data

    def receive(self):
        size, = struct.unpack('<i', self.exact(4))
        if not 10 <= size <= 4 * 1024 * 1024:
            raise RuntimeError(f'Invalid RCON packet size: {size}')
        packet = self.exact(size)
        return struct.unpack('<ii', packet[:8])[0], packet[8:-2].decode()

    def command(self, command, expected=None):
        self.send(2, command)
        request, reply = self.receive()
        if request != self.request:
            raise RuntimeError('Unexpected RCON response identifier')
        # Minecraft splits command output into 4096-byte response packets.
        if len(reply.encode()) >= 4096:
            self.sock.settimeout(0.2)
            try:
                while True:
                    request, part = self.receive()
                    if request != self.request:
                        raise RuntimeError('Unexpected RCON response identifier')
                    reply += part
            except socket.timeout:
                pass
            finally:
                self.sock.settimeout(120)
        print(f'> {command}\n{reply}', flush=True)
        if expected is not None and expected not in reply:
            raise AssertionError(f'Expected {expected!r}: {reply}')
        return reply


def wait_log(path, pattern, process, timeout=180, offset=0):
    deadline = float('inf') if timeout is None else time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = path.read_text(errors='replace') if path.exists() else ''
        if re.search(pattern, text[offset:], re.IGNORECASE):
            return
        if process.poll() is not None:
            raise RuntimeError(f'Server exited: {process.returncode}; see {path}')
        time.sleep(0.2)
    raise TimeoutError(f'Log line missing: {pattern}; see {path}')


def setup():
    mods = RUN / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    jar = mods / CARPET
    if not jar.exists():
        with urllib.request.urlopen(URL, timeout=60) as response:
            data = response.read()
        if hashlib.sha512(data).hexdigest() != SHA512:
            raise RuntimeError('Carpet checksum mismatch')
        jar.write_bytes(data)
    if hashlib.sha512(jar.read_bytes()).hexdigest() != SHA512:
        raise RuntimeError('Carpet checksum mismatch')
    props = RUN / 'server.properties'
    current = props.read_text() if props.exists() else ''
    settings = {'online-mode': 'false', 'enable-rcon': 'true', 'rcon.password': PASSWORD,
                'rcon.port': '25575', 'server-ip': '127.0.0.1', 'broadcast-rcon-to-ops': 'true',
                'view-distance': '3', 'simulation-distance': '3'}
    lines = [line for line in current.splitlines() if line.split('=', 1)[0] not in settings]
    props.write_text('\n'.join(lines + [f'{k}={v}' for k, v in settings.items()]) + '\n')
    (RUN / 'eula.txt').write_text('eula=true\n')


def check_startup_cleanup():
    parent = RUN / 'world/dimensions/mobarena'
    leftovers = list(parent.iterdir()) if parent.exists() else []
    if leftovers:
        raise AssertionError(f'Mob Arena level folders remain after startup: {leftovers}')
    print('PASS: No Mob Arena level folders after startup.', flush=True)


def run_scenario(rcon, log, process, *, spike=False, portal_only=False):
    cmd = rcon.command
    for key, value in TIMES.items():
        cmd(f'ma config set {key} {value}', 'Config saved')
    cmd('player red1 spawn')
    wait_log(log, r'red1 joined the game', process, timeout=90)
    cmd('player blue1 spawn')
    wait_log(log, r'blue1 joined the game', process, timeout=90)
    cmd('ma team assign red1 red', 'Player assigned to RED')
    cmd('ma team assign blue1 blue', 'Player assigned to BLUE')
    cmd('ma start', 'Game started')
    offset = len(log.read_text())
    cmd('ma status', 'Phase: PREP')
    process.stdin.write(b'ma status\n')
    process.stdin.flush()
    wait_log(log, r'Phase: PREP', process, offset=offset)
    if spike:
        cmd('carpet list respawn')
        cmd('carpet list death')
        cmd('gamemode creative red1')
        cmd('data get entity red1 playerGameType', 'entity data: 1')
        cmd('data get entity red1 Dimension', 'mobarena:red_overworld_')
        cmd('execute at red1 run fill ~-1 199 ~ ~2 203 ~ obsidian')
        cmd('execute at red1 run fill ~ 200 ~ ~1 202 ~ nether_portal[axis=x]')
        cmd('execute at red1 run tp red1 ~ 200 ~')
        deadline = time.monotonic() + 45
        while True:
            dimension = cmd('data get entity red1 Dimension')
            if 'mobarena:red_nether_' in dimension:
                break
            if time.monotonic() > deadline:
                raise AssertionError('Fake player did not enter the RED Nether')
            time.sleep(0.5)
        if not portal_only:
            cmd('gamemode survival red1')
            time.sleep(4)
            death_offset = len(log.read_text())
            cmd('damage red1 100 minecraft:generic_kill', 'Applied')
            wait_log(log, r'red1 left the game', process, offset=death_offset)
            cmd('player red1 spawn')
            wait_log(log, r'red1 joined the game', process, timeout=90, offset=death_offset)
            cmd('data get entity red1 Health', '20.0f')
        print('PASS: RED Nether portal' + ('.' if portal_only else ', Carpet death, and rejoin.'))
    cmd('ma stop', 'Game stopped')
    cmd('ma status', 'Phase: LOBBY')


def run(*, game=True, spike=False, portal_only=False, cleanup_delay=0, log_name='smoke-console.log'):
    # Refuse an existing server before changing its local files.
    for port in (25565, 25575):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            probe.bind(('127.0.0.1', port))
    setup()
    env = os.environ.copy()
    log = RUN / log_name
    rcon = None
    print(f'Waiting for a shared Gradle build slot. Server log: {log.relative_to(ROOT)}', flush=True)
    with log.open('w') as output:
        # tools/gradle.sh sets Java 25 and waits for one of the shared build slots.
        process = subprocess.Popen([str(ROOT / 'tools/gradle.sh'), '--console=plain', 'runServer'], cwd=ROOT,
                                   env=env, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            # Wait without a limit only for the build slot; a hung startup must still fail.
            wait_log(log, r'Starting minecraft server', process, timeout=None)
            wait_log(log, r'RCON running on', process, timeout=180)
            rcon = Rcon()
            check_startup_cleanup()
            if game:
                run_scenario(rcon, log, process, spike=spike, portal_only=portal_only)
                if cleanup_delay:
                    time.sleep(cleanup_delay)
        finally:
            if process.poll() is None:
                if process.stdin:
                    process.stdin.write(b'stop\n')
                    process.stdin.flush()
                try:
                    process.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
            if rcon:
                rcon.sock.close()
    for server_log in (log, RUN / 'logs/latest.log'):
        text = server_log.read_text(errors='replace') if server_log.exists() else ''
        if re.search(r'Exception|\bERROR\b|Caused by:', text):
            raise AssertionError(f'Server log contains an exception or error: {server_log.relative_to(ROOT)}')
    if process.returncode:
        raise RuntimeError(f'Server exit status: {process.returncode}')
    print('PASS: Server scenario and clean server log.')


if __name__ == '__main__':
    run(spike='--spike' in sys.argv)
