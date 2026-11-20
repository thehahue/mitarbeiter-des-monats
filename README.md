# Mitarbeiter des Monats – Teamglanz

Eigenständiges Spring-Boot-Projekt nach dem Aufbau von `dino`: **Java 21, Spring Boot 4.1.2, Lombok, Spring Data JDBC und H2**. Das responsive Frontend wird direkt von Spring Boot ausgeliefert. Es benötigt keinen separaten Node-Server.

## Starten

```bash
cd mitarbeiter-des-monats
./mvnw spring-boot:run
```

Alternativ mit installiertem Maven: `mvn spring-boot:run`.

Anschließend **http://localhost:8080** öffnen. Falls Dino bereits auf Port 8080 läuft:

```bash
PORT=8081 ./mvnw spring-boot:run
```

## Bedienung

- Die Mitarbeiterliste wird beim Öffnen über `GET /api/mitarbeiter` aus H2 geladen. Die Anzahl der angezeigten Mitarbeiter richtet sich nach der API-Antwort.
- **Mitarbeiter anlegen** öffnet die Namenseingabe. Neue Mitarbeiter werden gespeichert und am Ende der Rangliste angefügt.
- Das rote **×** rechts an einer Person löscht sie sofort. Die nachfolgenden Mitarbeiter rücken auf. Bei einer ungespeicherten Reihenfolge wird vor dem Verwerfen nachgefragt.
- Am Griff rechts eine Person mit Maus oder Touch an ihren neuen Platz ziehen. Am Bildschirmrand scrollt die Seite während des Ziehens automatisch.
- Alternativ die Pfeil-Schaltflächen verwenden. Mit der Tabulatortaste den Griff fokussieren und mit **↑ / ↓** verschieben; **Pos1 / Ende** verschiebt an den Anfang bzw. das Ende. **Escape** bricht einen laufenden Ziehvorgang ab.
- Der erste Platz wird rechts hervorgehoben. Änderungen erscheinen zunächst als Vorschau.
- **Speichern** sendet die gesamte Rangliste an `POST /api/save-mitarbeiter`. **Verwerfen** stellt den zuletzt geladenen bzw. gespeicherten Stand wieder her.
- Bei einem Speicherfehler bleibt die bearbeitete Liste erhalten und kann erneut gespeichert werden.

## API

`GET /api/mitarbeiter` und `POST /api/save-mitarbeiter` verwenden dasselbe Listenformat, ohne interne Datenbank-IDs. **Ranking 0 entspricht Platz 1**; für eine Liste mit `n` Mitarbeitern reichen die Rankings von `0` bis `n - 1`. Alle Endpunkte geben die vollständige Liste nach Ranking aufsteigend sortiert zurück.

Beispiel mit einer beliebigen Teamgröße:

```json
{
  "mitarbeiter": [
    { "name": "Franz Huber", "ranking": 0 },
    { "name": "Anna Berger", "ranking": 1 },
    { "name": "Lukas Steiner", "ranking": 2 }
  ]
}
```

```bash
curl http://localhost:8080/api/mitarbeiter

# Eine vollständige Rangliste als JSON-Datei speichern und senden:
curl -X POST http://localhost:8080/api/save-mitarbeiter \
  -H 'Content-Type: application/json' \
  --data-binary @rangliste.json
```

`POST /api/save-mitarbeiter` ersetzt die vollständige Liste atomar in einer Transaktion. Jeder Eintrag benötigt einen eindeutigen, nicht leeren Namen (maximal 80 Zeichen) und ein eindeutiges ganzzahliges Ranking. Die Rankings müssen lückenlos von `0` bis `n - 1` reichen. Eine leere Liste lässt sich mit `{"mitarbeiter":[]}` speichern. Ungültige Anfragen liefern HTTP 400 und verändern die gespeicherten Daten nicht.

### Mitarbeiter anlegen

`POST /api/new-mitarbeiter` nimmt beispielsweise `{"name":"Max Meier"}` entgegen, entfernt äußere Leerzeichen und fügt den Mitarbeiter am Ende der Rangliste hinzu. Das Ranking wird vom Backend vergeben. Die Antwort mit HTTP 200 enthält die vollständige aktualisierte Liste im oben gezeigten Format. Leere, fehlende, zu lange oder doppelte Namen liefern HTTP 400; bei der Prüfung auf doppelte Namen wird Groß-/Kleinschreibung ignoriert.

```bash
curl -X POST 'http://localhost:8080/api/new-mitarbeiter' -H 'Content-Type: application/json' -d '{"name":"Max Meier"}'
```

Der Frontend-Dialog **Mitarbeiter anlegen** verwendet diesen Endpunkt und übernimmt die Antwort als gespeicherten Stand. Bei einer ungespeicherten Reihenfolge weist der Dialog darauf hin, dass sie dabei ersetzt wird. Fehler bleiben im Dialog sichtbar; Name und bisherige Rangliste bleiben erhalten. Frontend und Backend unterstützen eine dynamische Mitarbeiterzahl einschließlich einer leeren Liste.

### Mitarbeiter löschen

`POST /api/delete-mitarbeiter` nimmt `{"name":"Max Meier"}` entgegen und löscht die passende Person. Äußere Leerzeichen und Groß-/Kleinschreibung werden beim Namensvergleich ignoriert. Die übrigen Mitarbeiter behalten ihre Reihenfolge; nachfolgende Rankings werden um eins verringert, sodass sie wieder lückenlos von `0` bis `n - 1` reichen. Löschen und Nachrücken erfolgen gemeinsam in einer Transaktion.

