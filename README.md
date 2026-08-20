# danny

Progetti personali di Daniele.

## obdiggio

App **Android** per la diagnostica **OBD-II** via **Bluetooth LE**, pensata per
adattatori ELM327 come il **Vgate iCar Pro BLE 4.0**. Scritta in Python
(Kivy + python-for-android).

Il codice sta nella cartella [`obdiggio/`](obdiggio/) — vedi il suo README per
dettagli su architettura, test e build.

### Compilare l'APK

La build da sorgente richiede il download di componenti da github, quindi va
fatta in un ambiente con rete aperta. Il modo più semplice è il workflow
**Build APK** (GitHub Actions):

1. Vai nella tab **Actions** del repo.
2. Scegli **Build APK** → **Run workflow** e seleziona il branch
   `claude/obd2-program-keevsv`.
3. A fine build scarica l'APK dagli **Artifacts** della run.

Per riceverlo anche **via email**, imposta questi *Secret* del repo
(*Settings → Secrets and variables → Actions*):

| Secret | Valore |
|--------|--------|
| `MAIL_USERNAME` | il tuo indirizzo Gmail |
| `MAIL_PASSWORD` | una *app-password* Google (non la password normale) |
| `MAIL_TO` | l'indirizzo a cui inviare l'APK |
