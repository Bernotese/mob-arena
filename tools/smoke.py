#!/usr/bin/env python3
"""Local Carpet/RCON smoke test. Uses only Python's standard library."""
import argparse
import hashlib
import json
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

    def command(self, command, expected=None, quiet=False):
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
        if not quiet:
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
    offset = len(log.read_text())
    cmd('ma start', 'Preparing the game worlds')
    # The worlds load over several ticks; PREP starts when they are ready.
    wait_log(log, r'Game started', process, timeout=90, offset=offset)
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


# ---- T10e scenarios with /ma debug (needs -Dmobarena.debug=true, set in the runServer config) ----

def state(rcon):
    """Current game state from /ma debug state (JSON after the MOBARENA_STATE marker)."""
    reply = rcon.command('ma debug state', 'MOBARENA_STATE', quiet=True)
    return json.JSONDecoder().raw_decode(reply[reply.index('MOBARENA_STATE ') + len('MOBARENA_STATE '):])[0]


def wait_state(rcon, check, what, timeout=120, seen=None):
    """Poll the state until check(state) is true. Records each new phase in seen."""
    deadline = time.monotonic() + timeout
    while True:
        current = state(rcon)
        if seen is not None and (not seen or seen[-1] != current['phase']):
            seen.append(current['phase'])
            print(f'PHASE: {current["phase"]}', flush=True)
        if check(current):
            print(f'PASS: {what}', flush=True)
            return current
        if time.monotonic() > deadline:
            raise AssertionError(f'Timeout: {what}; state: {json.dumps(current)}')
        time.sleep(0.5)


def battle(current, team):
    return current['battles'][team] or {}


def wave_of(current, team):
    tag = battle(current, team).get('tag') or ''
    return int(tag.rsplit('.', 1)[1]) if tag else 0


def tagged_count(rcon, tag):
    """Live entities with a scoreboard tag in all levels (independent of the mod's own count)."""
    reply = rcon.command(f'execute if entity @e[tag={tag}]', quiet=True)
    match = re.search(r'count: (\d+)', reply)
    return int(match.group(1)) if match else 0


def mob_arena_folders():
    parent = RUN / 'world/dimensions/mobarena'
    return list(parent.iterdir()) if parent.exists() else []


def start_game(rcon, log, process, *, max_alive=None):
    cmd = rcon.command
    for key, value in TIMES.items():
        cmd(f'ma config set {key} {max_alive if key == "maxAliveMobs" and max_alive else value}', 'Config saved')
    for name in ('red1', 'blue1'):
        offset = len(log.read_text())
        cmd(f'player {name} spawn')
        wait_log(log, rf'{name} joined the game', process, timeout=90, offset=offset)
    cmd('ma team assign red1 red', 'Player assigned to RED')
    cmd('ma team assign blue1 blue', 'Player assigned to BLUE')
    offset = len(log.read_text())
    cmd('ma start', 'Preparing the game worlds')
    # The worlds load over several ticks; PREP starts when they are ready.
    wait_log(log, r'Game started', process, timeout=90, offset=offset)


def to_arena(rcon, red_waves, blue_waves, seen):
    """PREP kills by debug kill, waves by debug waves, then ARENA. *_waves: three WaveSpec strings."""
    cmd = rcon.command
    wait_state(rcon, lambda s: s['phase'] == 'PREP', 'PREP', seen=seen)
    for player, waves in (('red1', red_waves), ('blue1', blue_waves)):
        needed = {}
        for spec in waves:
            for entry in ([] if spec == '-' else spec.split(',')):
                entity, count = entry.rsplit(':', 1)
                needed[entity] = needed.get(entity, 0) + int(count)
        for entity, count in needed.items():
            cmd(f'ma debug kill {player} minecraft:{entity} {count}', 'Debug action complete')
    current = state(rcon)
    for team, waves in (('RED', red_waves), ('BLUE', blue_waves)):
        total = sum(int(e.rsplit(':', 1)[1]) for spec in waves if spec != '-' for e in spec.split(','))
        if current['pools'][team] < total:
            raise AssertionError(f'PREP kills did not reach the {team} pool: {current["pools"]}')
    print('PASS: PREP kills fill both pools.', flush=True)
    cmd('ma debug timer 0', 'Debug action complete')
    wait_state(rcon, lambda s: s['phase'] == 'WAVE_CONFIG', 'WAVE_CONFIG', seen=seen)
    cmd('ma debug waves red ' + ' '.join(red_waves), 'Debug action complete')
    cmd('ma debug waves blue ' + ' '.join(blue_waves), 'Debug action complete')
    cmd('ma debug timer 0', 'Debug action complete')
    current = wait_state(rcon, lambda s: s['phase'] == 'ARENA', 'ARENA', seen=seen)
    # Resistance V: Carpet players do not fight back, and a down player would end the scenario early.
    cmd('effect give red1 minecraft:resistance infinite 4 true')
    cmd('effect give blue1 minecraft:resistance infinite 4 true')
    return current


