# Podpisywanie Windows i macOS

Stan na 24 października 2026: opiekun projektu potwierdził brak certyfikatów i konta Apple Developer. Obecne paczki są niepodpisanymi wersjami testowymi. Przygotowanie instalatorów jest ukończone; podpisywanie i sprawdzenie zaufania systemowego pozostają osobnymi warunkami wydania 1.0.

## Windows — droga dla projektu open source

1. Zapoznaj się z [SignPath Foundation](https://signpath.org/) i [warunkami dla projektów OSS](https://signpath.org/terms.html). Fundacja oferuje bezpłatne podpisywanie zakwalifikowanych projektów. Zgłoszenie podlega ich weryfikacji; nie oznacza przyznania certyfikatu.
2. Zgłoszenie powinno wskazywać publiczne repozytorium `SyntaxDevTeam/SyntaxLauncher`, licencję MIT, opis działania, publiczne wydania i proces budowania GitHub Actions. Format MSI jest już dostępny w wydaniach DEV. Weryfikacja musi objąć także warunki wszystkich składników dystrybucji; pliki gry/OptiFine nie są dołączane do instalatora.
3. Po przyjęciu projektu skonfigurujemy podpisywanie EXE oraz MSI w CI i weryfikację podpisów gotowych plików. Certyfikat Fundacji identyfikuje SignPath Foundation jako wydawcę; własny certyfikat komercyjny jest osobną możliwością, jeśli potrzebna jest własna zweryfikowana nazwa wydawcy.

Sam podpis nie jest gwarancją natychmiastowego zniknięcia wszystkich ostrzeżeń systemu. Wymagana jest osobna kontrola na czystym Windows.

## macOS — Apple Developer ID i notaryzacja

1. Załóż lub wykorzystaj własne konto Apple z uwierzytelnianiem dwuskładnikowym i zarejestruj się w [Apple Developer Program](https://developer.apple.com/help/account/membership/program-enrollment). Oficjalna opłata wynosi obecnie 99 USD rocznie; lokalna cena i podatki są wskazywane przy zakupie. Konto organizacji wymaga prawnie istniejącej organizacji i odpowiedniej weryfikacji, w tym D-U-N-S; rejestracja indywidualna używa danych osoby.
2. Po aktywacji uzyskaj [Developer ID Application](https://developer.apple.com/help/account/certificates/create-developer-id-certificates). To właściwy typ dla naszej aplikacji rozprowadzanej poza Mac App Store; Developer ID Installer dotyczy pakietów PKG, których obecna dystrybucja nie produkuje.
3. Następnie skonfigurujemy podpis całego bundla, hardened runtime, [notaryzację i weryfikację Gatekeeper](https://developer.apple.com/developer-id/), a także sprawdzenie DMG i pobranego archiwum na czystym macOS.

Zakup konta i potwierdzenie tożsamości należą do właściciela projektu. Nie wykonano rejestracji, zakupu ani zgłoszenia do SignPath w ramach tej pracy. Klucze prywatne i hasła należy przekazać do odpowiedniego magazynu sekretów CI, nigdy do repozytorium ani wiadomości w czacie.

## Kolejność włączenia podpisów

Podpis aplikacji musi powstać przed wyliczeniem manifestów aktualizacji oraz pakowaniem instalatorów. Wszystkie formaty muszą korzystać z tej samej podpisanej aplikacji. Podpis MSI/DMG i końcowe sumy plików powstają później. Zmienienie EXE/bundla po wygenerowaniu manifestu unieważniłoby jego sumy i nie jest poprawną integracją.

Do czasu uzyskania tożsamości wydawcy CI może publikować jawnie niepodpisane wersje DEV i przedprodukcyjne 0.x. Wydanie Stable 1.x lub późniejsze musi przejść końcową bramkę `release_readiness.py --enforce-production`; otwarte podpisywanie, premium i testy natywne nadal je blokują. Nie wolno używać certyfikatu samopodpisanego jako zastępstwa zaufanego podpisu publicznego.
