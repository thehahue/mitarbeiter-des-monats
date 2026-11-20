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

- Die zehn Mitarbeiter werden beim Öffnen über `GET /api/mitarbeiter` aus H2 geladen.
- Am Griff rechts eine Person mit Maus oder Touch an ihren neuen Platz ziehen. Am Bildschirmrand scrollt die Seite während des Ziehens automatisch.
- Alternativ die Pfeil-Schaltflächen verwenden. Mit der Tabulatortaste den Griff fokussieren und mit **↑ / ↓** verschieben; **Pos1 / Ende** verschiebt an den Anfang bzw. das Ende. **Escape** bricht einen laufenden Ziehvorgang ab.
- Der erste Platz wird rechts hervorgehoben. Änderungen erscheinen zunächst als Vorschau.
- **Speichern** sendet die gesamte Rangliste an `POST /api/save-mitarbeiter`. **Verwerfen** stellt den zuletzt geladenen bzw. gespeicherten Stand wieder her.
- Bei einem Speicherfehler bleibt die bearbeitete Liste erhalten und kann erneut gespeichert werden.

## API

GET und POST verwenden dasselbe Format, ohne interne Datenbank-IDs. **Ranking 0 entspricht Platz 1**, Ranking 9 entspricht Platz 10. GET und die POST-Antwort sind nach Ranking aufsteigend sortiert.

```json
{
  "mitarbeiter": [
    { "name": "Franz Huber", "ranking": 0 },
    { "name": "Anna Berger", "ranking": 1 },
    { "name": "Lukas Steiner", "ranking": 2 },
    { "name": "Sophie Wagner", "ranking": 3 },
    { "name": "Thomas Gruber", "ranking": 4 },
    { "name": "Julia Hofer", "ranking": 5 },
    { "name": "Michael Bauer", "ranking": 6 },
    { "name": "Sarah Leitner", "ranking": 7 },
    { "name": "David Fischer", "ranking": 8 },
    { "name": "Elena Mayer", "ranking": 9 }
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

POST ersetzt die vollständige Liste atomar in einer Transaktion. Es müssen genau zehn Einträge mit eindeutigen, nicht leeren Namen (maximal 80 Zeichen) und den eindeutigen ganzzahligen Rankings 0–9 vorliegen. Ungültige Anfragen liefern HTTP 400 und verändern die gespeicherten Daten nicht.

### Mitarbeiter anlegen (Frontend)

Über **Mitarbeiter anlegen** öffnet sich ein Dialog zur Namenseingabe. Er sendet `POST /api/new-mitarbeiter` mit beispielsweise `{"name":"Max Meier"}` und erwartet als Antwort die vollständige aktualisierte Liste im oben gezeigten Format. Diese Antwort wird als gespeicherter Stand übernommen. Bei einer ungespeicherten Reihenfolge weist der Dialog darauf hin, dass sie dabei ersetzt wird. Fehler bleiben im Dialog sichtbar; Name und bisherige Rangliste bleiben erhalten.

Das Frontend unterstützt beliebig viele Mitarbeiter, einschließlich einer leeren Liste; Rankings laufen von 0 bis zur Mitarbeiteranzahl minus 1. **Der neue Endpunkt und die Anpassung der bisherigen Zehnergrenze im Backend sind noch nicht implementiert.**

## Datenbank und Aufbau

Die H2-Datei liegt unter `data/mitarbeiter.mv.db`, relativ zum Arbeitsverzeichnis beim Start. Beim ersten Start werden die zehn Beispiele angelegt. Eine vorhandene Rangliste wird bei einem Neustart beibehalten. Für einen frischen Beispielstand die Anwendung stoppen und das Verzeichnis `data/` löschen.

```text
src/main/java/at/bbrz/mitarbeiter/
  controller/     REST-Endpunkte und Validierungsfehler
  entity/         H2-Datenmodell mit Lombok
  model/          JSON-Modelle mit Lombok und Bean Validation
  repo/           Spring-Data-JDBC-Repository
  service/        Ranking, Transaktionen und Beispieldaten
src/main/resources/
  application.properties
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

Die API-Integrationstests verwenden eine getrennte H2-In-Memory-Datenbank und prüfen Beispieldaten, Sortierung, Speichern und erneutes Laden sowie die unveränderte Datenbank bei ungültigen Anfragen.

## Browser-Test

Das Frontend lässt sich unabhängig vom Backend mit simulierten API-Antworten prüfen. Dabei wird keine Datenbank verändert:

```bash
python3 scripts/frontend-check.py --chromium-executable /usr/bin/chromium
```

Der Test benötigt Playwright (Installation siehe unten) und prüft unterschiedliche Teamgrößen, Anlegen, Fehlerfälle, Tastaturbedienung und mobile Ansichten. Ohne `--chromium-executable` verwendet er den von Playwright installierten Browser.

Das dauerhafte Testskript liegt unter `scripts/browser-check.py` und bleibt bei `./mvnw clean` erhalten. Es prüft Laden, Tastaturbedienung, Drag-and-drop mit Maus und Touch, Speichern, Fehlerbehandlung und responsive Ansichten. Es testet die vom Server ausgelieferten Dateien und verändert die Rangliste der Testinstanz.

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
