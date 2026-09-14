"""Standalone NPS CRA statement parser.

Unit tests use synthetic atoms matching the Protean layout. Live PDF
checks run only when sample files exist locally — they are never
committed (real PII).
"""

from __future__ import annotations

from decimal import Decimal
from pathlib import Path

import pytest

from casparser.enums import FileType
from casparser.parsers.detect import file_type_from_text
from casparser.parsers.nps import (
    _asset_class,
    _classify_three_numbers,
    _parse_investment_summary,
    _parse_period,
    _parse_pran,
    _parse_scheme_preference,
    _parse_scheme_wise_table,
    _parse_subscriber_name,
    nps_statement_password,
    parse_nps,
)
from casparser.parsers.pageobj import Atom


def _a(text: str, x: float, y: float, w: float = 80.0, h: float = 8.0) -> Atom:
    return Atom(x, x + w, y, y - h, text, "Helvetica", stream_seq=0)


class TestPassword:
    def test_vikas_convention(self):
        assert nps_statement_password("Vikas Kunjbihari Shah", "1986-03-22") == "vika2203"

    def test_kiah_four_letter_first_name(self):
        assert nps_statement_password("Kiah Vikas Shah", "2018-02-07") == "kiah0702"

    def test_short_name(self):
        assert nps_statement_password("Om", "2020-01-05") == "om0501"

    def test_dmy(self):
        assert nps_statement_password("Amit", "15/08/1990") == "amit1508"

    def test_rejects_bad_dob(self):
        with pytest.raises(ValueError):
            nps_statement_password("Vikas", "March 22")


class TestDetect:
    def test_protean_title(self):
        assert file_type_from_text("NPS TRANSACTION STATEMENT\nAug 01, 2026") == FileType.NPS

    def test_tier_header(self):
        assert (
            file_type_from_text("NPS Transaction Statement for Tier I Account") == FileType.NPS
        )

    def test_does_not_steal_cams(self):
        assert file_type_from_text("CAMSCASWS watermark here") == FileType.CAMS

    def test_unknown(self):
        assert file_type_from_text("hello world") == FileType.UNKNOWN


class TestHelpers:
    def test_classify_aug_icici(self):
        triple = _classify_three_numbers(
            [Decimal("481942.06"), Decimal("6418.7052"), Decimal("75.0840")]
        )
        assert triple is not None
        value, units, nav = triple
        assert value == Decimal("481942.06")
        assert units == Decimal("6418.7052")
        assert nav == Decimal("75.0840")

    def test_classify_shuffled(self):
        triple = _classify_three_numbers(
            [Decimal("75.0840"), Decimal("481942.06"), Decimal("6418.7052")]
        )
        assert triple == (
            Decimal("481942.06"),
            Decimal("6418.7052"),
            Decimal("75.0840"),
        )

    def test_vatsalya_asset_class(self):
        assert _asset_class("NPS TRUST - A/C UTI PENSION FUND - NPS VATSALYA SCHEME DIRECT") == (
            "VATSALYA"
        )
        assert _asset_class("NPS TRUST A/C ICICI PENSION FUND SCHEME E - TIER I DIRECT") == "E"


