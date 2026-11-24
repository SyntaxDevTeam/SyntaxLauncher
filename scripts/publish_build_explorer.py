#!/usr/bin/env python3
"""Publish the three tested portable applications as one Build Explorer v2 release."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from launcher_version import read_launcher_version, validate_git_ref

PLATFORMS = ('windows-x64', 'macos-arm64', 'linux-x64')
MAX_BYTES = 90 * 1024 * 1024
ENDPOINT = 'https://syntaxdevteam.pl/api/v2/builds/ci/syntaxlauncher'


def prepare(directory, env, version_file=None):
    run_id = env['GITHUB_RUN_ID']
    run_number = env['GITHUB_RUN_NUMBER']
    sha = env['GITHUB_SHA']
    if not run_id.isdecimal() or int(run_id) < 1 or not run_number.isdecimal():
        raise ValueError('Invalid workflow run identity')
    if not re.fullmatch('[a-f0-9]{40}', sha):
        raise ValueError('Expected full source commit SHA')
    ref = env['GITHUB_REF']
    version = read_launcher_version(version_file) if version_file is not None else read_launcher_version()
    if ref.startswith('refs/tags/v'):
        validate_git_ref(version, ref)
        channel = 'snapshot' if '-' in version.partition('+')[0] else 'release'
    elif ref == 'refs/heads/main':
        channel = 'snapshot'
    else:
        raise ValueError('Publication is restricted to main and version tags')
    timestamp = datetime.now(timezone.utc).isoformat()
    result = []
    for platform in PLATFORMS:
        path = directory / f'syntaxlauncher-{platform}.tar.gz'
        if not path.is_file() or path.is_symlink():
            raise ValueError(f'Missing portable application: {path.name}')
        size = path.stat().st_size
        if not 0 < size <= MAX_BYTES:
            raise ValueError(f'{path.name} exceeds the 90 MiB project limit or is empty')
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            if stream.read(3) != b'\x1f\x8b\x08':
                raise ValueError(f'{path.name} is not gzip')
            stream.seek(0)
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        result.append((path, {
            'id': int(run_id), 'time': timestamp, 'channel': channel,
            'platform': platform, 'platform_family': 'other', 'game_versions': [],
            'version': version, 'build_number': run_number,
            'branch': env.get('GITHUB_REF_NAME', ''), 'commit_sha': sha,
            'filename': f'SyntaxLauncher-{version}-{platform}.tar.gz',
            'sha256': digest.hexdigest(), 'size': size, 'commits': [],
        }))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path('dist'))
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    releases = prepare(args.directory, os.environ)  # Validate every file before the first upload.
    if args.dry_run:
        print(json.dumps([metadata for _, metadata in releases], indent=2))
        return
    token = os.environ.get('BUILD_EXPLORER_TOKEN', '')
    if not token.strip():
        raise SystemExit('Configure BUILD_EXPLORER_TOKEN or grant this repository access to BUILD_CI_TOKEN.')
    if not token.isascii() or not token.isprintable() or token != token.strip():
        raise SystemExit('Build Explorer token contains characters unsuitable for an HTTP header.')
    with tempfile.TemporaryDirectory(prefix='build-explorer-') as temporary:
        for path, metadata in releases:
            platform = metadata['platform']
            metadata_file = Path(temporary) / 'metadata.json'
            metadata_file.write_text(json.dumps(metadata))
            response = subprocess.run([
                'curl', '--header', '@-', '--silent', '--show-error', '--fail-with-body',
                '--write-out', '\n%{http_code}',
                '--proto', '=https', '--connect-timeout', '30', '--max-time', '600',
                '--retry', '3', '--retry-delay', '5',
                '--form', f'metadata=@{metadata_file};type=application/json',
                '--form', f'artifact=@{path};type=application/gzip', ENDPOINT,
            ], input=f'X-Build-Token: {token}\n', text=True, capture_output=True)
            body, _, status = response.stdout.rpartition('\n')
            if status in ('401', '403'):
                raise SystemExit(f'Build Explorer rejected the token for {platform} (HTTP {status}).')
            try:
                result = json.loads(body)
            except json.JSONDecodeError:
                result = None
            if response.returncode != 0 or not status.startswith('2'):
                message = f'Build Explorer upload failed for {platform} (HTTP {status or "unknown"}, curl {response.returncode}).'
                if isinstance(result, dict):
                    for field in ('error', 'message', 'detail'):
                        detail = result.get(field)
                        if isinstance(detail, str) and detail.strip():
                            detail = detail.replace(token, '[REDACTED]')
                            detail = ' '.join(''.join(c if c.isprintable() else ' ' for c in detail).split())
                            message += f' Server response: {detail[:400]}'
                            break
                raise SystemExit(message)
            if not isinstance(result, dict) or result.get('status') != 'ok' or result.get('platform') != platform:
                raise SystemExit(f'Build Explorer did not confirm publication for {platform}.')
            print(f"Published {platform}: release {result['release_id']}, artifact {result['artifact_id']}")


if __name__ == '__main__':
    main()
