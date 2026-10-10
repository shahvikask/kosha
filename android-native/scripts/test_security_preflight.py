#!/usr/bin/env python3
"""Regression tests for static policy routing; never uses actual vault data.

Run: python3 -m unittest discover -s android-native/scripts -p 'test_*.py'
These are SOURCE checks, not Gradle builds or mobile penetration tests.
"""
import unittest

from security_preflight import (
    read, repository_binding_errors, vault_health_boundary_errors, cipher_page_integrity_errors,
)

SOURCE = "android-native/data/src/main/kotlin/in/growurdhan/data/vault/SqlCipherVaultRepository.kt"


class RepositoryPolicyRoutingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.production = read(SOURCE)

    def assert_rejected(self, original: str, replacement: str, method: str):
        self.assertIn(original, self.production, "Test fixture no longer matches production.")
        mutant = self.production.replace(original, replacement, 1)
        issues = repository_binding_errors(mutant)
        self.assertTrue(
            any(method in problem for problem in issues),
            "Static policy must reject bypass of " + method + ": " + repr(issues),
        )

    def test_every_current_production_entry_point_is_guarded(self):
        self.assertEqual([], repository_binding_errors(self.production))

    def test_private_book_read_cannot_bypass_authorized_session(self):
        self.assert_rejected(
            "sessionBound.read { delegate.readBook() }",
            "delegate.readBook()",
            "readBook",
        )

    def test_financial_approval_cannot_call_sqlcipher_directly(self):
        self.assert_rejected(
            "approvedWrite { delegate.commitReviewedStatement(review) }",
            "delegate.commitReviewedStatement(review)",
            "commitReviewedStatement",
        )

    def test_backup_export_cannot_bypass_recent_approval(self):
        self.assert_rejected(
            "sessionBound.readRecentlyApprovedBytes { delegate.exportEncryptedBackup(passphrase) }",
            "delegate.exportEncryptedBackup(passphrase)",
            "exportEncryptedBackup",
        )

    def test_explicit_close_must_share_open_cleanup_mutex(self):
        self.assert_rejected(
            "override suspend fun close() = sessionBound.close()",
            "override suspend fun close() = delegate.close()",
            "close",
        )

    def test_transaction_must_recheck_authorization_before_commit(self):
        self.assert_rejected(
            "check(gate.sessionRevision() == approvedRevision)",
            "check(true)",
            "transaction",
        )

    def test_production_sqlcipher_must_receive_session_authorization(self):
        self.assert_rejected(
            "SqlCipherVaultRepository(context, mutationAuthorization = authorization)",
            "SqlCipherVaultRepository(context)",
            "transaction authorization",
        )

    def test_new_unclassified_entry_point_is_rejected(self):
        mutant = self.production.replace(
            "override suspend fun status(): VaultStatus = delegate.status()",
            "override suspend fun status(): VaultStatus = delegate.status()\n"
            "    override suspend fun leakUnapproved(): Any = delegate.readBook()",
            1,
        )
        self.assertTrue(
            any("Unclassified production repository method leakUnapproved" in issue
                for issue in repository_binding_errors(mutant)),
        )

    def test_corrupt_health_cannot_be_accepted_as_ready(self):
        model = read(
            "android-native/app/src/main/java/in/growurdhan/app/presentation/VaultViewModel.kt"
        )
        gate = read(
            "android-native/domain/src/main/kotlin/in/growurdhan/domain/vault/VaultHealthGate.kt"
        )
        self.assertEqual([], vault_health_boundary_errors(model, gate))
        mutant = gate.replace('health.integrity == "ok"', "true", 1)
        self.assertTrue(vault_health_boundary_errors(model, mutant))

    def test_failed_health_query_must_close_sqlcipher_and_revoke_session(self):
        model = read(
            "android-native/app/src/main/java/in/growurdhan/app/presentation/VaultViewModel.kt"
        )
        gate = read(
            "android-native/domain/src/main/kotlin/in/growurdhan/domain/vault/VaultHealthGate.kt"
        )
        without_close = model.replace("repository.close()\n                } catch", "Unit\n                } catch", 1)
        self.assertTrue(vault_health_boundary_errors(without_close, gate))
        without_cleanup = model.replace("withContext(NonCancellable)", "withContext(Dispatchers.IO)", 1)
        self.assertTrue(vault_health_boundary_errors(without_cleanup, gate))
        without_ui_route = model.replace(
            "val health = verifiedHealthOrClose()", "val health = repository.health()", 1,
        )
        self.assertTrue(vault_health_boundary_errors(without_ui_route, gate))

    def test_encrypted_page_hmac_must_be_validated_on_open(self):
        schema = read(
            "android-native/data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"
        )
        self.assertEqual([], cipher_page_integrity_errors(schema))
        without_hmac = schema.replace(
            'db.rawQuery("PRAGMA cipher_integrity_check"',
            'db.rawQuery("PRAGMA wrong_integrity_check"', 1,
        )
        self.assertTrue(cipher_page_integrity_errors(without_hmac))
        ignored_errors = schema.replace("if (pageErrors != 0)", "if (false)", 1)
        self.assertTrue(cipher_page_integrity_errors(ignored_errors))

    def test_missing_repository_method_is_rejected(self):
        mutant = self.production.replace(
            "override suspend fun readBook(): VaultBook = sessionBound.read { delegate.readBook() }",
            "fun removedReadBookFromProduction() = Unit",
            1,
        )
        self.assertTrue(any("readBook" in x for x in repository_binding_errors(mutant)))


if __name__ == "__main__":
    unittest.main()
