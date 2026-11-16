#!/usr/bin/env python3
"""Create a PRIVATE RikkaHub provider import with GitHub egress authentication.

Requires qrcode[pil] in the active Python environment. Credentials are read from
RIKKA_GITHUB_TOKEN or the active `gh` login, never from a command-line argument.
Outputs stay in .private-config (ignored by Git), with owner-only permissions.
The Google Gemini API key is deliberately left blank for entry on the phone.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import uuid

GUIDE = (
    "GitHub HTTPS Git and REST authentication is configured by the remote environment. "
    "Use git over HTTPS to clone and push, or call https://api.github.com with curl or Python; "
    "the egress proxy adds authentication automatically. Do not use SSH or require gh auth login. "
    "Access is limited to repositories permitted by the configured GitHub token. "
    "Never request, print, or store credentials. Verify repository access before substantial work; "
    "push changes to a feature branch and verify its remote commit. "
    "If GitHub rejects workflow-file changes or another operation, report the missing permission. "
    "Each new interaction starts a fresh sandbox: re-clone the requested repository as needed."
)


def provider_config(token):
    basic = base64.b64encode(('x-oauth-basic:' + token).encode()).decode()
    environment = {
        'type': 'remote',
        'network': {'allowlist': [
            {'domain': 'github.com', 'transform': {'Authorization': 'Basic ' + basic}},
            {'domain': 'api.github.com', 'transform': {'Authorization': 'Bearer ' + token}},
            {'domain': '*'},
        ]},
    }
    return {
        'type': 'google',
        'id': str(uuid.uuid4()),
        'name': 'Antigravity GitHub',
        'enabled': True,
        'baseUrl': 'https://generativelanguage.googleapis.com/v1beta',
        'apiKey': '',
        'vertexAI': False,
        'useInteractionsApi': True,
        'models': [{
            'id': str(uuid.uuid4()),
            'modelId': 'antigravity-preview-09-2026',
            'displayName': 'Antigravity + GitHub',
            'type': 'CHAT',
            'inputModalities': ['TEXT'],
            'outputModalities': ['TEXT'],
            'abilities': ['REASONING'],
            'customBodies': [
                {'key': 'environment', 'value': environment},
                {'key': 'system_instruction', 'value': GUIDE},
            ],
        }],
    }


def private_write(path, data):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, 'wb') as file:
        file.write(data)


def main():
    import qrcode
    token = os.environ.get('RIKKA_GITHUB_TOKEN', '').strip()
    if not token:
        token = subprocess.check_output(['gh', 'auth', 'token'], text=True, stderr=subprocess.DEVNULL).strip()
    if not token:
        raise SystemExit('No GitHub credential available')
    root = Path(__file__).resolve().parents[2]
    output = root / '.private-config'
    output.mkdir(mode=0o700, exist_ok=True)
    os.chmod(output, 0o700)
    provider = provider_config(token)
    packed = json.dumps(provider, ensure_ascii=False, separators=(',', ':')).encode()
    imported = 'ai-provider:v1:' + base64.b64encode(packed).decode()
    private_write(output / 'antigravity-github-provider.json', packed)
    private_write(output / 'antigravity-github-import.txt', imported.encode())
    environment = provider['models'][0]['customBodies'][0]['value']
    private_write(output / 'github-environment.json', json.dumps(environment, indent=2).encode())
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_L, box_size=8, border=4)
    qr.add_data(imported)
    qr.make(fit=True)
    from io import BytesIO
    png = BytesIO()
    qr.make_image(fill_color='black', back_color='white').save(png, format='PNG')
    private_write(output / 'antigravity-github-qr.png', png.getvalue())
    print('Created private provider JSON, import text, environment JSON and QR in .private-config/.')
    print('Scan through Settings > Providers > Import, then enter your Gemini API key on the phone.')
    print('Contains a GitHub credential: never upload these files to Git or public Releases.')


if __name__ == '__main__':
    main()
