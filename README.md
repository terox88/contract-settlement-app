# Contract Settlement App

Wewnętrzna aplikacja do kontroli rozliczeń umowy z generalnym wykonawcą i umów podwykonawczych.

## Stack
- Java 21
- Spring Boot 3
- Gradle
- React + Vite
- PostgreSQL
- Docker / Docker Compose

## Docelowe moduły
1. Zadania inwestycyjne
2. Budżet zadania z finansowaniem w podziale na lata
3. Kontrahenci
4. Umowa główna
3. Umowy podwykonawcze
4. Aneksy wspólne dla obu typów umów
5. Faktury GW
6. Faktury podwykonawców
7. Powiązania faktur podwykonawców z fakturami GW
8. Przelewy i alokacja płatności
9. Oświadczenia podwykonawców
10. Załączniki (wiele plików na dokument)
11. Import i parsowanie faktur XML
12. Ekran zatwierdzania/edycji danych odczytanych z XML

## Uruchomienie w Dockerze
1. Skopiuj `.env.example` do `.env`.
2. Ustaw silne hasło w `DB_PASSWORD`.
3. Uruchom:

```bash
docker compose up --build -d
```

Aplikacja będzie dostępna pod:

`http://localhost:8080`

W sieci lokalnej:

`http://ADRES_IP_SERWERA:8080`

## Dane trwałe
- PostgreSQL: Docker volume `postgres_data`
- Dokumenty: `./storage/documents`

Na serwerze firmowym warto zamienić bind mount na np.:

`/srv/settlements/documents:/data/documents`

## Ważne przed produkcją
- dodać Spring Security i użytkowników/role,
- dodać backup PostgreSQL + katalogu dokumentów,
- dodać walidację typów i rozmiaru załączników,
- dodać logowanie operacji/audyt,
- ustalić format XML faktur obsługiwany w firmie (np. KSeF/FA(3)),
- rozważyć HTTPS/reverse proxy na serwerze.

## Gradle Wrapper
Projekt jest przypięty do Gradle 8.14 i Java 21. Nie używaj globalnego `gradle` (np. Gradle 7.6).

Na Windows uruchamiaj:

```powershell
.\gradlew.bat --version
.\gradlew.bat clean build
```

albo w PowerShell także:

```powershell
.\gradlew --version
```

Pierwsze uruchomienie pobierze Gradle 8.14 automatycznie do katalogu użytkownika `.gradle`. W IntelliJ ustaw **Gradle distribution: Wrapper** oraz **Gradle JVM: Java 21**.

## Baza danych i Flyway

Schemat bazy jest zarządzany przez Flyway. Hibernate ma ustawione `ddl-auto: validate`, więc nie tworzy ani nie modyfikuje tabel samodzielnie.

Pierwsza migracja znajduje się w:

`backend/src/main/resources/db/migration/V1__initial_schema.sql`

Każda późniejsza zmiana schematu powinna dostać nową migrację, np. `V2__add_invoice_tables.sql`. Nie edytujemy migracji, która została już uruchomiona na współdzielonej/produkcyjnej bazie.


## Budżet zadania inwestycyjnego

Każde `InvestmentTask` może mieć jeden `Budget`. Budżet składa się z pozycji `BudgetYear`, dzięki czemu finansowanie można rozłożyć na wiele lat.

Przykład:

```text
2026 -> 1 500 000 PLN
2027 -> 4 000 000 PLN
2028 -> 2 500 000 PLN
Razem -> 8 000 000 PLN
```

Łączna wartość budżetu i wartość dla wskazanego roku są wyliczane, a nie przechowywane jako dodatkowe kolumny. DTO `BudgetResponse` jest przygotowane pod widok React: suma budżetu + bieżący rok, a po rozwinięciu pełna lista finansowania rocznego.

Migracja budżetu: `V2__add_task_budget.sql`.
