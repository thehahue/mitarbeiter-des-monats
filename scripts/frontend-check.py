"""Frontend mit simulierten API-Antworten prüfen; verändert keine Datenbank."""

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread

from playwright.sync_api import expect, sync_playwright


PROJECT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--chromium-executable', help='Optionaler Pfad zu Chromium')
args = parser.parse_args()


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def entries(count):
    return [{'name': f'Person {index + 1}', 'ranking': index} for index in range(count)]


server = ThreadingHTTPServer(
    ('127.0.0.1', 0),
    partial(QuietHandler, directory=str(PROJECT / 'src/main/resources/static')),
)
Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=args.chromium_executable)
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('https://fonts.googleapis.com/**', lambda route: route.abort())
        team = []
        page.route('**/api/mitarbeiter', lambda route: route.fulfill(json={'mitarbeiter': team}))

        for count in [0, 1, 3, 10, 12, 101]:
            team = entries(count)
            page.goto(base)
            expect(page.locator('#employee-count')).to_have_text(str(count))
            expect(page.locator('.employee-row')).to_have_count(count)
            expect(page.locator('#new-employee-button')).to_be_enabled()
            expect(page.locator('#save-button')).to_be_disabled()
            if count == 0:
                expect(page.locator('#empty-state')).to_be_visible()
                expect(page.locator('.first-place')).to_be_hidden()
            else:
                expect(page.locator('#empty-state')).to_be_hidden()
                expect(page.locator('#spotlight-title')).to_have_text('Person 1')
                expect(page.locator('.rank').last).to_have_text(str(count).zfill(2))
                expect(page.locator('[data-direction="up"]').first).to_be_disabled()
                expect(page.locator('[data-direction="down"]').last).to_be_disabled()
        print('PASS: 0/1/3/10/12/101 employees, count, ranks and empty state')

        # The returned list, including its order, is authoritative.
        team = entries(12)
        page.goto(base)
        expect(page.locator('.employee-row')).to_have_count(12)
        page.locator('#new-employee-button').click()
        dialog = page.locator('#new-employee-dialog')
        name = page.locator('#new-employee-name')
        expect(dialog).to_be_visible()
        expect(name).to_be_focused()
        page.keyboard.press('Escape')
        expect(dialog).to_be_hidden()
        expect(page.locator('#new-employee-button')).to_be_focused()
        page.locator('#new-employee-button').click()
        name.fill('   ')
        page.locator('#new-employee-submit').click()
        expect(page.locator('#new-employee-error')).to_have_text('Bitte gib einen Namen ein.')

        pending = []
        page.route('**/api/new-mitarbeiter', lambda route: pending.append(route))
        name.fill('  Max Meier  ')
        with page.expect_request('**/api/new-mitarbeiter') as sent:
            name.press('Enter')
        assert sent.value.method == 'POST'
        assert sent.value.headers['content-type'] == 'application/json'
        assert sent.value.post_data_json == {'name': 'Max Meier'}
        expect(page.locator('#new-employee-submit')).to_be_disabled()
        expect(page.locator('#new-employee-cancel')).to_be_disabled()
        expect(name).to_be_disabled()
        page.keyboard.press('Escape')
        expect(dialog).to_be_visible()
        page.locator('#new-employee-form').evaluate('(form) => form.requestSubmit()')
        assert len(pending) == 1
        team = [{'name': 'Max Meier', 'ranking': 0}] + [
            {**entry, 'ranking': index + 1} for index, entry in enumerate(team)
        ]
        pending.pop().fulfill(json={'mitarbeiter': list(reversed(team))})
        expect(dialog).to_be_hidden()
        expect(page.locator('.employee-row')).to_have_count(13)
        expect(page.locator('#spotlight-title')).to_have_text('Max Meier')
        expect(page.locator('#save-button')).to_be_disabled()
        expect(page.locator('#new-employee-button')).to_be_focused()
        print('PASS: popup focus/Escape, blank name, exact POST, busy guard, response applied')

        # Failures keep both the typed name and the unsaved order.
        page.locator('.drag-handle').nth(1).focus()
        page.keyboard.press('ArrowUp')
        before_error = page.locator('.employee-name').all_text_contents()
        page.locator('#new-employee-button').click()
        expect(page.locator('#new-employee-note')).to_be_visible()
        name.fill('Max Meier')
        page.locator('#new-employee-submit').click()
        expect(name).to_be_disabled()
        pending.pop().fulfill(status=400, json={'detail': 'Dieser Mitarbeiter existiert bereits.'})
        expect(page.locator('#new-employee-error')).to_have_text('Dieser Mitarbeiter existiert bereits.')
        expect(dialog).to_be_visible()
        expect(name).to_have_value('Max Meier')
        expect(name).to_be_focused()
        assert page.locator('.employee-name').all_text_contents() == before_error
        page.locator('#new-employee-submit').click()
        expect(name).to_be_disabled()
        pending.pop().fulfill(json={'mitarbeiter': [{'name': 'Broken', 'ranking': -1}]})
        expect(page.locator('#new-employee-error')).to_have_text('Die empfangene Rangliste ist ungültig.')
        assert page.locator('.employee-name').all_text_contents() == before_error
        page.locator('#new-employee-submit').click()
        expect(name).to_be_disabled()
        pending.pop().abort()
        expect(page.locator('#new-employee-error')).to_contain_text('Keine Verbindung')
        page.locator('#new-employee-cancel').click()
        expect(page.locator('#save-button')).to_be_enabled()
        print('PASS: HTTP/network/invalid-response recovery preserves name and unsaved order')

        def save(route):
            saved_team = route.request.post_data_json['mitarbeiter']
            assert [entry['ranking'] for entry in saved_team] == list(range(13))
            route.fulfill(json={'mitarbeiter': saved_team})

        page.route('**/api/save-mitarbeiter', save)
        page.locator('#save-button').click()
        expect(page.locator('#save-status')).to_have_text('Rangliste erfolgreich gespeichert')
        expect(page.locator('#save-button')).to_be_disabled()
        page.locator('.drag-handle').first.focus()
        page.keyboard.press('End')
        expect(page.locator('.employee-name').last).to_have_text(before_error[0])
        page.locator('#reset-button').click()
        assert page.locator('.employee-name').all_text_contents() == before_error
        print('PASS: sorting, saving and discarding with more than ten employees')

        for width in [320, 390, 768, 1024, 1440]:
            page.set_viewport_size({'width': width, 'height': 844})
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
            page.locator('#new-employee-button').click()
            expect(dialog).to_be_visible()
            bounds = dialog.bounding_box()
            assert bounds['x'] >= 0 and bounds['x'] + bounds['width'] <= width, width
            assert page.evaluate('document.querySelector("dialog").scrollWidth <= document.querySelector("dialog").clientWidth'), width
            page.locator('#new-employee-cancel').click()
        screenshot_dir = PROJECT / 'target/screenshots'
        screenshot_dir.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(screenshot_dir / 'frontend-new-employee-desktop.png'), full_page=True)
        page.set_viewport_size({'width': 390, 'height': 844})
        page.locator('#new-employee-button').click()
        name.fill('Max Meier')
        page.screenshot(path=str(screenshot_dir / 'frontend-new-employee-mobile.png'))
        print('PASS: list and popup fit widths 320/390/768/1024/1440')

        # Creation is also available when no employees exist yet.
        page.locator('#new-employee-cancel').click()
        team = []
        page.goto(base)
        expect(page.locator('#empty-state')).to_be_visible()
        page.locator('#new-employee-button').click()
        name.fill('Erster Mitarbeiter')
        page.locator('#new-employee-submit').click()
        expect(name).to_be_disabled()
        pending.pop().fulfill(json={'mitarbeiter': [{'name': 'Erster Mitarbeiter', 'ranking': 0}]})
        expect(dialog).to_be_hidden()
        expect(page.locator('#empty-state')).to_be_hidden()
        expect(page.locator('#spotlight-title')).to_have_text('Erster Mitarbeiter')
        expect(page.locator('#employee-count')).to_have_text('1')
        assert not errors, errors
        print('PASS: first employee from empty list; no JavaScript errors')
        browser.close()
finally:
    server.shutdown()
    server.server_close()
