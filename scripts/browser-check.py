"""Browser-Prüfung gegen eine laufende Testinstanz mit eigener Datenbank."""

import argparse
import re
from pathlib import Path

from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://localhost:18080', help='URL der Testinstanz (Standard: %(default)s)')
parser.add_argument('--chromium-executable', help='Optionaler Pfad zu einem vorhandenen Chromium-Browser')
args = parser.parse_args()

BASE = args.base_url.rstrip('/')
PROJECT = Path(__file__).resolve().parents[1]
SCREENSHOTS = PROJECT / 'target' / 'screenshots'
SCREENSHOTS.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, executable_path=args.chromium_executable)
    page = browser.new_page(viewport={'width': 1440, 'height': 1100}, device_scale_factor=1)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(BASE)
    expect(page.locator('#employee-count')).to_have_text(re.compile(r'\d+'))
    baseline = page.locator('.employee-name').all_text_contents()
    expect(page.locator('.employee-row')).to_have_count(len(baseline))
    assert page.locator('#save-button').is_disabled()
    print('PASS: initial API load')
    page.screenshot(path=str(SCREENSHOTS / 'desktop.png'), full_page=True)

    if len(baseline) > 1:
        page.locator('.drag-handle').nth(1).focus()
        page.keyboard.press('ArrowUp')
        expect(page.locator('.employee-name').first).to_have_text(baseline[1])
        expect(page.locator('#spotlight-title')).to_have_text(baseline[1])
        page.keyboard.press('End')
        expect(page.locator('.employee-name').last).to_have_text(baseline[1])
        page.keyboard.press('Home')
        expect(page.locator('.employee-name').first).to_have_text(baseline[1])
        page.locator('#reset-button').click()
        assert page.locator('.employee-name').all_text_contents() == baseline
        page.locator('.move-button[data-direction="up"]').nth(1).click()
        expect(page.locator('.employee-name').first).to_have_text(baseline[1])
        page.locator('#reset-button').click()
        print('PASS: keyboard, Home/End, arrow buttons, discard')

    def mouse_drag(from_index, to_index, cancel=False):
        handle = page.locator('.drag-handle').nth(from_index)
        handle.scroll_into_view_if_needed()
        start = handle.bounding_box()
        target = page.locator('.employee-row').nth(to_index).bounding_box()
        x = start['x'] + start['width']/2
        page.mouse.move(x, start['y'] + start['height']/2)
        page.mouse.down()
        page.mouse.move(x, target['y'] + target['height'] * (.8 if from_index < to_index else .2), steps=20)
        if cancel:
            page.keyboard.press('Escape')
        page.mouse.up()

    if len(baseline) > 1:
        target_index = min(3, len(baseline) - 1)
        mouse_drag(0, target_index, cancel=True)
        assert page.locator('.employee-name').all_text_contents() == baseline
        mouse_drag(0, target_index)
        expect(page.locator('.employee-name').nth(target_index)).to_have_text(baseline[0])
        mouse_drag(target_index, 0)
        assert page.locator('.employee-name').all_text_contents() == baseline
        mouse_drag(0, target_index)
        print('PASS: mouse drag down/up and Escape cancellation')

        page.route('**/api/save-mitarbeiter', lambda route: route.abort())
        page.locator('#save-button').click()
        expect(page.locator('#toast')).to_have_class('toast error')
        assert page.locator('#save-button').is_enabled()
        expect(page.locator('.employee-name').nth(target_index)).to_have_text(baseline[0])
        page.unroute('**/api/save-mitarbeiter')
        with page.expect_request('**/api/save-mitarbeiter') as sent:
            page.locator('#save-button').click()
        body = sent.value.post_data_json
        assert body['mitarbeiter'][target_index] == {'name': baseline[0], 'ranking': target_index}
        assert [e['ranking'] for e in body['mitarbeiter']] == list(range(len(baseline)))
        assert list(body) == ['mitarbeiter']
        assert all(set(e) == {'name', 'ranking'} for e in body['mitarbeiter'])
        expect(page.locator('#save-status')).to_have_text('Rangliste erfolgreich gespeichert')
        page.reload()
        expect(page.locator('.employee-name').nth(target_index)).to_have_text(baseline[0])
        print('PASS: save error recovery, exact JSON payload, save and reload')
    else:
        print('SKIP: reordering requires at least two employees')
    for width in [320, 390, 768, 1024, 1440]:
        page.set_viewport_size({'width': width, 'height': 844})
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), f'Overflow at {width}'
    page.set_viewport_size({'width': 390, 'height': 844})
    page.screenshot(path=str(SCREENSHOTS / 'mobile.png'), full_page=True)
    print('PASS: responsive widths 320/390/768/1024/1440')

    failed_page = browser.new_page()
    failed_page.route('**/api/mitarbeiter', lambda route: route.abort())
    failed_page.goto(BASE)
    expect(failed_page.locator('#load-error')).to_be_visible()
    assert failed_page.locator('#save-button').is_disabled()
    failed_page.unroute('**/api/mitarbeiter')
    failed_page.locator('#retry-button').click()
    expect(failed_page.locator('#employee-count')).to_have_text(str(len(baseline)))
    expect(failed_page.locator('.employee-row')).to_have_count(len(baseline))
    print('PASS: load error and retry')
    failed_page.close()

    context = browser.new_context(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True, device_scale_factor=1)
    touch_page = context.new_page()
    touch_page.on('pageerror', lambda error: errors.append(str(error)))
    touch_page.goto(BASE)
    expect(touch_page.locator('#employee-count')).to_have_text(str(len(baseline)))
    expect(touch_page.locator('.employee-row')).to_have_count(len(baseline))
    touch_baseline = touch_page.locator('.employee-name').all_text_contents()
    if len(touch_baseline) > 1:
        touch_target = min(2, len(touch_baseline) - 1)
        handle = touch_page.locator('.drag-handle').first
        handle.scroll_into_view_if_needed()
        start = handle.bounding_box()
        target = touch_page.locator('.employee-row').nth(touch_target).bounding_box()
        x, y = start['x']+start['width']/2, start['y']+start['height']/2
        target_y = target['y'] + target['height']*.8
        cdp = context.new_cdp_session(touch_page)
        cdp.send('Input.dispatchTouchEvent', {'type':'touchStart', 'touchPoints':[{'x':x,'y':y}]})
        for step in range(1,21):
            cdp.send('Input.dispatchTouchEvent', {'type':'touchMove', 'touchPoints':[{'x':x,'y':y+(target_y-y)*step/20}]})
        cdp.send('Input.dispatchTouchEvent', {'type':'touchEnd', 'touchPoints':[]})
        expect(touch_page.locator('.employee-name').nth(touch_target)).to_have_text(touch_baseline[0])
        touch_page.locator('#reset-button').click()
        assert touch_page.locator('.employee-name').all_text_contents() == touch_baseline
        print('PASS: real touch drag using Chromium input events')
    else:
        print('SKIP: touch reordering requires at least two employees')
    assert not errors, errors
    print('PASS: no JavaScript errors')
    browser.close()
