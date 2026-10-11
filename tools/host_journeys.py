#!/usr/bin/env python3
"""Actual intents, Back/Up, saved task restoration and screens on an explicit device.

Run separately from instrumentation on a disposable emulator with the debug APK installed.
Only this fixture's task/data are reset. No gesture-animation or performance claim.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from fresh_android_xml import read_ui_xml
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--output', type=Path, required=True)
args = p.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (out / helper_name).write_bytes(helper.read_bytes())
package = 'staff.android.navigation'
commands = []
result = {'date': datetime.now(timezone.utc).isoformat(), 'serial': args.serial,
    'scope': 'actual debug APK intents, task history and background process restoration',
    'scenarios': [], 'passed': False}

def adb(*parts, check=True, binary=False):
    r = subprocess.run([args.adb, '-s', args.serial, *map(str, parts)], capture_output=True)
    commands.append({'args': list(parts), 'exit': r.returncode})
    if check and r.returncode:
        raise RuntimeError(r.stdout.decode() + r.stderr.decode())
    return r.stdout if binary else r.stdout.decode().strip()

def tree(allow_system=False):
    expected = {package}
    if allow_system:
        expected |= {'com.google.android.apps.nexuslauncher', 'com.android.launcher3', 'com.android.systemui'}
    content = read_ui_xml(adb, '/sdcard/chapter9-host.xml', expected)
    return ET.fromstring(content), content

def find(root, tag):
    return next((n for n in root.iter('node') if n.get('resource-id') in
        (tag, package + ':id/' + tag)), None)

def scroll_screen(forward):
    root, _ = tree()
    region = next((n for n in root.iter('node') if n.get('scrollable') == 'true'), None)
    if region is None:
        return
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', region.get('bounds')))
    top, bottom = y1+(y2-y1)//5, y1+(y2-y1)*4//5
    adb('shell', 'input', 'swipe', (x1+x2)//2, bottom if forward else top,
        (x1+x2)//2, top if forward else bottom, 180)


def expect(tag, text=None):
    end = time.monotonic() + 20
    attempt = 0
    while time.monotonic() < end:
        root, _ = tree()
        n = find(root, tag)
        if n is not None and (text is None or text.casefold() in n.get('text', '').casefold()):
            return n
        attempt += 1
        scroll_screen(attempt % 6 < 3)
    raise AssertionError((tag, text))

def tap(tag):
    n = expect(tag)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    assert x2 > x1 and y2 > y1
    adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)

def capture(label):
    root, xml = tree()
    (out / (label + '.xml')).write_text(xml)
    (out / (label + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    return {'capture': label, 'nodes': {tag: find(root, tag).get('text')
        for tag in ('open_cart', 'open_camera', 'open_tea', 'accepted', 'quantity_input', 'problem')
        if find(root, tag) is not None}}

def task_snapshot(label):
    dump = adb('shell', 'dumpsys', 'activity', 'activities')
    (out / (label + '.txt')).write_text(dump)
    match = re.search(r'topResumedActivity=ActivityRecord\{[^}]* '
        + re.escape(package) + r'/\.MainActivity t(\d+)\}', dump)
    assert match, 'Expected resumed MainActivity in retained task'
    return int(match.group(1))

def resume_from_recents():
    adb('shell', 'input', 'keyevent', 'KEYCODE_APP_SWITCH')
    deadline = time.monotonic() + 45
    attempt = 0
    while time.monotonic() < deadline:
        root, xml = tree(allow_system=True)
        (out / f'recents-{attempt:02}.xml').write_text(xml)
        attempt += 1
        snapshot = next((n for n in root.iter('node') if
            n.get('content-desc') == 'Common Goods' and
            n.get('resource-id', '').endswith('/snapshot')), None)
        if snapshot is not None:
            (out / 'recents.xml').write_text(xml)
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', snapshot.get('bounds')))
            adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
            return
        # This launcher's carousel can retain an old scroll position.
        overview = next((n for n in root.iter('node') if
            n.get('resource-id', '').endswith('/overview_panel')), None)
        assert overview is not None, 'Expected Recents overview'
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', overview.get('bounds')))
        adb('shell', 'input', 'swipe', x1 + (x2-x1)*4//5, (y1+y2)//2,
            x1 + (x2-x1)//5, (y1+y2)//2, 300)
    raise AssertionError('Retained Common Goods task absent from Recents')

def fresh(activity='MainActivity'):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package + '/.' + activity, '-f', '0x10008000')

def open_editor():
    tap('open_cart')
    tap('open_camera')
    expect('quantity_input')

def enter(raw):
    tap('quantity_input')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * 32))
    if raw:
        adb('shell', 'input', 'text', raw)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    expect('quantity_input', raw)

def back():
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def cold_link(product):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW',
        '-d', 'shoplab://cart/edit/' + product, '-p', package, '-f', '0x10008000')

try:
    result['device'] = {key: adb('shell', 'getprop', key) for key in
        ('ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.fingerprint')}
    result['settings'] = {'display': adb('shell', 'wm', 'size'), 'density': adb('shell', 'wm', 'density'),
        'font_scale': adb('shell', 'settings', 'get', 'system', 'font_scale'),
        'input_method': adb('shell', 'settings', 'get', 'secure', 'default_input_method')}
    fresh()
    tap('lab_controls')
    tap('reset')
    tap('broken_toggle')
    open_editor()
    enter('3')
    expect('accepted', '2')
    before = capture('01-broken-before-confirm')
    tap('confirm')
    expect('open_camera', '3')
    after = capture('02-broken-new-cart')
    back()
    expect('quantity_input', '3')
    expect('accepted', '3')
    result['scenarios'].append({'name': 'broken-append-reveals-finished-editor',
        'before': before, 'after': after, 'back': capture('03-broken-back-to-finished-editor')})

    fresh()
    tap('lab_controls')
    tap('reset')
    open_editor()
    enter('3')
    tap('confirm')
    expect('open_camera', '3')
    after = capture('04-correct-existing-cart')
    back()
    expect('open_cart')
    result['scenarios'].append({'name': 'correct-return-then-catalog',
        'after': after, 'back': capture('05-correct-back-to-catalog')})

    tap('lab_controls')
    tap('reset')
    cold_link('camera')
    expect('accepted', '2')
    enter('3')
    before = capture('06-deep-entry-draft')
    old_pid = adb('shell', 'pidof', package)
    old_task = task_snapshot('before-process-death')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(1)
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline and adb('shell', 'pidof', package, check=False):
        adb('shell', 'am', 'kill', package)
        time.sleep(1)
    assert not adb('shell', 'pidof', package, check=False), 'Background process did not terminate'
    resume_from_recents()
    expect('quantity_input', '3')
    expect('accepted', '2')
    new_pid = adb('shell', 'pidof', package)
    new_task = task_snapshot('after-process-restoration')
    assert old_task == new_task, 'Restoration must resume the original task'
    assert old_pid != new_pid and new_pid
    restored = capture('07-restored-deep-entry-draft')
    back(); expect('open_camera', '2')
    cart = capture('08-restored-parent-cart')
    back(); expect('open_cart')
    result['scenarios'].append({'name': 'deep-entry-process-restoration-preserves-draft-and-parent-history',
        'old_pid': old_pid, 'new_pid': new_pid, 'old_task': old_task,
        'new_task': new_task, 'before': before, 'restored': restored,
        'cart': cart, 'catalog': capture('09-restored-parent-catalog')})

    fresh('CallerActivity')
    tap('open_camera')
    expect('quantity_input')
    leaf = capture('10-caller-single-entry')
    back()
    expect('open_camera', 'Open Camera link')
    result['scenarios'].append({'name': 'without-new-task-back-returns-to-caller',
        'leaf': leaf, 'back': capture('11-back-to-caller')})

    tap('open_camera')
    expect('quantity_input')
    tap('up')
    expect('open_camera', 'Accepted: 2')
    parent = capture('12-up-builds-cart-parent')
    back(); expect('open_cart')
    result['scenarios'].append({'name': 'without-new-task-up-builds-parent-history',
        'cart': parent, 'catalog': capture('13-up-parent-catalog')})

    fresh('CallerActivity')
    tap('open_camera'); expect('quantity_input')
    enter('3'); tap('confirm')
    expect('open_camera', 'Accepted: 3')
    parent = capture('14-confirm-uses-parent-fallback')
    back(); expect('open_cart')
    result['scenarios'].append({'name': 'without-new-task-confirm-uses-up-fallback',
        'cart': parent, 'catalog': capture('15-fallback-parent-catalog')})

    cold_link('unknown')
    expect('problem', 'unavailable')
    root, _ = tree()
    assert find(root, 'confirm') is None
    result['scenarios'].append({'name': 'unknown-product-has-no-write-action',
        'state': capture('16-unavailable-deep-entry')})
    result['passed'] = True
except Exception as error:
    result['error'] = str(error)
    raise
finally:
    result['commands'] = commands
    result['artifacts_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
        for p in out.iterdir() if p.suffix in ('.xml', '.png', '.txt')}
    (out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'passed': result['passed'], 'scenarios': len(result['scenarios'])}))
