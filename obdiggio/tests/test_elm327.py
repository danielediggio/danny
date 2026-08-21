"""Test del driver ELM327 usando il trasporto simulato."""

from obdiggio.ble.mock_transport import MockELM327Transport
from obdiggio.obd.elm327 import ELM327
from obdiggio.obd.pids import get_pid


def make_elm(dtcs=True):
    t = MockELM327Transport(dtcs=dtcs)
    elm = ELM327(t, timeout=2.0)
    elm.connect()
    return elm


def test_parse_hex_bytes_filters_frames():
    # frame ISO-TP e testo devono essere scartati
    data = ELM327.parse_hex_bytes("SEARCHING... 0: 41 0C 1A F8")
    assert data == [0x41, 0x0C, 0x1A, 0xF8]


def test_strip_mode_header_matches_pid():
    elm = ELM327(MockELM327Transport())
    data = elm._strip_mode_header([0x41, 0x0C, 0x1A, 0xF8], mode=0x01, pid=0x0C)
    assert data == [0x1A, 0xF8]


def test_strip_mode_header_wrong_pid():
    elm = ELM327(MockELM327Transport())
    assert elm._strip_mode_header([0x41, 0x0D, 0x64], mode=0x01, pid=0x0C) is None


def test_read_pid_rpm_from_sim():
    elm = make_elm()
    result = elm.read_pid(get_pid(0x0C))
    assert result.value is not None
    assert 700 <= result.value <= 1200  # minimo simulato


def test_read_pid_speed_zero():
    elm = make_elm()
    assert elm.read_pid(get_pid(0x0D)).value == 0.0


def test_read_dtcs_from_sim():
    elm = make_elm(dtcs=True)
    codes = [d.code for d in elm.read_dtcs()]
    assert codes == ["P0133", "P0420"]


def test_read_no_dtcs():
    elm = make_elm(dtcs=False)
    assert elm.read_dtcs() == []


def test_clear_dtcs():
    elm = make_elm()
    assert elm.clear_dtcs() is True


def test_voltage():
    elm = make_elm()
    v = elm.voltage()
    assert v is not None and 11.0 < v < 13.5