def scenario_s1(rcon, log, process):
    """T10 S1: full game. RED clears all 3 waves, BLUE is down in wave 2, RED wins, END -> LOBBY, levels gone."""
    seen = []
    start_game(rcon, log, process)
    # RED fights the BLUE plan and BLUE fights the RED plan.
    to_arena(rcon, ['zombie:2', 'zombie:1,cow:1', 'zombie:1'], ['zombie:1', 'zombie:2', 'zombie:1'], seen)
    for wave in (1, 2, 3):
        wait_state(rcon, lambda s, w=wave: wave_of(s, 'RED') == w and battle(s, 'RED').get('state') == 'FIGHT'
                   and battle(s, 'RED').get('alive', 0) > 0, f'RED wave {wave} fight', seen=seen)
        if wave == 1:
            wait_state(rcon, lambda s: battle(s, 'BLUE').get('state') == 'FIGHT', 'BLUE wave 1 fight', seen=seen)
            rcon.command('ma debug clearwave blue', 'Debug action complete')
        if wave == 2:
            wait_state(rcon, lambda s: wave_of(s, 'BLUE') == 2 and battle(s, 'BLUE').get('state') == 'FIGHT',
                       'BLUE wave 2 fight', seen=seen)
            rcon.command('effect clear blue1')
            rcon.command('ma debug down blue1', 'Debug action complete')
            wait_state(rcon, lambda s: battle(s, 'BLUE').get('state') == 'ELIMINATED', 'BLUE eliminated in wave 2', seen=seen)
        rcon.command('ma debug clearwave red', 'Debug action complete')
    current = wait_state(rcon, lambda s: s['phase'] != 'ARENA', 'Game result', seen=seen)
    verdict = current['verdict']
    if not verdict or (verdict['type'], verdict['winner'], verdict['reason']) != ('WIN', 'RED', 'All waves cleared'):
        raise AssertionError(f'Expected RED to win by all waves cleared: phase {current["phase"]}, verdict {verdict}')
    print('PASS: RED wins (All waves cleared).', flush=True)
    # The game end can block the server for some seconds (known issue), so wait long.
    current = wait_state(rcon, lambda s: s['phase'] == 'LOBBY', 'END -> LOBBY', timeout=180, seen=seen)
    expected = ['PREP', 'WAVE_CONFIG', 'ARENA', 'END', 'LOBBY']
    if seen != expected:
        raise AssertionError(f'Phase order: {seen}, expected {expected}')
    print(f'PASS: Phases in order: {seen}', flush=True)
    # Fantasy deletes the levels in a later server tick.
    started = time.monotonic()
    wait_state(rcon, lambda s: not [l for l in s['openLevels'] if l.startswith('mobarena:')],
               'No game level is open', timeout=60)
    print(f'INFO: Game levels closed {time.monotonic() - started:.1f} s after LOBBY was seen.', flush=True)
    for name in ('red1', 'blue1'):
        rcon.command(f'data get entity {name} Inventory', 'has the following entity data: []')
    print('PASS: Inventories are empty.', flush=True)
    deadline = time.monotonic() + 30
    while mob_arena_folders():
        if time.monotonic() > deadline:
            raise AssertionError(f'Game level folders remain: {mob_arena_folders()}')
        time.sleep(1)
    print('PASS: Game level folders are deleted.', flush=True)


