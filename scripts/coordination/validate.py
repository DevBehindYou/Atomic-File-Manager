"""Validate the shared task queue and render its human-readable roadmap. No dependencies."""

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs/dictionary/coordinator'
STATES = {'assigned', 'claimed', 'implementing', 'host_verified', 'acceptance_pending',
          'accepted', 'blocked', 'deferred', 'superseded'}


def render(data):
    lines = ['# Claude Code — next 50 tasks', '',
             'Generated from [tasks.json](tasks.json). Edit that file, then run '
             '`python scripts/coordination/validate.py --write`.', '',
             'All tasks are assigned to Claude Code for execution and Codex for review. '
             'Assignment does not mean execution has started. Read the '
             '[Coordinator Dictionary](README.md) and [handoff](CLAUDE_START.md) first.', '',
             'Default order is C001–C050. Choose the lowest eligible ID; log a blocked '
             'dependency once and continue independent work. Each task is a bounded slice '
             'of the original scope, not a replacement for its full acceptance criteria.', '']
    phase = None
    for task in data['tasks']:
        if task['phase'] != phase:
            phase = task['phase']
            lines += [f'## {phase}', '']
        deps = ', '.join(task['dependencies']) or 'None'
        lines += [f"### {task['id']} — {task['title']}", '',
                  f"**{task['priority']} · {task['status']} · Owner: {task['owner']} · Review: {task['reviewer']}**", '',
                  f"Dependencies: {deps}. Scope: {', '.join(task['scope'])}.", '',
                  f"Source areas: {', '.join('`' + area + '`' for area in task['areas'])}.", '',
                  f"**Objective.** {task['objective']}", '',
                  f"**Implementation.** {task['implementation']}", '',
                  f"**Expected outcome.** {task['expected_outcome']}", '',
                  f"**Validation.** {task['validation']}", '']
        if task['external_gate']:
            lines += [f"**Gate.** {task['external_gate']}", '']
        lines += [f"Branch: {task['branch'] or 'unclaimed'}. PR: {task['pr'] or 'none'}. "
                  f"Evidence records: {len(task['evidence'])}. Updated: {task['last_updated']}.", '']
    return '\n'.join(lines)


def validate(data):
    tasks = data['tasks']
    expected = [f'C{i:03}' for i in range(1, 51)]
    assert data['denominator'] == 50 and [t['id'] for t in tasks] == expected, 'Expected exactly C001–C050'
    ids = set(expected)
    for task in tasks:
        for key in ('phase', 'priority', 'title', 'owner', 'reviewer', 'objective',
                    'implementation', 'expected_outcome', 'validation', 'last_updated'):
            assert isinstance(task[key], str) and task[key].strip(), f"Missing {key}: {task['id']}"
        assert task['owner'] == 'Claude Code' and task['reviewer'] == 'Codex'
        assert task['status'] in STATES and task['priority'] in {'P0', 'P1', 'P2'}
        assert task['scope'] and task['areas']
        assert len(set(task['dependencies'])) == len(task['dependencies'])
        assert all(d in ids and d < task['id'] for d in task['dependencies']), 'Invalid/cyclic dependency'
        assert isinstance(task['evidence'], list) and isinstance(task['remaining'], list)
        if task['status'] in {'blocked', 'deferred', 'superseded'}:
            assert task['remaining'], 'Record the blocker or superseding evidence'
        if task['status'] == 'accepted':
            assert task['pr'] and task['evidence'] and not task['remaining'], 'Acceptance needs evidence'
            assert all(tasks[int(d[1:]) - 1]['status'] == 'accepted' for d in task['dependencies'])
    accepted = sum(t['status'] == 'accepted' for t in tasks)
    return f'Claude batch accepted: {accepted}/50 ({accepted * 2}%). Original U13/N42 denominators unchanged.'


def links():
    count = 0
    for path in DOCS.glob('*.md'):
        for target in re.findall(r'\]\(([^)]+)\)', path.read_text(encoding='utf-8')):
            if '://' in target or target.startswith('#'):
                continue
            clean = target.split('#')[0]
            assert (path.parent / clean).resolve().exists(), f'Broken link: {path.name}: {target}'
            count += 1
    return count


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true', help='Regenerate ROADMAP_50.md')
    args = parser.parse_args()
    data = json.loads((DOCS / 'tasks.json').read_text(encoding='utf-8-sig'))
    result = validate(data)
    roadmap = DOCS / 'ROADMAP_50.md'
    expected = render(data)
    if args.write:
        (DOCS / 'tasks.json').write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        roadmap.write_text(expected, encoding='utf-8')
    assert roadmap.read_text(encoding='utf-8') == expected, 'Roadmap drift; run with --write'
    print(result)
    print(f'Validated 50 tasks, dependency ordering, generated roadmap and {links()} local links.')


if __name__ == '__main__':
    main()
