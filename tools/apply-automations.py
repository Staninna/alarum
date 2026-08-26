#!/usr/bin/env python3
"""Append the Alarum automations to Home Assistant's automations.yaml, idempotently.

Backup goes to /media, never a .bak beside the config, per the box conventions.
"""
import datetime
import os
import shutil
import sys

CONFIG = "/config/automations.yaml"
BLOCK = "/tmp/alarum-automations.yaml"
MARK_START = "# >>> ALARUM >>>"
MARK_END = "# <<< ALARUM <<<"

body = open(BLOCK).read().rstrip("\n")
current = open(CONFIG).read()

stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
backup_dir = f"/media/ha-cleanup-{datetime.date.today()}"
os.makedirs(backup_dir, exist_ok=True)
shutil.copy2(CONFIG, f"{backup_dir}/automations.yaml.{stamp}")

if MARK_START in current:
    head = current.split(MARK_START)[0].rstrip("\n")
    tail = current.split(MARK_END)[1] if MARK_END in current else ""
    current = head + tail.rstrip("\n")
    print("replacing existing Alarum block")

new = current.rstrip("\n") + "\n\n" + MARK_START + "\n" + body + "\n" + MARK_END + "\n"

# Validate before writing anything back.
import yaml


class Loose(yaml.SafeLoader):
    pass


Loose.add_multi_constructor("!", lambda loader, suffix, node: None)

parsed = yaml.load(new, Loader=Loose)
ids = [a["id"] for a in parsed]
if len(ids) != len(set(ids)):
    sys.exit(f"duplicate automation ids: {[i for i in ids if ids.count(i) > 1]}")

open(CONFIG, "w").write(new)
print(f"wrote {len(parsed)} automations, {len([i for i in ids if i.startswith('alarum_')])} of them Alarum")
print(f"backup: {backup_dir}/automations.yaml.{stamp}")
