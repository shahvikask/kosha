#!/usr/bin/env python3
"""Compile the Kotlin native-vault DDL before spending time on an Android emulator.

This check uses the standard-library SQLite SQL parser; SQLCipher-specific
encryption, PRAGMAs and key handling are exercised separately on Android.
The test reads the production schema definition, not a second SQL copy.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sqlite3


SCHEMA_FILE = (
    Path(__file__).resolve().parents[1]
    / "data/src/main/kotlin/in/growurdhan/data/vault/VaultSchema.kt"
)
EXPECTED_TABLES = {
    "family_member",
    "custody_account",
    "holding",
    "ledger_event",
    "snapshot_import",
    "statement_position",
    "vault_meta",
}
EXPECTED_STATEMENT_COUNT = 14


def production_ddl(source: str) -> list[str]:
    anchor = "private val ddl = listOf("
    if source.count(anchor) != 1:
        raise AssertionError("Cannot unambiguously locate native vault DDL.")
    body = source.split(anchor, 1)[1].rsplit("\n    )", 1)[0]
    if not body:
        raise AssertionError("Empty native vault DDL list.")

    # Kotlin uses only triple-quoted raw literals (with optional trimIndent)
    # and normal quoted string literals for these fixed SQL statements.
    literals = re.compile(
        r'"""(.*?)"""(?:\.trimIndent\(\))?|("(?:\\.|[^"\\])*")',
        flags=re.DOTALL,
    )
    statements = [
        (raw if raw is not None else json.loads(quoted)).strip()
        for raw, quoted in literals.findall(body)
    ]
    if len(statements) != EXPECTED_STATEMENT_COUNT:
        raise AssertionError(
            f"Expected {EXPECTED_STATEMENT_COUNT} schema statements; "
            f"found {len(statements)}. Update this preflight for deliberate DDL changes."
        )
    if any(not stmt.startswith("CREATE ") for stmt in statements):
        raise AssertionError("Unexpected non-DDL statement in schema list.")
    return statements


def assert_rejected(db: sqlite3.Connection, sql: str) -> None:
    try:
        db.execute(sql)
    except sqlite3.IntegrityError:
        return
    raise AssertionError(f"An immutable ledger operation was accepted: {sql}")


def main() -> None:
    statements = production_ddl(SCHEMA_FILE.read_text(encoding="utf-8"))
    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys = ON")
        for index, statement in enumerate(statements, start=1):
            try:
                db.execute(statement)
            except sqlite3.DatabaseError as exc:
                raise AssertionError(
                    f"Native SQL schema statement {index}/{len(statements)} "
                    f"failed: {statement[:150]!r}"
                ) from exc

        actual_tables = {
            name for (name,) in db.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            )
            if not name.startswith("sqlite_")
        }
        if actual_tables != EXPECTED_TABLES:
            raise AssertionError(f"Unexpected native schema tables: {actual_tables}")
        columns = {
            row[1] for row in db.execute("PRAGMA table_info('snapshot_import')")
        }
        if "imported_at" not in columns:
            raise AssertionError("Snapshot audit timestamp was not created.")
        if db.execute("PRAGMA foreign_keys").fetchone() != (1,):
            raise AssertionError("SQLite foreign keys not enabled.")

        db.execute(
            "INSERT INTO family_member(id,display_name,relationship,is_primary) "
            "VALUES('self','Synthetic user','Self',1)"
        )
        db.execute(
            "INSERT INTO holding(id,owner_id,display_name,asset_category,"
            "quantity_scaled,price_scaled) "
            "VALUES('holding','self','Synthetic asset','FUND',0,10000)"
        )
        db.execute(
            "INSERT INTO ledger_event(id,owner_id,holding_id,kind,event_date,"
            "quantity_delta_scaled,amount_scaled) "
            "VALUES('event','self','holding','BUY','2026-10-08',100000000,10000)"
        )
        assert_rejected(
            db, "UPDATE ledger_event SET amount_scaled = 0 WHERE id = 'event'"
        )
        assert_rejected(db, "DELETE FROM ledger_event WHERE id = 'event'")
        remaining = db.execute(
            "SELECT COUNT(*) FROM ledger_event WHERE id='event'"
        ).fetchone()[0]
        if remaining != 1:
            raise AssertionError("Immutable ledger row was modified.")
        db.execute(
            "INSERT INTO snapshot_import("
            "id,owner_id,source_sha256,as_of,is_complete"
            ") VALUES('snapshot','self',?,'2026-10-08',1)",
            ("a" * 64,),
        )
        db.execute(
            "INSERT INTO statement_position("
            "snapshot_id,owner_id,holding_id,quantity_scaled,value_scaled"
            ") VALUES('snapshot','self','holding',100000000,10000)"
        )
        if db.execute("PRAGMA integrity_check").fetchone() != ("ok",):
            raise AssertionError("Native SQLite integrity check failed.")
        if db.execute("PRAGMA foreign_key_check").fetchall():
            raise AssertionError("Native SQLite foreign keys are inconsistent.")
    print(
        f"PASS: {len(statements)} production DDL statements, "
        f"{len(actual_tables)} tables, ledger triggers and snapshot foreign keys."
    )


if __name__ == "__main__":
    main()
