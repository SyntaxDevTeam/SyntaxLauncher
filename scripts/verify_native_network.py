"""Run the actual packaged runtime against public Mojang catalog and metadata HTTPS.

The diagnostic branch never initializes player data, authentication or the updater.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile


def verify(image: Path, platform: str) -> None:
    executable = image / {
        'linux-x64': 'bin/SyntaxLauncher',
        'windows-x64': 'SyntaxLauncher.exe',
        'macos-arm64': 'Contents/MacOS/SyntaxLauncher',
    }[platform]
    # Windows GUI launchers do not guarantee redirected stdout; use a fresh private receipt.
    with tempfile.TemporaryDirectory(prefix='syntax-network-probe-') as stage:
        receipt = Path(stage) / 'result.txt'
        subprocess.run([str(executable.resolve()), '--verify-mojang-https', str(receipt)],
                       capture_output=True, text=True, timeout=90, check=True)
        result = receipt.read_text(encoding='utf-8')
        if not result.startswith('NETWORK_PROBE_OK catalog='):
            raise RuntimeError('Packaged HTTPS acceptance did not return its completion marker')
        print(result)



if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True, type=Path)
    parser.add_argument('--platform', required=True, choices=['linux-x64', 'windows-x64', 'macos-arm64'])
    args = parser.parse_args()
    verify(args.image, args.platform)
