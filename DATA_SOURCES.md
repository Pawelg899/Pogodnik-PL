# Źródła i zasady Pogodnik-PL

- **IMGW-PIB** — oficjalne dane publiczne i ostrzeżenia.
- **PERUN** — rzeczywisty system detekcji i lokalizacji wyładowań IMGW; raporty operacyjne są generowane co minutę.
- **TSP** — oficjalny model nowcastingowy IMGW do detekcji, intensywności, prawdopodobieństwa burz i ich przemieszczania; około 10 min, do 1 h, 1 km.
- **Open-Meteo** — dane modelowe używane do prognozy oraz pomocniczej warstwy „MODEL BURZ”. Nie jest ona przedstawiana jako PERUN/TSP.
- **RainViewer** — ostatnie obrazy radarowe dla warstwy RADAR.
- **OpenStreetMap** — podkład mapowy.

Pogodnik-PL nie będzie oznaczał danych jako PERUN/TSP, jeżeli pochodzą z innego źródła. IMGW potwierdza dostępność PERUN/TSP w swoich serwisach, ale publiczna dokumentacja API nie pokazuje obecnie prostego, udokumentowanego endpointu JSON/XYZ do bezpośredniego pobierania operacyjnych warstw PERUN/TSP przez aplikację mobilną. Przełączniki są przygotowane, ale nie podstawiają pod te nazwy danych zewnętrznych.

Źródło pochodzenia danych IMGW-PIB: Instytut Meteorologii i Gospodarki Wodnej — Państwowy Instytut Badawczy.
