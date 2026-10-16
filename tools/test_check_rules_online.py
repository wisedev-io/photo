#!/usr/bin/env python3
"""Offline tests of the rules watch: the patterns accept the State Department's wording and reject changed figures."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_rules_online as w  # noqa: E402

# Wording as quoted from the official pages (see docs/RULES.md).
HEAD = "Sized such that the head is between 1 inch and 1 3/8 inches (22 mm and 35 mm) or 50% and 69% of the image's total height from the bottom of the chin to the top of the head."
EYE = "The eye height (measured from the bottom of the image to the level of the eyes) should be between 56% and 69% of the image's height."
SIZE = "Minimum acceptable dimensions are 600 x 600 pixels. Maximum acceptable dimensions are 1200 x 1200 pixels."
FILE = "The image must be in JPEG file format. The image must be less than or equal to 240 kB (kilobytes)."
AGE = "Photos must be taken within the last 6 months to reflect your current appearance."
FAQ = "Your photo must be a clear shot of your entire face horizontally centered in the photo. Profile shots or photos not in focus will not be accepted."


def run(source, page):
    figures = w.read_figures()
    return dict(w.check_text(w.normalise(page), w.expectations(figures)[source]))


class RulesWatch(unittest.TestCase):
    def test_figures_come_from_the_code(self):
        f = w.read_figures()
        self.assertEqual(f['head'], (50, 69))
        self.assertEqual(f['eye'], (56, 69))
        self.assertEqual(f['side'], (600, 1200))
        self.assertEqual((f['kb'], f['months']), (240, 6))

    def test_sources_are_read_from_the_catalogue(self):
        u = w.read_sources()
        for name in ('PHOTO_REQUIREMENTS', 'COMPOSITION_TEMPLATE', 'DIGITAL_IMAGE', 'PHOTO_FAQ'):
            self.assertTrue(u[name].startswith('https://travel.state.gov/'), name)

    def test_official_wording_is_recognised(self):
        self.assertTrue(all(run('COMPOSITION_TEMPLATE', f'<p>{HEAD}</p><p>{EYE}</p>').values()))
        self.assertTrue(all(run('DIGITAL_IMAGE', f'<li>{SIZE}</li><li>{FILE}</li>').values()))
        self.assertTrue(all(run('PHOTO_REQUIREMENTS', f'<div>{HEAD} {AGE}</div>').values()))
        self.assertTrue(all(run('PHOTO_FAQ', f'<p>{FAQ}</p>').values()))

    def test_markup_entities_and_dash_styles_do_not_hide_figures(self):
        page = '<script>var x="50% and 70%"</script><p>head <b>50</b>&nbsp;%&ndash;<b>69</b>&nbsp;%</p><p>6&nbsp;months &mdash; last 6 months</p>'
        r = run('PHOTO_REQUIREMENTS', page)
        self.assertTrue(all(r.values()), r)
        self.assertTrue(all(run('DIGITAL_IMAGE', '600×600… 1200 by 1200 …240 KB …JPG'.replace('×', ' x ')).values()))

    def test_changed_figures_are_reported(self):
        self.assertFalse(all(run('COMPOSITION_TEMPLATE', f'{HEAD.replace("69%", "70%")} {EYE}').values()))
        self.assertFalse(all(run('COMPOSITION_TEMPLATE', f'{HEAD} {EYE.replace("56%", "55%")}').values()))
        self.assertFalse(all(run('DIGITAL_IMAGE', f'{SIZE.replace("1200", "1000")} {FILE}').values()))
        self.assertFalse(all(run('DIGITAL_IMAGE', f'{SIZE} {FILE.replace("240", "200")}').values()))
        self.assertFalse(all(run('PHOTO_REQUIREMENTS', f'{HEAD} {AGE.replace("6 months", "12 months")}').values()))

    def test_text_in_scripts_does_not_count(self):
        r = run('PHOTO_REQUIREMENTS', f'<script>{HEAD} {AGE}</script><p>nothing here</p>')
        self.assertFalse(any(r.values()))

    def test_every_source_has_expectations_and_a_url(self):
        figures = w.read_figures()
        urls = w.read_sources()
        for name in w.expectations(figures):
            self.assertIn(name, urls)


if __name__ == '__main__':
    unittest.main(verbosity=2)