class TestSpatialParse:
    def test_period_pran_name(self):
        atoms = [
            _a("NPS TRANSACTION STATEMENT", 395, 748),
            _a("Aug 01, 2026 To Aug 31, 2026", 415, 735, w=110),
            _a("PRAN", 35, 674, w=20),
            _a("110123981321", 142, 674, w=40),
            _a("Subscriber Name", 35, 661, w=50),
            _a("VIKAS KUNJBIHARI SHAH", 142, 661, w=80),
        ]
        period = _parse_period(atoms)
        assert period is not None
        assert period.from_ == "2026-08-01"
        assert period.to == "2026-08-31"
        assert _parse_pran(atoms) == "110123981321"
        assert _parse_subscriber_name(atoms) == "VIKAS KUNJBIHARI SHAH"

    def test_kiah_name_strips_guardian(self):
        atoms = [
            _a("Subscriber Name", 35, 661, w=50),
            _a("KIAH VIKAS SHAH (Under", 142, 661, w=90),
            _a("Guardian - VIKAS KUNJBIHARI", 142, 654, w=90),
            _a("SHAH )", 142, 647, w=40),
            _a("Address", 35, 635, w=40),
        ]
        assert _parse_subscriber_name(atoms) == "KIAH VIKAS SHAH"

    def test_scheme_wise_three_columns(self):
        # Mirrors the Aug 2026 Protean layout (page 1, scheme-wise table).
        atoms = [
            _a("Investment Details - Scheme Wise Summary", 210, 149, w=180),
            _a("NPS TRUST A/C ICICI PENSION FUND", 189, 121, w=108),
            _a("SCHEME E - TIER I DIRECT", 204, 115, w=77),
            _a("NPS TRUST A/C HDFC PENSION FUND", 318, 121, w=110),
            _a("MANAGEMENT LIMITED SCHEME C - TIER", 313, 115, w=121),
            _a("I DIRECT", 360, 109, w=25),
            _a("NPS TRUST A/C LIC PENSION FUND", 449, 121, w=104),
            _a("SCHEME G - TIER I DIRECT", 461, 115, w=78),
            _a("481942.06", 231, 94, w=28),
            _a("59635.90", 360, 94, w=25),
            _a("87550.73", 493, 94, w=25),
            _a("6418.7052", 231, 74, w=28),
            _a("1930.3580", 359, 74, w=28),
            _a("2781.1542", 491, 74, w=28),
            _a("75.0840", 234, 54, w=21),
            _a("30.8937", 362, 54, w=21),
            _a("31.4800", 495, 54, w=21),
        ]
        schemes = _parse_scheme_wise_table(atoms)
        assert len(schemes) == 3
        by_class = {s.asset_class: s for s in schemes}
        assert set(by_class) == {"E", "C", "G"}
        assert by_class["E"].units == Decimal("6418.7052")
        assert by_class["E"].nav == Decimal("75.0840")
        assert by_class["E"].value == Decimal("481942.06")
        assert by_class["C"].units == Decimal("1930.3580")
        assert by_class["G"].units == Decimal("2781.1542")
        assert by_class["E"].fund_manager == "ICICI"
        assert by_class["C"].fund_manager == "HDFC"
        assert by_class["G"].fund_manager == "LIC"
        assert by_class["E"].tier == "I"

    def test_scheme_wise_vatsalya_one_column(self):
        atoms = [
            _a("Investment Details - Scheme Wise Summary", 210, 156, w=180),
            _a("NPS TRUST - A/C UTI PENSION FUND - NPS VATSALYA", 304, 126, w=160),
            _a("SCHEME", 369, 120, w=40),
            _a("70826.85", 370, 101, w=30),
            _a("7671.5543", 368, 81, w=32),
            _a("9.2324", 374, 61, w=22),
        ]
        schemes = _parse_scheme_wise_table(atoms)
        assert len(schemes) == 1
        s = schemes[0]
        assert s.asset_class == "VATSALYA"
        assert s.units == Decimal("7671.5543")
        assert s.nav == Decimal("9.2324")
        assert s.value == Decimal("70826.85")
        assert s.fund_manager == "UTI"

    def test_empty_table_returns_nothing(self):
        atoms = [
            _a("Investment Details - Scheme Wise Summary", 210, 154, w=180),
            _a("Particulars", 66, 125, w=30),
            _a("Total Units", 35, 80, w=28),
            _a("NAV as on", 35, 60, w=28),
        ]
        assert _parse_scheme_wise_table(atoms) == []

    def test_preference_and_summary(self):
        atoms = [
            _a("Current Scheme Preference", 250, 377, w=80),
            _a("Scheme 1", 84, 315, w=26),
            _a("NPS TRUST A/C LIC PENSION FUND SCHEME G - TIER I DIRECT", 206, 315, w=180),
            _a("15.00%", 489, 315, w=20),
            _a("Scheme 2", 84, 302, w=26),
            _a("NPS TRUST A/C ICICI PENSION FUND SCHEME E - TIER I DIRECT", 204, 302, w=180),
            _a("75.00%", 488, 302, w=20),
            _a("Scheme 3", 84, 290, w=26),
            _a("NPS TRUST A/C HDFC PENSION FUND MANAGEMENT LIMITED SCHEME C - TIER I", 180, 290, w=230),
            _a("10.00%", 489, 290, w=20),
            _a("DIRECT", 287, 283, w=22),
            _a("Investment Summary", 250, 255, w=80),
            _a("(A)", 65, 200, w=10),
            _a("(B)", 217, 200, w=10),
            _a("(C)", 294, 200, w=10),
            _a("629128.69", 55, 185, w=28),
            _a("65", 141, 185, w=8),
            _a("415000.00", 207, 185, w=28),
            _a("0.00", 292, 185, w=12),
            _a("214128.69", 360, 185, w=28),
        ]
        pref = _parse_scheme_preference(atoms)
        assert len(pref) == 3
        assert pref[0][1] == Decimal("15.00")
        assert "SCHEME G" in pref[0][0].upper()
        assert pref[1][1] == Decimal("75.00")
        assert pref[2][1] == Decimal("10.00")
        assert pref[2][0].upper().endswith("DIRECT")
        value, contrib = _parse_investment_summary(atoms)
        assert value == Decimal("629128.69")
        assert contrib == Decimal("415000.00")


