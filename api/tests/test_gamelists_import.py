from __future__ import annotations

from scripts.import_gamelists import convert_catalog_id, parse_ps1_line, parse_ps2_line


def test_convert_catalog_id_standard_case():
    assert convert_catalog_id("SLES-51716") == "SLES_517.16"


def test_convert_catalog_id_is_case_insensitive_and_trims():
    assert convert_catalog_id(" sles-51716 ") == "SLES_517.16"


def test_convert_catalog_id_rejects_wrong_digit_count():
    assert convert_catalog_id("SLES-5171") is None
    assert convert_catalog_id("SLES-517166") is None


def test_convert_catalog_id_rejects_already_filename_format():
    assert convert_catalog_id("SLES_517.16") is None


def test_convert_catalog_id_rejects_garbage():
    assert convert_catalog_id("") is None
    assert convert_catalog_id("not an id") is None


def test_parse_ps2_line_converts_and_extracts_title():
    assert parse_ps2_line("SLES-52237\t .HACK - PART 1 - INFECTION") == ("SLES_522.37", ".HACK - PART 1 - INFECTION")


def test_parse_ps2_line_skips_section_header_lines():
    assert parse_ps2_line("0-9") is None
    assert parse_ps2_line("A") is None


def test_parse_ps2_line_skips_lines_with_a_bad_id():
    assert parse_ps2_line("NOTANID\t Some Title") is None


def test_parse_ps2_line_skips_lines_with_no_title():
    assert parse_ps2_line("SLES-52237\t ") is None


def test_parse_ps1_line_extracts_id_and_title_unchanged():
    assert parse_ps1_line("SLES_031.34**007 - THE WORLD IS NOT ENOUGH") == (
        "SLES_031.34",
        "007 - THE WORLD IS NOT ENOUGH",
    )


def test_parse_ps1_line_skips_section_header_lines():
    assert parse_ps1_line("0-9") is None
    assert parse_ps1_line("A") is None


def test_parse_ps1_line_skips_lines_with_a_bad_id():
    assert parse_ps1_line("SLES-031.34**Some Title") is None  # catalog format, not filename format
    assert parse_ps1_line("NOTANID**Some Title") is None


def test_parse_ps1_line_skips_lines_with_no_title():
    assert parse_ps1_line("SLES_031.34**") is None
