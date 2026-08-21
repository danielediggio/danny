"""Test della decodifica DTC."""

from obdiggio.obd.dtc import DTC, decode_dtc, decode_dtc_bytes


def test_decode_p0133():
    dtc = decode_dtc(0x01, 0x33)
    assert dtc.code == "P0133"


def test_decode_all_letters():
    assert decode_dtc(0x01, 0x33).code[0] == "P"   # 00 -> P
    assert decode_dtc(0x41, 0x00).code[0] == "C"   # 01 -> C
    assert decode_dtc(0x81, 0x00).code[0] == "B"   # 10 -> B
    assert decode_dtc(0xC1, 0x00).code[0] == "U"   # 11 -> U


def test_empty_slot_is_none():
    assert decode_dtc(0x00, 0x00) is None


def test_decode_sequence_skips_empty():
    data = [0x01, 0x33, 0x04, 0x20, 0x00, 0x00]
    codes = [d.code for d in decode_dtc_bytes(data)]
    assert codes == ["P0133", "P0420"]


def test_hex_digits_preserved():
    # 0x43 0xAF -> P03AF
    assert decode_dtc(0x03, 0xAF).code == "P03AF"


def test_description_lookup():
    assert "catalizzatore" in decode_dtc(0x04, 0x20).description.lower()
    assert decode_dtc(0x12, 0x34).description  # fallback non vuoto
