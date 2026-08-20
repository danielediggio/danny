"""obdiggio — app Android (Kivy) per diagnostica OBD-II via Bluetooth LE.

Entry point dell'applicazione. Su desktop, in assenza di BLE, usa il simulatore
ELM327 cosi' l'interfaccia e' completamente provabile senza hardware.

Avvio desktop:  ``python main.py``
Build Android:  ``buildozer -v android debug``
"""

from __future__ import annotations

import logging
import threading

from kivy.app import App
from kivy.clock import Clock, mainthread
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.screenmanager import Screen, ScreenManager, SlideTransition
from kivy.uix.scrollview import ScrollView

from obdiggio.obd.pids import PidResult
from obdiggio.service import DASHBOARD_PIDS, OBDService
from obdiggio.obd.pids import all_pids

logging.basicConfig(level=logging.INFO)

# Palette "automotive" scura.
BG = (0.07, 0.08, 0.10, 1)
PANEL = (0.12, 0.13, 0.16, 1)
ACCENT = (0.13, 0.62, 0.87, 1)
OK = (0.20, 0.75, 0.40, 1)
WARN = (0.90, 0.35, 0.30, 1)
TEXT = (0.92, 0.93, 0.95, 1)
MUTED = (0.60, 0.63, 0.68, 1)


def _pid_by_code(code):
    return next((p for p in all_pids() if p.code == code), None)


class GaugeTile(BoxLayout):
    """Riquadro con nome PID, valore grande e unita'."""

    def __init__(self, title: str, unit: str, **kw):
        super().__init__(orientation="vertical", padding=dp(10), spacing=dp(2), **kw)
        from kivy.graphics import Color, RoundedRectangle
        with self.canvas.before:
            Color(*PANEL)
            self._bg = RoundedRectangle(radius=[dp(12)])
        self.bind(pos=self._sync, size=self._sync)

        self.value_label = Label(text="—", font_size=dp(34), bold=True, color=TEXT,
                                 halign="center", valign="middle")
        self.title_label = Label(text=title, font_size=dp(13), color=MUTED,
                                 halign="center", valign="middle")
        self.unit_label = Label(text=unit, font_size=dp(12), color=ACCENT,
                                halign="center", valign="middle")
        for lbl in (self.title_label, self.value_label, self.unit_label):
            lbl.bind(size=lbl.setter("text_size"))
        self.add_widget(self.title_label)
        self.add_widget(self.value_label)
        self.add_widget(self.unit_label)

    def _sync(self, *_):
        self._bg.pos = self.pos
        self._bg.size = self.size

    def update(self, result: PidResult):
        if result.value is None:
            self.value_label.text = "—"
            return
        v = result.value
        self.value_label.text = str(int(v)) if float(v).is_integer() else f"{v:.1f}"


class DashboardScreen(Screen):
    def __init__(self, app: "ObdiggioApp", **kw):
        super().__init__(name="dashboard", **kw)
        self.app = app
        self.tiles = {}

        root = BoxLayout(orientation="vertical")
        root.add_widget(self.app.make_header("Cruscotto"))

        grid = GridLayout(cols=2, spacing=dp(10), padding=dp(12),
                          size_hint_y=None)
        grid.bind(minimum_height=grid.setter("height"))
        for code in DASHBOARD_PIDS:
            pid = _pid_by_code(code)
            if pid is None:
                continue
            tile = GaugeTile(pid.name, pid.unit, size_hint_y=None, height=dp(110))
            self.tiles[pid.key] = tile
            grid.add_widget(tile)

        scroll = ScrollView()
        scroll.add_widget(grid)
        root.add_widget(scroll)
        root.add_widget(self.app.make_navbar())
        self.add_widget(root)

    def update_tile(self, result: PidResult):
        tile = self.tiles.get(result.pid.key)
        if tile is not None:
            tile.update(result)


class DtcScreen(Screen):
    def __init__(self, app: "ObdiggioApp", **kw):
        super().__init__(name="dtc", **kw)
        self.app = app

        root = BoxLayout(orientation="vertical")
        root.add_widget(self.app.make_header("Codici Errore (DTC)"))

        buttons = BoxLayout(size_hint_y=None, height=dp(56), spacing=dp(10),
                            padding=[dp(12), dp(6)])
        buttons.add_widget(self.app.make_button("Leggi errori", self.read_dtcs, ACCENT))
        buttons.add_widget(self.app.make_button("Cancella errori", self.clear_dtcs, WARN))
        root.add_widget(buttons)

        self.list_label = Label(text="Premi \"Leggi errori\".", color=TEXT,
                                font_size=dp(15), halign="left", valign="top",
                                padding=(dp(14), dp(10)))
        self.list_label.bind(size=self.list_label.setter("text_size"))
        scroll = ScrollView()
        scroll.add_widget(self.list_label)
        root.add_widget(scroll)
        root.add_widget(self.app.make_navbar())
        self.add_widget(root)

    def read_dtcs(self, *_):
        if not self.app.require_connection():
            return
        self.list_label.text = "Lettura in corso…"

        def work():
            try:
                dtcs = self.app.service.read_dtcs()
            except Exception as exc:  # pragma: no cover - runtime device
                self._show(f"Errore: {exc}")
                return
            if not dtcs:
                self._show("Nessun codice di errore memorizzato. ✓")
            else:
                lines = [f"[b]{d.code}[/b]  {d.description}" for d in dtcs]
                self._show("\n\n".join(lines))

        threading.Thread(target=work, daemon=True).start()

    def clear_dtcs(self, *_):
        if not self.app.require_connection():
            return
        self.list_label.text = "Cancellazione in corso…"

        def work():
            try:
                ok = self.app.service.clear_dtcs()
            except Exception as exc:  # pragma: no cover - runtime device
                self._show(f"Errore: {exc}")
                return
            self._show("Errori cancellati e spia MIL spenta. ✓" if ok
                       else "Cancellazione non confermata dall'ECU.")

        threading.Thread(target=work, daemon=True).start()

    @mainthread
    def _show(self, text: str):
        self.list_label.markup = True
        self.list_label.text = text


