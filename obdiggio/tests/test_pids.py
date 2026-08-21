"""Test dei decoder PID (formule SAE J1979)."""

from obdiggio.obd.pids import PIDS, get_pid


def test_rpm_decode():
    # 0x1A 0xF8 = 6904 -> /4 = 1726 rpm
    pid = get_pid(0x0C)
    assert pid.command() == "010C"
    result = pid.decode([0x1A, 0xF8])
    assert result.value == 1726.0
    assert result.unit == "rpm"


def test_coolant_temp_offset():
    pid = get_pid(0x05)
    # 0x7B = 123 -> 123 - 40 = 83°C
    assert pid.decode([0x7B]).value == 83.0


def test_speed_direct():
    assert get_pid(0x0D).decode([0x64]).value == 100.0


def test_throttle_percent():
    # 0xFF -> 100%
    assert round(get_pid(0x11).decode([0xFF]).value, 1) == 100.0
    # 0x00 -> 0%
    assert get_pid(0x11).decode([0x00]).value == 0.0


def test_maf_two_bytes():
    # ((0x01*256)+0x00)/100 = 2.56 g/s
    assert get_pid(0x10).decode([0x01, 0x00]).value == 2.56


def test_control_module_voltage():
    # ((0x30*256)+0x39)/1000 = 12.345 -> 0x3039 = 12345
    assert get_pid(0x42).decode([0x30, 0x39]).value == 12.345


def test_fuel_trim_signed():
    # A/1.28 - 100 ; 0x80=128 -> 0
    assert round(get_pid(0x06).decode([0x80]).value, 1) == 0.0


def test_missing_bytes_returns_none():
    assert get_pid(0x0C).decode([0x1A]).value is None


def test_result_str():
    assert "RPM" in str(get_pid(0x0C).decode([0x1A, 0xF8]))