# Live Protean samples live outside the repo (PII). Skip on CI.
_SAMPLES = Path("/tmp/nps/NPS")
_AUG = _SAMPLES / "Vikas" / "1113981321_Aug2026.pdf"
_KIAH_MAR = _SAMPLES / "Kiah" / "1120330238_Mar2026.pdf"
_MAY = _SAMPLES / "Vikas" / "1113981321_May2026.pdf"


@pytest.mark.skipif(not _AUG.exists(), reason="NPS sample PDFs not present")
class TestLiveProteanPdfs:
    def test_aug_vikas_scheme_wise(self):
        data = parse_nps(str(_AUG), "vika2203")
        assert data.file_type == FileType.NPS.value
        assert data.nps is not None
        assert data.nps.pran == "110123981321"
        assert data.nps.nps_sp == "PROT"
        assert data.nps.value == Decimal("629128.69")
        assert data.investor_info.name.startswith("VIKAS")
        assert len(data.nps.schemes) == 3
        by_class = {s.asset_class: s for s in data.nps.schemes}
        assert by_class["E"].units == Decimal("6418.7052")
        assert by_class["C"].units == Decimal("1930.3580")
        assert by_class["G"].units == Decimal("2781.1542")
        assert by_class["E"].nav == Decimal("75.0840")
        summed = sum((s.value for s in data.nps.schemes), Decimal(0))
        assert abs(summed - data.nps.value) <= Decimal("0.05")
        assert data.statement_period.to == "2026-08-31"

    def test_mar_kiah_vatsalya(self):
        data = parse_nps(str(_KIAH_MAR), "kiah0702")
        assert data.nps is not None
        assert data.nps.pran == "110240330238"
        assert data.investor_info.name.startswith("KIAH")
        assert len(data.nps.schemes) == 1
        s = data.nps.schemes[0]
        assert s.asset_class == "VATSALYA"
        assert s.units == Decimal("7671.5543")
        assert s.nav == Decimal("9.2324")
        assert s.value == Decimal("70826.85")

    def test_may_vikas_empty_table_falls_back_to_total(self):
        data = parse_nps(str(_MAY), "vika2203")
        assert data.nps is not None
        assert data.nps.pran == "110123981321"
        assert data.nps.value == Decimal("609669.77")
        assert data.nps.schemes
        assert any("scheme-wise" in w.lower() for w in data.parse_warnings)

    def test_wrong_password(self):
        from casparser.exceptions import IncorrectPasswordError
        from casparser.parsers import read_cas_pdf

        with pytest.raises(IncorrectPasswordError):
            read_cas_pdf(str(_AUG), "wrong")
