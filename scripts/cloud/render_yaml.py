#!/usr/bin/env python3
"""Render inspectable YAML using the same configuration as the guarded release."""
import argparse
import json
from pathlib import Path
import re
import yaml
from run_release import SERVICES, render, validate

def write_manifests(config, images, revision, out):
    validate(config)
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,18}', revision):
        raise ValueError('Invalid revision suffix')
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for service in SERVICES:
        manifest = render(config, service, images[service], images['collector'], revision)
        path = out / ('cloudrun-' + service + '.yaml')
        path.write_text(yaml.safe_dump(manifest, sort_keys=False))
    return [out / ('cloudrun-' + service + '.yaml') for service in SERVICES]

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config', required=True)
    p.add_argument('--images', required=True)
    p.add_argument('--revision', required=True)
    p.add_argument('--out', default='artifacts/cloud-release/manifests')
    a = p.parse_args()
    for path in write_manifests(json.loads(Path(a.config).read_text()),
                               json.loads(Path(a.images).read_text()), a.revision, a.out):
        print(path)