```bash
curl -X POST 'http://localhost:8080/api/delete-mitarbeiter' -H 'Content-Type: application/json' -d '{"name":"Max Meier"}'
```

Die Antwort mit HTTP 200 enthält die vollständige aktualisierte Liste. Beim Löschen der letzten Person lautet sie `{"mitarbeiter":[]}`. Ein unbekannter Name liefert HTTP 404; ein fehlender, leerer oder zu langer Name liefert HTTP 400. Fehler verändern die gespeicherte Rangliste nicht. Der Löschbutton im Frontend übernimmt die Antwort unmittelbar als gespeicherten Stand.

## Datenbank und Aufbau

Die H2-Konsole ist unter **http://localhost:8080/h2-console** erreichbar (bei anderem Server-Port entsprechend anpassen). Zum Anmelden die JDBC-URL `jdbc:h2:file:./data/mitarbeiter`, den Benutzernamen `sa` und ein leeres Passwort verwenden.

Die H2-Datei liegt unter `data/mitarbeiter.mv.db`, relativ zum Arbeitsverzeichnis beim Start. Beim ersten Start werden Beispieldaten angelegt. Diese legen keine feste Teamgröße fest. Eine vorhandene Rangliste wird bei einem Neustart beibehalten, auch wenn sie leer gespeichert wurde. Der Initialisierungsstatus liegt in `MITARBEITER_STATUS`; die Tabelle wird auch in bestehenden Datenbanken automatisch ergänzt. Dieselbe Statuszeile serialisiert schreibende Transaktionen, damit parallele Neuanlagen unterschiedliche Rankings erhalten. Für einen frischen Beispielstand die Anwendung stoppen und das Verzeichnis `data/` löschen.

```text
src/main/java/at/bbrz/mitarbeiter/
  controller/     REST-Endpunkte und Validierungsfehler
  model/          JSON-Modelle mit Lombok und Bean Validation
  repo/           Spring-Data-JDBC-Repository
    entity/       H2-Datenmodell mit Lombok
  service/        Ranking, Transaktionen und Beispieldaten
src/main/resources/
  application.yml
  schema.sql
  static/         HTML, CSS, JavaScript und SVG-Favicon
```

Die Oberfläche verwendet Google Fonts mit lokalen Systemschriften als Fallback. Alle Funktionen und die Pokalillustration funktionieren ohne externe JavaScript- oder Bilddienste. Die Monatsanzeige zeigt den aktuellen Kalendermonat; gespeichert wird eine gemeinsame Rangliste ohne Monatsarchiv.

## Tests und JAR

```bash
./mvnw test
./mvnw package
java -jar target/mitarbeiter-des-monats-0.0.1-SNAPSHOT.jar
```

Die API-Integrationstests verwenden eine getrennte H2-In-Memory-Datenbank und prüfen unterschiedliche Teamgrößen einschließlich leerer Listen, Sortierung, Anlegen, Löschen mit Nachrücken und erneutes Laden, Namens- und Rankingvalidierung, parallele Schreibzugriffe sowie die unveränderte Datenbank bei ungültigen Anfragen.

## Browser-Test

Das Frontend lässt sich unabhängig vom Backend mit simulierten API-Antworten prüfen. Dabei wird keine Datenbank verändert:

```bash
python3 scripts/frontend-check.py --chromium-executable /usr/bin/chromium
```

Der Test benötigt Playwright (Installation siehe unten) und prüft unterschiedliche Teamgrößen, Anlegen, Fehlerfälle, Tastaturbedienung und mobile Ansichten. Ohne `--chromium-executable` verwendet er den von Playwright installierten Browser.

Das dauerhafte Testskript liegt unter `scripts/browser-check.py` und bleibt bei `./mvnw clean` erhalten. Es prüft Laden, Tastaturbedienung, Drag-and-drop mit Maus und Touch, Speichern, Anlegen über das Popup einschließlich Duplikatprüfung, Löschen über den Zeilenbutton mit Nachrücken, Fehlerbehandlung und responsive Ansichten. Es testet die vom Server ausgelieferten Dateien und verändert die Rangliste der Testinstanz.

Einmalig Playwright und Chromium in einer Python-Umgebung installieren:

```bash
python3 -m venv .venv
.venv/bin/pip install playwright
.venv/bin/python -m playwright install chromium
```

Nach einem Clean zuerst das JAR bauen und in einem Terminal eine Testinstanz mit eigener Datenbank starten:

```bash
./mvnw clean package
java -jar target/mitarbeiter-des-monats-0.0.1-SNAPSHOT.jar \
  --server.port=18080 \
  --spring.datasource.url=jdbc:h2:file:./target/browser-data/mitarbeiter
```

In einem zweiten Terminal den Browser-Test ausführen:

```bash
.venv/bin/python scripts/browser-check.py
```

Eine andere Testinstanz lässt sich mit `--base-url http://localhost:18081` auswählen. Für einen bereits installierten Browser kann beispielsweise `--chromium-executable /usr/bin/chromium` angegeben werden.

Das Skript funktioniert unabhängig vom aktuellen Arbeitsverzeichnis. Screenshots landen immer unter `target/screenshots/`; dieses Ausgabeverzeichnis wird bei Bedarf neu angelegt. Screenshots und die Testdatenbank werden durch `./mvnw clean` gelöscht, das Skript und die Python-Umgebung bleiben bestehen.