def tick_mspt(rcon):
    reply = rcon.command('tick query', quiet=True)
    match = re.search(r'Average time per tick: ([\d.]+)', reply)
    if not match:
        raise AssertionError(f'Unknown /tick query output: {reply}')
    return float(match.group(1))


def scenario_s15(rcon, log, process, *, count=300, cap=20, seconds=40):
    """T10 S15: BLUE fights a wave of 300 zombies with maxAliveMobs 20: never more than 20 alive; MSPT < 50."""
    seen = []
    start_game(rcon, log, process, max_alive=cap)
    # The RED plan is the wave that BLUE fights.
    to_arena(rcon, [f'zombie:{count}', '-', '-'], ['zombie:1', '-', '-'], seen)
    tag = 'mobarena.wave.blue.1'
    wait_state(rcon, lambda s: battle(s, 'BLUE').get('state') == 'FIGHT', 'BLUE wave 1 fight', seen=seen)
    peak, peak_mspt, killed = 0, 0.0, 0
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        current = state(rcon)
        alive = max(battle(current, 'BLUE').get('alive', 0), tagged_count(rcon, tag))
        peak = max(peak, alive)
        if alive > cap:
            raise AssertionError(f'{alive} wave mobs alive, cap {cap}')
        peak_mspt = max(peak_mspt, tick_mspt(rcon))
        # Kill some mobs so the queue spawns more.
        reply = rcon.command(f'kill @e[tag={tag},limit=10]', quiet=True)
        match = re.search(r'Killed (\d+) entities', reply)
        killed += int(match.group(1)) if match else (1 if reply.startswith('Killed ') else 0)
        time.sleep(0.5)
    print(f'PASS: Max {peak} wave mobs alive (cap {cap}) over {seconds} s.', flush=True)
    if peak < cap or killed <= cap:
        raise AssertionError(f'Queue did not fill and refill: peak {peak}, {killed} mobs removed')
    print(f'PASS: Queue refilled ({killed} mobs removed, peak {peak}).', flush=True)
    if peak_mspt >= 50:
        raise AssertionError(f'MSPT {peak_mspt:.1f} >= 50')
    print(f'PASS: Max average MSPT {peak_mspt:.1f} < 50.', flush=True)
    rcon.command('ma stop', 'Game stopped')


def scenario_s16(rcon, log, process):
    """T10 S16: server stop during ARENA with live wave mobs. The caller stops the server and checks the next start."""
    seen = []
    start_game(rcon, log, process)
    to_arena(rcon, ['zombie:3', 'zombie:1', 'zombie:1'], ['zombie:3', 'zombie:1', 'zombie:1'], seen)
    wait_state(rcon, lambda s: all(battle(s, t).get('state') == 'FIGHT' and battle(s, t).get('alive', 0) > 0
                                   for t in ('RED', 'BLUE')), 'Both teams fight', seen=seen)
    print('Stopping the server during ARENA.', flush=True)


SCENARIOS = {'s1': scenario_s1, 's15': scenario_s15, 's16': scenario_s16}


def run(*, game=True, spike=False, portal_only=False, cleanup_delay=0, log_name='smoke-console.log', scenario=None):
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
            if scenario:
                scenario(rcon, log, process)
            elif game:
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--spike', action='store_true', help='also check death, rejoin, and a RED Nether portal')
    parser.add_argument('--scenario', help='comma-separated T10 scenarios: ' + ','.join(SCENARIOS) + ', or all')
    args = parser.parse_args()
    if not args.scenario:
        run(spike=args.spike)
        return
    names = list(SCENARIOS) if args.scenario == 'all' else args.scenario.split(',')
    unknown = [name for name in names if name not in SCENARIOS]
    if unknown:
        parser.error(f'Unknown scenario: {unknown}')
    for name in names:
        print(f'=== Scenario {name.upper()} ===', flush=True)
        run(scenario=SCENARIOS[name], log_name=f'smoke-{name}.log')
        if name == 's16':
            # The next start must delete the level folders of the stopped game.
            run(game=False, log_name='smoke-s16-restart.log')
        print(f'=== Scenario {name.upper()}: PASS ===', flush=True)


if __name__ == '__main__':
    main()