class ConnectScreen(Screen):
    def __init__(self, app: "ObdiggioApp", **kw):
        super().__init__(name="connect", **kw)
        self.app = app

        root = BoxLayout(orientation="vertical")
        root.add_widget(self.app.make_header("Connessione"))

        body = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(12))
        self.status = Label(text="Non connesso", color=MUTED, font_size=dp(16),
                            size_hint_y=None, height=dp(40))
        body.add_widget(self.status)

        body.add_widget(self.app.make_button("Cerca e connetti", self.connect, OK,
                                             height=dp(56)))
        body.add_widget(self.app.make_button("Modalita' demo (simulatore)",
                                             self.connect_mock, ACCENT, height=dp(56)))

        self.info = Label(
            text="Accendi il quadro, inserisci il Vgate iCar Pro nella presa\n"
                 "OBD e attiva il Bluetooth. Poi premi \"Cerca e connetti\".",
            color=MUTED, font_size=dp(13), halign="center", valign="top")
        self.info.bind(size=self.info.setter("text_size"))
        body.add_widget(self.info)
        body.add_widget(Label())  # spacer

        root.add_widget(body)
        root.add_widget(self.app.make_navbar())
        self.add_widget(root)

    def connect(self, *_):
        self.status.text = "Ricerca adattatore…"
        threading.Thread(target=self.app.do_connect, args=(False,), daemon=True).start()

    def connect_mock(self, *_):
        self.status.text = "Avvio simulatore…"
        threading.Thread(target=self.app.do_connect, args=(True,), daemon=True).start()

    @mainthread
    def set_status(self, text: str):
        self.status.text = text


class ObdiggioApp(App):
    title = "obdiggio"

    def build(self):
        from kivy.core.window import Window
        Window.clearcolor = BG

        self.service = None  # creato alla connessione
        self.sm = ScreenManager(transition=SlideTransition(duration=0.15))
        self.connect_screen = ConnectScreen(self)
        self.dashboard_screen = DashboardScreen(self)
        self.dtc_screen = DtcScreen(self)
        self.sm.add_widget(self.connect_screen)
        self.sm.add_widget(self.dashboard_screen)
        self.sm.add_widget(self.dtc_screen)
        self.sm.current = "connect"
        return self.sm

    # --- helper UI --------------------------------------------------------

    def make_header(self, title: str) -> BoxLayout:
        from kivy.graphics import Color, Rectangle
        bar = BoxLayout(size_hint_y=None, height=dp(52), padding=[dp(14), 0])
        with bar.canvas.before:
            Color(*PANEL)
            rect = Rectangle()
        bar.bind(pos=lambda *_: setattr(rect, "pos", bar.pos),
                 size=lambda *_: setattr(rect, "size", bar.size))
        lbl = Label(text=title, font_size=dp(20), bold=True, color=TEXT,
                    halign="left", valign="middle")
        lbl.bind(size=lbl.setter("text_size"))
        bar.add_widget(lbl)
        return bar

    def make_navbar(self) -> BoxLayout:
        nav = BoxLayout(size_hint_y=None, height=dp(56), spacing=dp(2))
        nav.add_widget(self.make_button("Cruscotto", lambda *_: self.go("dashboard"), PANEL))
        nav.add_widget(self.make_button("Errori", lambda *_: self.go("dtc"), PANEL))
        nav.add_widget(self.make_button("Connessione", lambda *_: self.go("connect"), PANEL))
        return nav

    def make_button(self, text, callback, color, height=None) -> Button:
        btn = Button(text=text, background_normal="", background_color=color,
                     color=TEXT, font_size=dp(15), bold=True)
        if height is not None:
            btn.size_hint_y = None
            btn.height = height
        btn.bind(on_release=callback)
        return btn

    def go(self, screen_name: str):
        self.sm.current = screen_name

    # --- logica connessione ----------------------------------------------

    def do_connect(self, force_mock: bool):
        try:
            self.service = OBDService()
            if force_mock:
                from obdiggio.ble.mock_transport import MockELM327Transport
                self.service = OBDService(transport=MockELM327Transport())
            self.service.on_update = self._on_update
            self.service.on_status = self.connect_screen.set_status

            if hasattr(self.service.transport, "scan") and not force_mock:
                self.connect_screen.set_status("Scansione BLE…")
                devices = self.service.transport.scan(timeout=8)
                obd = next((d for d in devices if d.looks_like_obd()), None)
                if obd is None:
                    self.connect_screen.set_status(
                        "Nessun adattatore OBD trovato. Riprova.")
                    return
                self.service.transport.select(obd)

            self.service.connect()
            self.service.start_polling()
            self._go_dashboard()
        except Exception as exc:
            logging.exception("Connessione fallita")
            self.connect_screen.set_status(f"Connessione fallita: {exc}")

    @mainthread
    def _go_dashboard(self):
        self.go("dashboard")

    @mainthread
    def _on_update(self, result: PidResult):
        self.dashboard_screen.update_tile(result)

    def require_connection(self) -> bool:
        if self.service is None or not self.service.is_connected:
            self.go("connect")
            self.connect_screen.set_status("Prima connettiti a un adattatore.")
            return False
        return True

    def on_stop(self):
        if self.service is not None:
            try:
                self.service.disconnect()
            except Exception:
                pass


def main():
    ObdiggioApp().run()


if __name__ == "__main__":
    main()
