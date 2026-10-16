#!/usr/bin/env python3
"""Negative tests for check_resources.py: each broken resource set must be reported."""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SCRIPT = os.path.join(HERE, 'check_resources.py')


def run(root):
    env = dict(os.environ, DV_ROOT=root)
    p = subprocess.run([sys.executable, SCRIPT], env=env, capture_output=True, text=True)
    return p.returncode, p.stdout


class CheckResources(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        shutil.copytree(os.path.join(ROOT, 'app/src/main/res'), os.path.join(self.tmp, 'app/src/main/res'))
        shutil.copytree(os.path.join(ROOT, 'app/src/main/kotlin'), os.path.join(self.tmp, 'app/src/main/kotlin'))

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def edit(self, rel, old, new):
        path = os.path.join(self.tmp, 'app/src/main/res', rel)
        s = open(path, encoding='utf-8').read()
        self.assertIn(old, s, f'test setup: {old!r} not found in {rel}')
        open(path, 'w', encoding='utf-8').write(s.replace(old, new, 1))

    def test_clean_project_passes(self):
        code, out = run(self.tmp)
        self.assertEqual(code, 0, out)

    def test_missing_key_is_reported(self):
        self.edit('values-ru/strings.xml', '<string name="busy">Проверяем фото…</string>\n', '')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('ru: missing', out)

    def test_placeholder_mismatch(self):
        self.edit('values-fr/strings.xml', '%1$d × %1$d px · %2$d Ko', '%1$d × %2$d px · %2$d Ko')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('fr:result_specs', out)

    def test_stray_percent(self):
        self.edit('values-es/strings.xml', 'Cabeza: %1$d–%2$d%% de la altura', 'Cabeza: %1$d–%2$d% de la altura')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('stray %', out)

    def test_unescaped_apostrophe(self):
        self.edit('values/strings.xml', 'I’m ready', "I'm ready")
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('unescaped apostrophe', out)

    def test_unknown_key_in_kotlin(self):
        path = os.path.join(self.tmp, 'app/src/main/kotlin/app/dvphoto/ui/Labels.kt')
        open(path, 'a', encoding='utf-8').write('\nval x = R.string.does_not_exist\n')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('does_not_exist', out)

    def test_language_lists_must_agree(self):
        self.edit('xml/locales_config.xml', '<locale android:name="tr" />', '')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('locales_config.xml', out)

    def test_malformed_xml(self):
        self.edit('values-bn/strings.xml', '</resources>', '')
        code, out = run(self.tmp)
        self.assertEqual(code, 1)
        self.assertIn('not well-formed', out)


if __name__ == '__main__':
    unittest.main(verbosity=2)
