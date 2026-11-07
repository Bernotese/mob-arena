#!/usr/bin/env python3
"""Compare shutdown paths twice and check cleanup on each next startup."""
from contextlib import redirect_stdout
import argparse
import json
import re
import subprocess
import sys
import traceback

import smoke

EVIDENCE = smoke.ROOT / 'tools/evidence'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases', default='1,2,3,4,5')
    parser.add_argument('--repeats', type=int, default=2)
    parser.add_argument('--label', default='T10b')
    args = parser.parse_args()
    if args.repeats < 1 or not re.fullmatch(r'[A-Za-z0-9_-]+', args.label):
        parser.error('Use positive repeats and a simple label')
    selected = args.cases.split(',')
    if any(case not in ('1', '2', '3', '4', '5') for case in selected):
        parser.error('Choose case numbers 1 through 5')
    EVIDENCE.mkdir(exist_ok=True)
    smoke.RUN.mkdir(exist_ok=True)
    rows = []
    cases = [('1-no-game', dict(game=False)),
             ('2-game-wait-10s', dict(cleanup_delay=10)),
             ('3-portal-rejoin-immediate', dict(spike=True)),
             ('4-portal-rejoin-wait-10s', dict(spike=True, cleanup_delay=10)),
             ('5-portal-only-immediate', dict(spike=True, portal_only=True))]
    cases = [(case, options) for case, options in cases if case[0] in selected]
    attempts = [(f'{case}-{attempt}', options) for case, options in cases for attempt in range(1, args.repeats + 1)]
    # This extra startup checks the folders left by the final comparison run.
    attempts.append(('final-cleanup-check', {}))
    for name, options in attempts:
        print(f'RUN: {name}', flush=True)
        log_name = 'smoke-console.log' if name == 'final-cleanup-check' else f'compare-{args.label}-{name}.log'
        driver_log = smoke.RUN / f'compare-{args.label}-{name}-driver.log'
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
                failure = str(error).replace(str(smoke.ROOT) + '/', '')
                traceback.print_exc(file=output)
        log_path = smoke.RUN / log_name
        text = log_path.read_text(errors='replace') if log_path.exists() else ''
        driver = driver_log.read_text(errors='replace')
        match = re.search(r'(?m)^.*\[.*ERROR\].*\n(?:.*\n)*?(?=^\[|\Z)', text)
        if match:
            (EVIDENCE / f'{args.label}-{name}-error.txt').write_text(match.group())
        rows.append(dict(case=name, result='FAIL' if failure else 'PASS',
                         startup_clean='PASS: No Mob Arena level folders after startup.' in driver,
                         rejected_execution='RejectedExecutionException' in text,
                         error=failure))
        (EVIDENCE / f'{args.label}-comparison.json').write_text(json.dumps(rows, indent=2) + '\n')
        print(f'{name}: {rows[-1]}', flush=True)
    lines = [f'# {args.label} shutdown comparison', '',
             '| Case | Check | RejectedExecutionException | Folders clear on next start |',
             '|---|---|---|---|']
    for index, row in enumerate(rows[:-1]):
        clean = rows[index + 1]['startup_clean']
        lines.append(f"| {row['case']} | {row['result']} | {'yes' if row['rejected_execution'] else 'no'} | {'yes' if clean else 'NO'} |")
    lines += ['', f"Final default smoke: {rows[-1]['result']}.", '', 'The checker rejects every exception and ERROR entry.',
              'Each run uses the shared Gradle lock.',
              'The final default smoke checks cleanup after the last comparison run.', '']
    report = '\n'.join(lines)
    (EVIDENCE / f'{args.label}-comparison.md').write_text(report)
    print(report)


if __name__ == '__main__':
    main()
