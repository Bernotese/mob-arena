#!/usr/bin/env python3
"""Compare shutdown paths twice and check cleanup on each next startup."""
from contextlib import redirect_stdout
import json
import re
import subprocess
import sys
import traceback

import smoke

EVIDENCE = smoke.ROOT / 'tools/evidence'


def main():
    EVIDENCE.mkdir(exist_ok=True)
    smoke.RUN.mkdir(exist_ok=True)
    rows = []
    cases = [('1-no-game', dict(game=False)),
             ('2-game-wait-10s', dict(cleanup_delay=10)),
             ('3-portal-rejoin-immediate', dict(spike=True))]
    attempts = [(f'{case}-{attempt}', options) for case, options in cases for attempt in (1, 2)]
    # This extra startup checks the folders left by the final comparison run.
    attempts.append(('final-cleanup-check', {}))
    for name, options in attempts:
        print(f'RUN: {name}', flush=True)
        log_name = 'smoke-console.log' if name == 'final-cleanup-check' else f'compare-{name}.log'
        driver_log = smoke.RUN / f'compare-{name}-driver.log'
        failure = ''
        with driver_log.open('w') as output, redirect_stdout(output):
            try:
                if name == 'final-cleanup-check':
                    result = subprocess.run([sys.executable, str(smoke.ROOT / 'tools/smoke.py')],
                                            stdout=output, stderr=subprocess.STDOUT)
                    if result.returncode:
                        raise RuntimeError(f'Default smoke exit status: {result.returncode}')
                else:
                    smoke.run(log_name=log_name, **options)
            except Exception as error:
                failure = str(error)
                traceback.print_exc(file=output)
        text = (smoke.RUN / log_name).read_text(errors='replace')
        driver = driver_log.read_text(errors='replace')
        match = re.search(r'(?m)^.*\[.*ERROR\].*\n(?:.*\n)*?(?=^\[|\Z)', text)
        if match:
            (EVIDENCE / f'{name}-error.txt').write_text(match.group())
        rows.append(dict(case=name, result='FAIL' if failure else 'PASS',
                         startup_clean='PASS: No Mob Arena level folders after startup.' in driver,
                         rejected_execution='RejectedExecutionException' in text,
                         error=failure))
        (EVIDENCE / 'T10b-comparison.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(f'{name}: {rows[-1]}', flush=True)
    lines = ['# T10b shutdown comparison', '',
             '| Case | Check | RejectedExecutionException | Folders clear on next start |',
             '|---|---|---|---|']
    for index, row in enumerate(rows[:-1]):
        clean = rows[index + 1]['startup_clean']
        lines.append(f"| {row['case']} | {row['result']} | {'yes' if row['rejected_execution'] else 'no'} | {'yes' if clean else 'NO'} |")
    lines += ['', f"Final default smoke: {rows[-1]['result']}.", '', 'The checker rejects every exception and ERROR entry.',
              'Each run uses the shared Gradle lock.',
              'The final default smoke checks cleanup after the last comparison run.', '']
    report = '\n'.join(lines)
    (EVIDENCE / 'T10b-comparison.md').write_text(report)
    print(report)


if __name__ == '__main__':
    main()
