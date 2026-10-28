"""Signer identity guards using retained public SDK36/37 reports; no credentials."""
import json
from pathlib import Path
import unittest

from verify_beta import verify_signer_report

ROOT = Path(__file__).parent
CERT = json.loads((ROOT / 'beta-config.json').read_text())['certificateSha256']


class SignerReportTest(unittest.TestCase):
    def setUp(self):
        self.legacy = (ROOT / 'fixtures/apksigner-sdk36.txt').read_text()
        self.current = (ROOT / 'fixtures/apksigner-sdk37.txt').read_text()
        self.line = 'V2 Signer: certificate SHA-256 digest: ' + CERT.lower()

    def rejects(self, report):
        with self.assertRaises(SystemExit):
            verify_signer_report(report, CERT)

    def test_actual_sdk36_report(self):
        verify_signer_report(self.legacy, CERT)

    def test_actual_sdk37_report(self):
        verify_signer_report(self.current, CERT)

    def test_matching_certificate_for_multiple_schemes_is_one_signer(self):
        verify_signer_report(self.current + self.line.replace('V2 ', 'V3.1 ') + '\n', CERT)

    def test_missing_signer_count(self):
        self.rejects(self.current.replace('Number of signers: 1\n', ''))

    def test_multiple_signers_even_if_digest_matches(self):
        self.rejects(self.current.replace('Number of signers: 1', 'Number of signers: 2'))

    def test_duplicate_or_conflicting_count(self):
        for count in (1, 2):
            with self.subTest(count=count):
                self.rejects(self.current + f'Number of signers: {count}\n')

    def test_missing_certificate_not_satisfied_by_public_key_digest(self):
        report = self.current.replace(self.line + '\n', '')
        report = report.replace(
            '27ced02cecbddd32ca7bf19b3264a8655ff1f5684fe766895950f7cd1e8b9d1d',
            CERT.lower())
        self.rejects(report)

    def test_wrong_certificate(self):
        for report in (self.legacy, self.current):
            with self.subTest(report_format=report.splitlines()[-1]):
                self.rejects(report.replace(CERT.lower(), 'a' * 64))

    def test_mismatching_additional_scheme_is_not_ignored(self):
        self.rejects(self.current + 'V3 Signer: certificate SHA-256 digest: ' + 'b' * 64 + '\n')

    def test_unrecognized_scheme_is_not_ignored(self):
        self.rejects(self.current + self.line.replace('V2 ', 'V99 ') + '\n')

    def test_unrecognized_label_without_known_label(self):
        self.rejects(self.current.replace('V2 Signer:', 'Other Signer:'))

    def test_second_legacy_signer_is_not_ignored(self):
        self.rejects(self.legacy + 'Signer #2 certificate SHA-256 digest: ' + CERT.lower() + '\n')

    def test_malformed_digest_is_not_ignored(self):
        for digest in ('abcd', 'g' * 64, CERT.lower() + '00', CERT.lower() + ' trailing'):
            with self.subTest(digest=digest):
                self.rejects(self.current + 'V3 Signer: certificate SHA-256 digest: ' + digest + '\n')

    def test_uppercase_digest_and_crlf_report(self):
        verify_signer_report(self.current.replace(CERT.lower(), CERT).replace('\n', '\r\n'), CERT)

    def test_invalid_pin_rejected(self):
        with self.assertRaises(SystemExit):
            verify_signer_report(self.current, 'not-a-certificate')

    def test_empty_report_rejected(self):
        self.rejects('')


if __name__ == '__main__':
    unittest.main()
