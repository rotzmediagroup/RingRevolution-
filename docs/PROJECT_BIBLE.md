# DUMPLING RINGS — Master Projectplan
**Shio Studios · v1.0 · 3 werelden · 150 levels · iOS & Android**

## 1. Productvisie
Dumpling Rings is een zelfstandige premium-cozy rotate-rings-puzzelgame binnen de Dumpling Diner-visuele familie. Hergebruik bestaande, rechtmatig beschikbare Dumpling Diner-assets; maak een eigen interface, levels, audio en progression. Het bekende genreprincipe — open ringen draaien, ontwarren en uit het speelveld verwijderen — wordt gecombineerd met een warme, handgeschilderde Aziatische theehuisstijl, tastbare feedback, eerlijke puzzels, slimme boosters en respectvolle monetisatie. Kopieer geen levels, interface, naamgeving of beschermde assets van een concurrent.

**Doelen:** 150 handmatig gevalideerde levels bij release, verdeeld over drie werelden van 50; volledig offline speelbaar; soepele 60 fps op gangbare telefoons en tablets; portrait-first en tablet-landscape; geen gedwongen advertenties; eenmalige Premium/no-ads-aankoop; AdMob via feature flags, standaard uit tot configuratie en toestemming gereed zijn.

## 2. Productprincipes
1. Puzzel eerst: één duidelijke actie, direct begrijpelijk, diepgang via ringconfiguraties.
2. Ontspanning zonder verveling: zachte sfeer, optionele uitdaging, geen energielimiet of paywall.
3. Elke zet voelt goed: tactiele ringrotatie, zachte haptiek, prettige klik, vrijgave-animatie.
4. Geen oneerlijke levels: deterministische puzzelgenerator met solver, validatie en handmatige QA.
5. Eerlijke economie: boosters optioneel, verdiend of via rewarded ads; geen kunstmatige moeilijkheidspieken.
6. Privacy en lage operationele kosten: offline-first, geen accountplicht, geen eigen backend vereist voor basisgame.
7. Hergebruik bestaande Dumpling Diner-assets waar licentie en technische kwaliteit dat toelaten.

## 3. Kernmechaniek en spelregels
**Leveldoel:** maak alle ringen vrij. Iedere ring heeft een opening (gap), een rotatiehoek, een draairichting indien beperkt, een kleur/thema, een geometrische positie en relaties met overlappende ringen. Speler sleept met vinger rond de ring om hem te draaien; snapping per 15° of 30° afhankelijk van level. Als de gap geometrisch aansluit op een geldige ontsnappingsrichting én alle afhankelijkheden vrij zijn, wordt de ring verwijderd. De overige ringen reageren fysiek subtiel maar deterministisch; decoratieve animatie beïnvloedt de solver nooit.

**Interactie:** tap selecteert ring; cirkelvormige drag roteert; toegankelijk alternatief via twee grote links/rechts-knoppen; pinch zoom op drukke levels; undo en restart altijd beschikbaar. Geldige verwijdering wordt helder aangekondigd; ongeldige rotatie heeft geen straf. Geen timer in standaardmodus. Sterren op basis van optionele zetdoelen, niet op snelheid. Alle levels blijven met één ster toegankelijk.

**Leveltypes:** vrije ringen; overlappende ringen; geneste ringen; vergrendelde draaivolgorde; beperkte rotatiebogen; dubbele gaps; gekleurde matching-poorten; rotatieketens; scharnierende verbindingen; roterende obstakels. Introduceer nooit twee onbekende mechanieken in hetzelfde eerste tutoriallevel. Optionele challenge-levels mogen complexer zijn, maar zijn niet vereist om voortgang te maken.

**Solver en validatie:** representeer level als discrete toestand (ringhoek, verwijderstatus, locks, poorten). Legale acties: rotate(step), release(ring), booster(action). BFS/A* of IDA* voor kleinste oplossing; symmetry reduction, transposition table en state hashing voor complexe levels. Verifieer bij elke level: oplosbaar zonder boosters, minimaal aantal zetten binnen doelband, geen onbedoelde shortcuts, geen onmogelijke overlap, touch-targets >=44pt, voldoende contrast en geen object buiten safe area. Sla canonical solution en moeilijkheidsmetrics op; verberg oplossing voor speler tenzij hint gebruikt wordt.

## 4. Werelden en progressie
**Wereld 1 — Blossom Teahouse (levels 1–50).** Warme bamboe, zacht roze kersenbloesem, ochtendlicht, stoom uit mandjes. Leert basisrotatie en ringvrijgave. Ontgrendel Mochi Bao, Plum Dumpling en de eerste theehuisdecoraties. Moeilijkheid: beginner → gemiddeld.

**Wereld 2 — Lantern Night Market (levels 51–100).** Gouden lampionnen, indigo avondlucht, streetfoodkraampjes, lichtreflecties, speelse foodkarakters. Meer overlap, volgordepuzzels, kleurpoorten en eerste rotatieketens. Moeilijkheid: gemiddeld → gevorderd.

**Wereld 3 — Moonlit Mountain Kitchen (levels 101–150).** Bergtempel, maanlicht, zachte wolken, jade, winterbloesem, elegante gouden details. Combinaties van alle eerder geleerde mechanieken; nieuwe dubbele gaps, scharnieren en eindpuzzels. Moeilijkheid: gevorderd → expert, maar nooit op booster-aankoop ontworpen.

**Progressie:** elke wereld 5 hoofdstukken × 10 levels. Ieder tiende level is een visueel speciaal chef-level (geen verplichte tijdsdruk). Hoofdstukbeloning: cosmetische decoratie of personage. Wereldovergang: korte geïllustreerde scène. Na level 150: vrij speelbare dagelijkse puzzel uit lokaal opgeslagen seeds, sterren verbeteren, cosmetische verzamelingen. Dagelijkse puzzel werkt ook zonder netwerk en gebruikt een deterministische kalenderseed.

## 5. Volledige level-roadmap (150 levels)
De onderstaande regels zijn designbriefs, geen onbewezen speelbare layouts. De agent genereert voor ieder level een JSON-layout en solver-gevalideerde oplossing; daarna volgt handmatige UX-QA. N = ringaantal; T = doelmechaniek; D = richtwaarde voor moeilijkheid (1–10). Houd variatie in topologie, kleur, hoek, oplossingslengte en visuele compositie.

| Level | Wereld | Hoofdstuk | Ringen | Focus | D |
|---:|---|---|---:|---|---:|
| 1 | 1 | Eerste stoom | 2 | Basisrotatie; introductie | 1 |
| 2 | 1 | Eerste stoom | 2 | Basisrotatie; nieuwe topologie | 1 |
| 3 | 1 | Eerste stoom | 2 | Basisrotatie; asymmetrische plaatsing | 1 |
| 4 | 1 | Eerste stoom | 3 | Basisrotatie; kortere oplossingsroute | 1 |
| 5 | 1 | Eerste stoom | 3 | Basisrotatie; extra overlap | 1 |
| 6 | 1 | Eerste stoom | 3 | Basisrotatie; afleidende draairichting | 2 |
| 7 | 1 | Eerste stoom | 3 | Basisrotatie; geneste configuratie | 2 |
| 8 | 1 | Eerste stoom | 4 | Basisrotatie; meerstapsvolgorde | 2 |
| 9 | 1 | Eerste stoom | 4 | Basisrotatie; combinatieproef | 2 |
| 10 | 1 | Eerste stoom | 4 | Basisrotatie; chef-level / hoofdstukfinale | 2 |
| 11 | 1 | Bloesempad | 3 | Eenvoudige overlap; introductie | 2 |
| 12 | 1 | Bloesempad | 3 | Eenvoudige overlap; nieuwe topologie | 2 |
| 13 | 1 | Bloesempad | 3 | Eenvoudige overlap; asymmetrische plaatsing | 2 |
| 14 | 1 | Bloesempad | 4 | Eenvoudige overlap; kortere oplossingsroute | 2 |
| 15 | 1 | Bloesempad | 4 | Eenvoudige overlap; extra overlap | 2 |
| 16 | 1 | Bloesempad | 4 | Eenvoudige overlap; afleidende draairichting | 3 |
| 17 | 1 | Bloesempad | 4 | Eenvoudige overlap; geneste configuratie | 3 |
| 18 | 1 | Bloesempad | 5 | Eenvoudige overlap; meerstapsvolgorde | 3 |
| 19 | 1 | Bloesempad | 5 | Eenvoudige overlap; combinatieproef | 3 |
| 20 | 1 | Bloesempad | 5 | Eenvoudige overlap; chef-level / hoofdstukfinale | 3 |
| 21 | 1 | Bamboemandjes | 4 | Ontgrendelvolgorde; introductie | 3 |
| 22 | 1 | Bamboemandjes | 4 | Ontgrendelvolgorde; nieuwe topologie | 3 |
| 23 | 1 | Bamboemandjes | 4 | Ontgrendelvolgorde; asymmetrische plaatsing | 3 |
| 24 | 1 | Bamboemandjes | 5 | Ontgrendelvolgorde; kortere oplossingsroute | 3 |
| 25 | 1 | Bamboemandjes | 5 | Ontgrendelvolgorde; extra overlap | 3 |
| 26 | 1 | Bamboemandjes | 5 | Ontgrendelvolgorde; afleidende draairichting | 4 |
| 27 | 1 | Bamboemandjes | 5 | Ontgrendelvolgorde; geneste configuratie | 4 |
| 28 | 1 | Bamboemandjes | 6 | Ontgrendelvolgorde; meerstapsvolgorde | 4 |
| 29 | 1 | Bamboemandjes | 6 | Ontgrendelvolgorde; combinatieproef | 4 |
| 30 | 1 | Bamboemandjes | 6 | Ontgrendelvolgorde; chef-level / hoofdstukfinale | 4 |
| 31 | 1 | De theemeester | 4 | Geneste ringen; introductie | 3 |
| 32 | 1 | De theemeester | 4 | Geneste ringen; nieuwe topologie | 3 |
| 33 | 1 | De theemeester | 5 | Geneste ringen; asymmetrische plaatsing | 3 |
| 34 | 1 | De theemeester | 5 | Geneste ringen; kortere oplossingsroute | 4 |
| 35 | 1 | De theemeester | 5 | Geneste ringen; extra overlap | 4 |
| 36 | 1 | De theemeester | 6 | Geneste ringen; afleidende draairichting | 4 |
| 37 | 1 | De theemeester | 6 | Geneste ringen; geneste configuratie | 4 |
| 38 | 1 | De theemeester | 6 | Geneste ringen; meerstapsvolgorde | 5 |
| 39 | 1 | De theemeester | 7 | Geneste ringen; combinatieproef | 5 |
| 40 | 1 | De theemeester | 7 | Geneste ringen; chef-level / hoofdstukfinale | 5 |
| 41 | 1 | Het lentefeest | 5 | Gecombineerde basispuzzels; introductie | 4 |
| 42 | 1 | Het lentefeest | 5 | Gecombineerde basispuzzels; nieuwe topologie | 4 |
| 43 | 1 | Het lentefeest | 6 | Gecombineerde basispuzzels; asymmetrische plaatsing | 4 |
| 44 | 1 | Het lentefeest | 6 | Gecombineerde basispuzzels; kortere oplossingsroute | 5 |
| 45 | 1 | Het lentefeest | 6 | Gecombineerde basispuzzels; extra overlap | 5 |
| 46 | 1 | Het lentefeest | 7 | Gecombineerde basispuzzels; afleidende draairichting | 5 |
| 47 | 1 | Het lentefeest | 7 | Gecombineerde basispuzzels; geneste configuratie | 5 |
| 48 | 1 | Het lentefeest | 7 | Gecombineerde basispuzzels; meerstapsvolgorde | 6 |
| 49 | 1 | Het lentefeest | 8 | Gecombineerde basispuzzels; combinatieproef | 6 |
| 50 | 1 | Het lentefeest | 8 | Gecombineerde basispuzzels; chef-level / hoofdstukfinale | 6 |
| 51 | 2 | Avondlicht | 5 | Dichte overlappingen; introductie | 4 |
| 52 | 2 | Avondlicht | 5 | Dichte overlappingen; nieuwe topologie | 4 |
| 53 | 2 | Avondlicht | 5 | Dichte overlappingen; asymmetrische plaatsing | 4 |
| 54 | 2 | Avondlicht | 6 | Dichte overlappingen; kortere oplossingsroute | 4 |
| 55 | 2 | Avondlicht | 6 | Dichte overlappingen; extra overlap | 4 |
| 56 | 2 | Avondlicht | 6 | Dichte overlappingen; afleidende draairichting | 5 |
| 57 | 2 | Avondlicht | 6 | Dichte overlappingen; geneste configuratie | 5 |
| 58 | 2 | Avondlicht | 7 | Dichte overlappingen; meerstapsvolgorde | 5 |
| 59 | 2 | Avondlicht | 7 | Dichte overlappingen; combinatieproef | 5 |
| 60 | 2 | Avondlicht | 7 | Dichte overlappingen; chef-level / hoofdstukfinale | 5 |
| 61 | 2 | Lampionstraat | 5 | Kleurpoorten; introductie | 5 |
| 62 | 2 | Lampionstraat | 5 | Kleurpoorten; nieuwe topologie | 5 |
| 63 | 2 | Lampionstraat | 6 | Kleurpoorten; asymmetrische plaatsing | 5 |
| 64 | 2 | Lampionstraat | 6 | Kleurpoorten; kortere oplossingsroute | 5 |
| 65 | 2 | Lampionstraat | 6 | Kleurpoorten; extra overlap | 5 |
| 66 | 2 | Lampionstraat | 7 | Kleurpoorten; afleidende draairichting | 6 |
| 67 | 2 | Lampionstraat | 7 | Kleurpoorten; geneste configuratie | 6 |
| 68 | 2 | Lampionstraat | 7 | Kleurpoorten; meerstapsvolgorde | 6 |
| 69 | 2 | Lampionstraat | 8 | Kleurpoorten; combinatieproef | 6 |
| 70 | 2 | Lampionstraat | 8 | Kleurpoorten; chef-level / hoofdstukfinale | 6 |
| 71 | 2 | De kruidentuin | 6 | Beperkte rotatiebogen; introductie | 5 |
| 72 | 2 | De kruidentuin | 6 | Beperkte rotatiebogen; nieuwe topologie | 5 |
| 73 | 2 | De kruidentuin | 7 | Beperkte rotatiebogen; asymmetrische plaatsing | 5 |
| 74 | 2 | De kruidentuin | 7 | Beperkte rotatiebogen; kortere oplossingsroute | 6 |
| 75 | 2 | De kruidentuin | 7 | Beperkte rotatiebogen; extra overlap | 6 |
| 76 | 2 | De kruidentuin | 8 | Beperkte rotatiebogen; afleidende draairichting | 6 |
| 77 | 2 | De kruidentuin | 8 | Beperkte rotatiebogen; geneste configuratie | 6 |
| 78 | 2 | De kruidentuin | 8 | Beperkte rotatiebogen; meerstapsvolgorde | 7 |
| 79 | 2 | De kruidentuin | 9 | Beperkte rotatiebogen; combinatieproef | 7 |
| 80 | 2 | De kruidentuin | 9 | Beperkte rotatiebogen; chef-level / hoofdstukfinale | 7 |
| 81 | 2 | De wokmeester | 6 | Rotatieketens; introductie | 6 |
| 82 | 2 | De wokmeester | 6 | Rotatieketens; nieuwe topologie | 6 |
| 83 | 2 | De wokmeester | 7 | Rotatieketens; asymmetrische plaatsing | 6 |
| 84 | 2 | De wokmeester | 7 | Rotatieketens; kortere oplossingsroute | 7 |
| 85 | 2 | De wokmeester | 7 | Rotatieketens; extra overlap | 7 |
| 86 | 2 | De wokmeester | 8 | Rotatieketens; afleidende draairichting | 7 |
| 87 | 2 | De wokmeester | 8 | Rotatieketens; geneste configuratie | 7 |
| 88 | 2 | De wokmeester | 8 | Rotatieketens; meerstapsvolgorde | 8 |
| 89 | 2 | De wokmeester | 9 | Rotatieketens; combinatieproef | 8 |
| 90 | 2 | De wokmeester | 9 | Rotatieketens; chef-level / hoofdstukfinale | 8 |
| 91 | 2 | Middernachtfeest | 7 | Poorten en ketens combineren; introductie | 6 |
| 92 | 2 | Middernachtfeest | 7 | Poorten en ketens combineren; nieuwe topologie | 6 |
| 93 | 2 | Middernachtfeest | 8 | Poorten en ketens combineren; asymmetrische plaatsing | 6 |
| 94 | 2 | Middernachtfeest | 8 | Poorten en ketens combineren; kortere oplossingsroute | 7 |
| 95 | 2 | Middernachtfeest | 8 | Poorten en ketens combineren; extra overlap | 7 |
| 96 | 2 | Middernachtfeest | 9 | Poorten en ketens combineren; afleidende draairichting | 7 |
| 97 | 2 | Middernachtfeest | 9 | Poorten en ketens combineren; geneste configuratie | 7 |
| 98 | 2 | Middernachtfeest | 9 | Poorten en ketens combineren; meerstapsvolgorde | 8 |
| 99 | 2 | Middernachtfeest | 10 | Poorten en ketens combineren; combinatieproef | 8 |
| 100 | 2 | Middernachtfeest | 10 | Poorten en ketens combineren; chef-level / hoofdstukfinale | 8 |
| 101 | 3 | Maanpoort | 7 | Complexe volgordes; introductie | 6 |
| 102 | 3 | Maanpoort | 7 | Complexe volgordes; nieuwe topologie | 6 |
| 103 | 3 | Maanpoort | 7 | Complexe volgordes; asymmetrische plaatsing | 6 |
| 104 | 3 | Maanpoort | 8 | Complexe volgordes; kortere oplossingsroute | 6 |
| 105 | 3 | Maanpoort | 8 | Complexe volgordes; extra overlap | 6 |
| 106 | 3 | Maanpoort | 8 | Complexe volgordes; afleidende draairichting | 7 |
| 107 | 3 | Maanpoort | 8 | Complexe volgordes; geneste configuratie | 7 |
| 108 | 3 | Maanpoort | 9 | Complexe volgordes; meerstapsvolgorde | 7 |
| 109 | 3 | Maanpoort | 9 | Complexe volgordes; combinatieproef | 7 |
| 110 | 3 | Maanpoort | 9 | Complexe volgordes; chef-level / hoofdstukfinale | 7 |
| 111 | 3 | Jadepaviljoen | 7 | Dubbele openingen; introductie | 7 |
| 112 | 3 | Jadepaviljoen | 7 | Dubbele openingen; nieuwe topologie | 7 |
| 113 | 3 | Jadepaviljoen | 8 | Dubbele openingen; asymmetrische plaatsing | 7 |
| 114 | 3 | Jadepaviljoen | 8 | Dubbele openingen; kortere oplossingsroute | 7 |
| 115 | 3 | Jadepaviljoen | 8 | Dubbele openingen; extra overlap | 7 |
| 116 | 3 | Jadepaviljoen | 9 | Dubbele openingen; afleidende draairichting | 8 |
| 117 | 3 | Jadepaviljoen | 9 | Dubbele openingen; geneste configuratie | 8 |
| 118 | 3 | Jadepaviljoen | 9 | Dubbele openingen; meerstapsvolgorde | 8 |
| 119 | 3 | Jadepaviljoen | 10 | Dubbele openingen; combinatieproef | 8 |
| 120 | 3 | Jadepaviljoen | 10 | Dubbele openingen; chef-level / hoofdstukfinale | 8 |
| 121 | 3 | Wolkentrap | 8 | Scharnierende verbindingen; introductie | 7 |
| 122 | 3 | Wolkentrap | 8 | Scharnierende verbindingen; nieuwe topologie | 7 |
| 123 | 3 | Wolkentrap | 9 | Scharnierende verbindingen; asymmetrische plaatsing | 7 |
| 124 | 3 | Wolkentrap | 9 | Scharnierende verbindingen; kortere oplossingsroute | 7 |
| 125 | 3 | Wolkentrap | 9 | Scharnierende verbindingen; extra overlap | 7 |
| 126 | 3 | Wolkentrap | 10 | Scharnierende verbindingen; afleidende draairichting | 8 |
| 127 | 3 | Wolkentrap | 10 | Scharnierende verbindingen; geneste configuratie | 8 |
| 128 | 3 | Wolkentrap | 10 | Scharnierende verbindingen; meerstapsvolgorde | 8 |
| 129 | 3 | Wolkentrap | 11 | Scharnierende verbindingen; combinatieproef | 8 |
| 130 | 3 | Wolkentrap | 11 | Scharnierende verbindingen; chef-level / hoofdstukfinale | 8 |
| 131 | 3 | Sterrenkeuken | 8 | Alle mechanieken combineren; introductie | 8 |
| 132 | 3 | Sterrenkeuken | 8 | Alle mechanieken combineren; nieuwe topologie | 8 |
| 133 | 3 | Sterrenkeuken | 9 | Alle mechanieken combineren; asymmetrische plaatsing | 8 |
| 134 | 3 | Sterrenkeuken | 9 | Alle mechanieken combineren; kortere oplossingsroute | 8 |
| 135 | 3 | Sterrenkeuken | 10 | Alle mechanieken combineren; extra overlap | 8 |
| 136 | 3 | Sterrenkeuken | 10 | Alle mechanieken combineren; afleidende draairichting | 9 |
| 137 | 3 | Sterrenkeuken | 11 | Alle mechanieken combineren; geneste configuratie | 9 |
| 138 | 3 | Sterrenkeuken | 11 | Alle mechanieken combineren; meerstapsvolgorde | 9 |
| 139 | 3 | Sterrenkeuken | 12 | Alle mechanieken combineren; combinatieproef | 9 |
| 140 | 3 | Sterrenkeuken | 12 | Alle mechanieken combineren; chef-level / hoofdstukfinale | 9 |
| 141 | 3 | De laatste stoom | 9 | Meesterpuzzels en finale; introductie | 8 |
| 142 | 3 | De laatste stoom | 9 | Meesterpuzzels en finale; nieuwe topologie | 8 |
| 143 | 3 | De laatste stoom | 10 | Meesterpuzzels en finale; asymmetrische plaatsing | 8 |
| 144 | 3 | De laatste stoom | 10 | Meesterpuzzels en finale; kortere oplossingsroute | 9 |
| 145 | 3 | De laatste stoom | 10 | Meesterpuzzels en finale; extra overlap | 9 |
| 146 | 3 | De laatste stoom | 11 | Meesterpuzzels en finale; afleidende draairichting | 9 |
| 147 | 3 | De laatste stoom | 11 | Meesterpuzzels en finale; geneste configuratie | 9 |
| 148 | 3 | De laatste stoom | 11 | Meesterpuzzels en finale; meerstapsvolgorde | 10 |
| 149 | 3 | De laatste stoom | 12 | Meesterpuzzels en finale; combinatieproef | 10 |
| 150 | 3 | De laatste stoom | 12 | Meesterpuzzels en finale; chef-level / hoofdstukfinale | 10 |

## 6. Visuele stijl en assethergebruik
**Art direction:** warme premium 2D/2.5D hand-painted Aziatische dumplingwereld, hout en bamboe, lantern lighting, zachte bloom, subtiele depth-of-field, kersenbloesem, zichtbare stoom en bescheiden particles. Ringen blijven scherp leesbaar tegen zachte achtergronden; geen overdadige effecten tijdens lastige puzzels. Iedere wereld krijgt een uniek kleurenpalet, soundtrack en unlockbare decoraties. Gebruik bestaande Dumpling Diner-character- en food-sprites, mits de vorm en licentie geschikt zijn. Hergebruik ook stoom, sparkles, restaurantprops en geluiden waar passend. Maak nieuwe ringmaterialen (sesamdeeg, matcha, biet, ube, goud), open-gap-varianten, rotatie- en vrijgave-animaties, UI-iconen en wereldkaarten. Nieuwe spritesheets op transparante achtergrond met ruime niet-overlappende padding, atlasgeneratie en consistente schaal.

**Animaties:** selectie = zachte squash/glow; rotatie = subtiele tactile ticks; correcte uitlijning = lichte highlight; vrijgave = bevredigende pop en dumpling-sprong naar bamboemand; hoofdstukvoltooiing = korte confetti van bloesemblaadjes. Ondersteun Reduce Motion en effectkwaliteit Low/Medium/High. Vermijd flashing en informatie die alleen via kleur wordt overgebracht.

## 7. UX en schermen
Startscherm: Play/Continue, wereldkaart, verzameling, instellingen, premium. Wereldkaart: drie scrollbare geïllustreerde locaties met 5 hoofdstukken per wereld; vergrendelde hoofdstukken tonen vooraf hun volgende mechanic. Levelscherm: bovenaan wereld/level, optioneel sterren-zetdoel en pauze; centraal groot responsief puzzelveld; onderaan undo, hint, shuffle/assist en restart. Boosterknoppen tonen voorraad en optioneel rewarded alternatief. Na voltooiing: directe positieve feedback, sterren, verdiende zachte valuta, cosmetische unlocks, Next Level; optionele rewarded x2 alleen op expliciete tap. Tablet: speelveld centraal, progressie/boosterpaneel zijdelings; landschap en portrait op tablets, portrait-first telefoon. Respecteer notch, dynamic island, gesture bars, schaalbare tekst en screenreaderlabels.

## 8. Boosters en economie
- **Hint / Chopstick:** markeert één productieve ring en richting zonder de zet uit te voeren.
- **Steam Peek:** toont 3 seconden een ghost-overlay van de volgende twee optimale stappen.
- **Chef's Hand:** voert één geldige oplossingsstap uit, inclusief animatie.
- **Fresh Start:** reset het level; altijd gratis, geen advertentie.
- **Undo:** onbeperkt gratis; nooit als monetisatiehefboom gebruiken.

Geef een startvoorraad van 3 Hint, 2 Steam Peek, 1 Chef's Hand; hoofdstukbeloningen vullen beperkt aan. Verdien dumplingmunten via levels en optionele dagelijkse uitdagingen; besteed uitsluitend aan boosters en cosmetica. Richtprijzen: Hint 60, Steam Peek 100, Chef's Hand 180; na echte playtesting afstemmen. Nooit levels moeilijker maken om boosterconsumptie af te dwingen. Bij nul voorraad blijft iedere puzzel gratis oplosbaar. Geen betaalde lootboxes of ondoorzichtige kansmechanieken.

## 9. Monetisatie — AdMob en Premium
**Filosofie:** geen advertentie bij starten, tijdens een puzzel of direct na verlies. Geen lege bannerplekken na Premium. Geen banner op het speelveld. Volledig offline speelbaar. Advertenties zijn aanvullend, niet de primaire progression-loop.

**Rewarded ads:** expliciete keuze voor één Hint, Steam Peek of een optionele x2-muntenbeloning na level. Geef de beloning alleen na geverifieerde rewarded completion; herstel correct bij achtergrondwissel, ad load failure of netwerkuitval. Geen straf als de speler weigert. Limiteer aanbod om spam te vermijden.

**Interstitial:** alleen indien expliciet geactiveerd na testfase; hoogstens 1 na 4–6 voltooide levels én minstens 8 minuten sinds vorige interstitial; nooit tijdens tutorial (levels 1–10), boss/chef-finale, eerste sessie, direct na een aankoop of als de gebruiker Premium heeft. Toon uitsluitend tussen schermen, nooit tijdens actieve interactie. Default feature flag OFF; bij twijfel volledig weglaten.

**Banner:** default OFF; uitsluitend op menu/wereldkaart als design er ruimte voor maakt. Na Premium wordt de advertentiecontainer volledig verwijderd en layout reflowt automatisch. Geen lege witte of grijze vlakken.

**Premium:** eenmalige niet-vervallende no-ads IAP; verwijdert banners en interstitials en biedt cosmetische bonus. Rewarded ads blijven uitsluitend optioneel als de speler ze zelf wil gebruiken; bied Premium-gebruikers liever een beperkte dagelijkse gratis boosterclaim zonder advertenties. Geen abonnementen. Configureer prijzen lokaal in App Store Connect / Play Console; geen hardcoded prijsstrings. Restore Purchases; platformgevalideerde aankoopstatus en correcte offline cache. Geen dark patterns.

**Technische voorbereiding:** AdMob-app-ID's en ad-unit-ID's via build config/remote config; nooit productie-IDs in tests. Development gebruikt uitsluitend officiële test-ID's. Consent flow via Google UMP vóór het laden van gepersonaliseerde ads; EEA/UK privacykeuzes, leeftijdsgeschikte behandeling, ATT op iOS waar vereist, privacy policy, data-safety-formulieren, app-ads.txt, SKAdNetwork/attribution en store disclosures controleren tegen actuele platformvereisten bij release. Geen advertenties of tracking voor spelers waarvoor toestemming ontbreekt als dat wettelijk vereist is. AdsManager met consent state, cooldown, cap, placement, preload, retry, no-fill fallback en idempotente beloning. PremiumStatus is één centrale bron van waarheid; wijzigingen direct reflecteren in alle schermen.

## 10. Architectuur en dataschema
Gebruik bestaande Dumpling Diner-engine/framework indien technisch zinvol; anders een lichte cross-platform 2D-engine. Scheid game logic volledig van rendering en monetisatie. Modules: RingGeometry, RotationController, Collision/ReleaseRules, LevelLoader, Solver, HintEngine, Progression, Economy, AssetCatalog, AudioHaptics, SaveStore, AdsManager, PurchaseManager, ConsentManager, Analytics opt-in, DailyPuzzle. Geen backend voor launch nodig. Versleutel of onderteken alleen waar nuttig; lokale progressie blijft beschikbaar zonder account. Ondersteun optionele OS-cloudsave indien al beschikbaar, met duidelijke conflictstrategie.

**Level JSON minimaal:** id, world, chapter, mechanicTags, ringDefinitions[{id,center,radius,thickness,gapStart,gapWidth,initialAngle,rotationStep,allowedArc,color,material}], blockers, portals, dependencies, winCondition, parMoves, difficulty, canonicalSolution, generatorSeed, schemaVersion. Voor dubbele gaps ondersteunt gapSegments[]. CanonicalSolution en solvermetadata kunnen in buildtijd naar een apart QA-artifact, niet noodzakelijk in client. De agent documenteert de geometrische betekenis van ‘vrij’ ondubbelzinnig vóór het produceren van levels.

**Opslag:** local progress per level (completed, bestMoves, stars), wallet, boosterInventory, unlockedCosmetics, selectedTheme, settings, premiumEntitlement, dailySeedHistory, lastRewardClaim. Migraties op schemaVersion, atomaire writes, corruptieherstel en offline-safe purchases. Geen persoonlijke gegevens nodig voor basisgame.

## 11. Geluid en engagement
Drie adaptieve, kalme muziekthema's, één per wereld, plus korte chef-finalevariaties. Geluid: zacht deeg/plop, houtklik, keramiek, stoom en lichte belletjes. Separate muziek/SFX/haptiek toggles, onthoud instellingen. Engagement zonder agressieve FOMO: dagelijkse lokale puzzel, optionele streak zonder verlies van verdiende beloningen, cosmetische dumplingverzameling, sterrenherkansing, hoofdstukscènes, subtiele verrassingsanimaties en vrije wereldherbezoeken. Pushnotificaties standaard uit; geen verplicht account of social sharing.

## 12. Levelproductie en QA
Bouw eerst een in-engine level-editor met ringplaatsing, rotatiegaps, blockers, dependencyvisualisatie, solve-preview, difficulty estimate en JSON-export. Maak 150 echte layouts volgens de roadmap, niet slechts 150 levelnamen. Elke layout moet uniek zijn op topologie, openingshoeken en oplossingspad; detecteer duplicates met canonical state hashes. Run automatische solver op alle 150 zonder boosters; rapporteer shortest path, branching factor, dead ends, gemiddelde oplossingslengte, reset/undo-test en accessibility-check. Test met mensen op verschillende niveaus; pas parMoves en moeilijkheid aan. Tutoriallevels 1–10 handmatig zorgvuldig cureren. Laat spelers bij vastlopen een gratis contextuele uitleg krijgen.

## 13. Implementatie in fasen
**Fase A — Audit & foundation:** inspecteer bestaande Dumpling Diner-repo/assets en platformarchitectuur, leg herbruikbare assets vast, definieer regels en bouw een verticale slice van 10 levels in wereld 1 met correcte solver.

**Fase B — Productiesystemen:** wereldkaart, saves, 4 boosters, UI, audio/haptiek, assetatlassen, iPhone/iPad/Android/tablet-layouts, level-editor en generator.

**Fase C — Content:** bouw en valideer 50 levels per wereld, alle 15 hoofdstukscènes/chef-levels, drie omgevingen en beloningen. Houd visuele en moeilijkheids-QA per batch van tien.

**Fase D — Monetisatie:** Premium IAP, UMP-consent, AdMob-integratie met officiële test-IDs en default-OFF flags, reward-transaction tests, restore/offline-tests. Productieadvertenties pas na accountconfiguratie, storebeleid-check en expliciete releasebeslissing activeren.

**Fase E — Release:** closed beta, device matrix, crash/ANR, performance, storemetadata, screenshots, privacy policy, leeftijdsclassificatie, app-ads.txt, aankoopherstel, offline regressie, accessibility, localization NL/EN/DE (later extra talen).

## 14. Definition of Done / acceptatiecriteria
- 150 unieke, gespeelde én solver-gevalideerde levels; geen enkele vereist boosters.
- Alle drie werelden met onderscheidende achtergrond, muziek, UI-accenten en 5 hoofdstukken.
- Volledig offline te starten en uit te spelen, inclusief dagelijkse lokale puzzels.
- Correcte voortgang, geen dubbele rewarded grants en betrouwbare IAP-restore.
- Premium verwijdert alle niet-vrijwillige advertenties én hun lege UI-ruimte.
- Consent correct vóór advertentieladen; productie-IDs niet in testbuilds.
- Scherpe, responsieve bediening op telefoon/tablet; alternatieve rotatieknoppen en Reduce Motion.
- Geen crash bij achtergrondwissel, onderbroken aankoop, geen netwerk of low memory.
- 60 fps doel op ondersteunde apparaten; degradeer decoratieve effecten waar nodig.
- Documentatie: README, level-schema, solver-spec, asset-manifest, ad-config checklist, testplan en releasechecklist.

## 15. One-shot opdracht voor de coding agent
Implementeer Dumpling Rings end-to-end op basis van dit document. Begin met een audit van de bestaande codebase en Dumpling Diner-assets; hergebruik compatibele componenten en visuele assets zonder bestaande games te beschadigen. Definieer de formele ring- en vrijgaveregels, implementeer een deterministische solver, bouw de game en genereer vervolgens alle 150 unieke, speelbare en automatisch gevalideerde levels. Lever volledige offline progression, 3 werelden, 15 hoofdstukken, UI/UX, animaties, audio, boosters, cosmetica, Premium-IAP en een AdMob/UMP-integratie die veilig voorbereid maar standaard uitgeschakeld is. Gebruik uitsluitend test-advertentie-ID's totdat productieconfiguratie is aangeleverd. Verifieer alle acceptatiecriteria met geautomatiseerde tests en device smoke tests; rapporteer uitsluitend echte testresultaten en expliciete blockers. Vraag alleen om externe secrets, storeaccounts of handmatige publicatieacties die onmogelijk uit de repository af te leiden zijn. Gebruik eigen premium art direction; kopieer geen beschermde assets of levelontwerpen van andere games.

---
# DEEL II — UITVOERBARE PROJECTBIJBEL (v2.0)
**Status:** dit deel preciseert het masterplan. Bij tegenstrijdigheid geldt Deel II voor technische uitvoering; de 150-level-roadmap uit Deel I blijft de inhoudelijke bron. Alle onderstaande taken behoren tot dezelfde one-shot-opdracht.

## 16. Agentmandaat en werkvolgorde
De agent moet de bestaande repository en beschikbare Dumpling Diner-assetmappen eerst inspecteren; nooit aannemen dat paden, API-keys, betaalaccounts of een engine aanwezig zijn. Maak `docs/AUDIT.md` met inventaris, licentie/herkomst, bestandsformaten, bestaande tooling, platformtargets en risicopunten. Als er al een productiegeschikte game-engine is: gebruik die. Bij greenfield: kies Godot 4.x met GDScript voor gameplay en platformplugins voor ads/IAP, tenzij een aanwezige Unity- of Flutter-gamecodebase aantoonbaar minder migratierisico heeft. Leg de keuze vast in een ADR. Zet een schone, reproduceerbare build op; maak geen wijzigingen aan de originele Dumpling Diner-code tenzij expliciet gedeelde componenten veilig via aparte module worden geëxtraheerd.

**Niet onderhandelbaar:** bouw echte werkende software; geen nepknoppen, placeholderlevels die als voltooid worden gemarkeerd, verzonnen testresultaten of ongeteste SDK-claims. Maak een todo/checkpointlog (`docs/IMPLEMENTATION_STATUS.md`) die na elke mijlpaal wordt bijgewerkt. Ga autonoom door naar volgende mijlpaal zolang geen externe credentials of publicatiehandelingen vereist zijn. Een ontbrekende API-key mag assetgeneratie niet blokkeren: gebruik bestaande assets of lokaal gegenereerde development-placeholder en markeer die zichtbaar in het assetmanifest; rapporteer wat nog ontbreekt. Publiceer niet zonder menselijke goedkeuring.

## 17. Definitieve spelregels en formele puzzelsemantiek
De speler ziet een 2D top-down speelveld met 2–10 gedeeltelijk open, visueel verweven ringen. Een ring is een cirkelboog met één of meer gaps. De speler roteert een geselecteerde ring om zijn middelpunt in discrete stappen (standaard 30°, optioneel 15° voor expertlevels). De mechanische speltoestand bestaat alleen uit ringhoeken, actieve/verwijderde ringen, vaste obstructies, schakelaarstatus, slotstatus en eventuele poortstanden. Decoratieve deformatie en particles zijn nooit onderdeel van de logica.

**Cruciale implementatiebeslissing:** gebruik een *deterministisch topologisch ontsnappingsmodel*, niet een willekeurige physics-engine die onbetrouwbaar bepaalt of ringen elkaar raken. Iedere ring heeft één of meer `exitConstraints`: een gedefinieerde richting/hoeksector waarin zijn opening moet liggen, plus een expliciete verzameling `blockingRingIds` die eerst verwijderd of in een compatibele stand gezet moeten zijn. Een ring mag alleen vrijkomen als alle actieve exitConstraints voldaan zijn en de release-animatie geen verboden kruising suggereert. Het model wordt door de level-editor grafisch gevisualiseerd met een `constraint overlay` die uitsluitend voor ontwikkelaars zichtbaar is. Geometrievalidator verifieert dat de zichtbare ring/gap/overlap representatie overeenkomt met de logische constraints. Als een level geometrisch niet eerlijk te representeren is, wijs het af. Ringen mogen niet simpelweg verdwijnen omdat de hoek overeenkomt terwijl hun getekende uitweg fysiek onmogelijk oogt.

Een **zet** is één afgeronde rotatie-interactie die minimaal één stap wijzigt, één expliciete release (indien release niet automatisch is) of één boosteractie. Een doorlopende drag die over meerdere stappen draait telt als één zet; de solver krijgt een equivalente `rotateTo(angle)`-actie met dezelfde kosten. Undo en restart zijn gratis en tellen niet mee. Na geldige release wordt de ring automatisch verwijderd na een korte animatie; de logische toestand wordt onmiddellijk en atomair vastgelegd. De speler mag tijdens de animatie geen dubbele release activeren. Bij ongeldige stand gebeurt niets behalve zachte feedback; geen straf of verborgen timer.

**Tutorial 1–10:** (1) één ring draaien, (2) gap en uitweg begrijpen, (3) twee ringen en volgorde, (4) vrije rotatie, (5) blokkade herkennen, (6) undo, (7) restart, (8) eenvoudige keten, (9) hint vrijwillig gebruiken, (10) kleine finale zonder gedwongen booster. Introduceer nieuwe mechanieken later telkens met één duidelijk interactief tutoriallevel, gevolgd door oefenlevels en pas daarna combinaties.

## 18. Leveldata en contentproductiecontract
Alle 150 campagnelevels worden als afzonderlijke, version-controlled bestanden opgeslagen in `content/levels/world_01/level_001.json` t/m `world_03/level_150.json`; daarnaast `content/worlds.json`, `content/chapters.json`, `content/cosmetics.json` en `content/economy.json`. Ieder level heeft een stabiele ID, expliciete starttoestand, zichtbare ringgeometrie, constraints, doel, mechanicTags, onboarding-cues, moeilijkheidsdoel, parMoves, reward en deterministische generatorSeed. Geen runtime-afhankelijkheid van online levelgeneratie.

```json
{
  "schemaVersion": 2,
  "id": "L001",
  "worldId": "blossom_teahouse",
  "chapter": 1,
  "seed": 1001,
  "mechanicTags": ["single_ring", "basic_release"],
  "rings": [{
    "id": "r1", "center": [0.5, 0.5], "radius": 0.19,
    "thickness": 0.055, "gaps": [{"startDeg": 0, "widthDeg": 60}],
    "initialAngleDeg": 120, "stepDeg": 30,
    "rotationAllowed": "both", "materialId": "dumpling_dough_pink",
    "exitConstraints": [{"exitAngleDeg": 0, "toleranceDeg": 15,
      "blockingRingIds": [], "requiredSwitchIds": []}]
  }],
  "obstacles": [], "switches": [], "portals": [],
  "winCondition": "remove_all_rings", "parMoves": 1,
  "reward": {"coins": 20}, "tutorialCueId": "rotate_first_ring"
}
```

Het voorbeeld is schema-illustratie; de generator/solver moet vaststellen dat het voorbeeld aan de eigen exacte hoek- en zetsemantiek voldoet voordat het als speelbaar level wordt gebruikt. De campagne-indeling is exact: wereld 1 = L001–L050, wereld 2 = L051–L100, wereld 3 = L101–L150. Per wereld vijf hoofdstukken van tien levels; level 10 van ieder hoofdstuk is een opvallend maar eerlijk chef-level. Gebruik de specifieke levelbeschrijvingen in §5 als contentbrief; waar die slechts een thema of moeilijkheid noemt, produceer een volledige JSON-layout met unieke ringposities, constraints, optimale oplossing en tutorialmetadata. Controleer per hoofdstuk de variatie in topologie, openingshoeken, volgordepatronen en visuele compositie.

**Generator:** begin bij een opgeloste, volledig verwijderbare configuratie; construeer terugwaarts door ringen/constraints toe te voegen en hoeken te verdraaien; verwerp ongeldige of triviale uitkomsten. **Solver:** bouw een canonieke hash van alle relevante toestand, gebruik BFS/A* of IDA* en leg optimale of aantoonbaar begrensde oplossing, bezochte toestanden, branching factor, tijd en unieke topologiehash vast. Maak `tools/validate_levels` en `tools/report_levels`, met machineleesbaar JSON-rapport en een leesbaar HTML/Markdown-overzicht. Hard fail CI als een campagnelevel ontbreekt, niet oplosbaar is zonder booster, duplicate topology heeft zonder expliciete variatie-reden, ongeldige dependencies bevat of een tutorialmechaniek gebruikt vóór introductie.

## 19. Moeilijkheidscurve en beloningsbalans
| Levels | Doel | Ringen (richtlijn) | Nieuwe complexiteit |
|---|---|---:|---|
| 1–10 | direct begrijpen | 1–3 | basisrotatie, volgorde, undo |
| 11–30 | ontspannen flow | 2–5 | overlap, nesting, eenvoudige sloten |
| 31–50 | eerste echte puzzels | 3–6 | gecombineerde dependencies, chef-levels |
| 51–70 | verdieping | 3–6 | kleurpoorten, beperkte rotatie |
| 71–90 | tactische puzzels | 4–8 | ketens, meerdere uitwegen |
| 91–100 | marktfinale | 5–8 | samengestelde constraints |
| 101–120 | expertintro | 4–8 | dubbele gaps, scharnieren |
| 121–140 | meesterschap | 5–10 | gecombineerde systemen |
| 141–150 | finale | 6–10 | lange maar begrijpelijke oplossingsketens |

Deze aantallen zijn richtlijnen, geen excuus voor drukke of onleesbare layouts. Een level mag eenvoudiger worden als dat de mobiele bediening verbetert. Meet echte moeilijkheid via optimale zetten, branching factor, dead-end-ratio en playtestduur; gebruik niet alleen ringaantal. Ontwerp parMoves pas nadat de solver een minimale oplossing heeft gevonden. 1 ster = voltooid, 2 = voltooid binnen ruime zetgrens, 3 = binnen parMoves. Geen sterrenvereiste om verder te gaan. Geef basiscoins voor ieder eerste levelcompletion, bescheiden bonus voor hogere sterren en geen farmbare oneindige herhaalbeloningen.

## 20. Wereldbijbel, art direction en animatie
**Wereld 1 — Blossom Teahouse:** honingkleurig hout, bamboe, zacht crème, sakuraroze, warme ochtendzon; zachte deegtexturen, keramiek, theekopjes en bloembladparticles. Introductiepersonage is een vrolijke bao-chef. Vijf hoofdstukken: Welkom, Bamboetuin, Theeservies, Bloesemfeest, Meester van het Theehuis.

**Wereld 2 — Lantern Night Market:** indigo schemer, amberkleurige lampions, warme rood- en goudaccenten, streetfoodkraampjes, subtiele nevel; nieuw personage is een pittige chili-dumpling. Vijf hoofdstukken: Eerste Lampions, Kleurrijke Kraampjes, Drukke Steegjes, Festivalnacht, Chef van de Markt.

**Wereld 3 — Moonlit Mountain Kitchen:** maanblauw, jadegroen, zachte lavendel, sneeuw of nevel buiten en een warme keuken binnen; een wijze dumplingmeester en magische stoomaccenten. Vijf hoofdstukken: Bergpad, Jadekeuken, Maanbrug, Sterrenstoom, Het Grote Dumplingfeest.

Maak één consistent **assetmanifest**: `id`, `source`, `license/ownership`, `hash`, `sourceResolution`, `atlas`, `world`, `usage`, `status`. Bestaande Dumpling Diner-assets worden bij voorkeur gerefereerd of gekopieerd via een herhaalbaar importscripts, niet handmatig opnieuw getekend. Bestaande assets mogen worden geschaald, gekleurd of opnieuw samengesteld zonder herkenbare character inconsistenties. Genereer ontbrekende assets alleen met beschikbare geautoriseerde API-keys; sla prompt, model, seed, output, upscale en QA-score op. Assets moeten op retina-resolutie scherp zijn en efficiënte atlassen opleveren; maak low-memory-varianten voor oudere apparaten.

**Ringvisuals:** ringgeometrie wordt vectorieel/procedureel gerenderd zodat gaten, rotatie en hitboxes exact overeenkomen; gebruik bestaande dumpling-assets als materiaal, decor, animatie en beloning, niet als vervanging voor onleesbare ringvormen. Minimaal 6 contrastrijke materiaal-/kleurvarianten; ook vorm- en patroononderscheid voor kleurenblindheid. Animaties: ring select 100–150 ms, snap 70–120 ms, release 300–550 ms, dumpling-pop 400–700 ms, chapter win 1–2 s met skip; alle effecten reduceren of uitschakelen bij Reduce Motion. Maak performancebudgetten voor overdraw, particles, atlasgeheugen en geluid.

## 21. Schermbijbel en responsive UI
Verplichte schermen: splash met directe offline start; optionele first-run onboarding; hoofdmenu; wereldkaart; hoofdstukkaart; gameplay; pauze; voltooiingsscherm; retry; dumplingcollectie; dagelijkse puzzel; shop/boosters; Premium; instellingen; privacy/consentkeuzes; credits; offline fout-/herstelstatus. Toon geen loginmuur.

**Gameplay-layout portrait:** bovenaan back, level, voortgang en optioneel muntentotaal; centraal een maximaal groot vierkant puzzelveld; daaronder undo, restart, hint en boosters; onderaan een rustige statusregel. Ad-banner alleen als de veilige beschikbare ruimte groot genoeg is en de speler niet Premium heeft; reserveer nooit een leeg bannergebied als er geen advertentie getoond wordt. **Landscape/tablet:** puzzelveld links of centraal, controls in zijpaneel; alle interactieve targets minimaal 44×44 pt; geen cruciale informatie onder notch, gesture bar of banner. Schaal via safe areas en viewport breakpoints, niet via hardcoded iPhone-pixels. Ondersteun iPhone portrait als primaire layout, iPad portrait/landscape en Android-telefoons/tablets; telefoon-landscape alleen als het zonder onbruikbare touch-targets goed werkt.

**Bediening:** touch-drag volgt de cirkelboog met consistente snapping; een drag buiten de ring mag niet een andere ring selecteren; overlappende ringen krijgen duidelijke selectiestaat en optionele cycling-control. Toegankelijk alternatief: tap ring, grote links/rechts-knoppen; duidelijke focusvolgorde, VoiceOver/TalkBack-labels, hoge-contrastmodus, afzonderlijke muziek/SFX/haptiek-toggles en Reduce Motion. Laat foutieve acties vriendelijk zien zonder agressief rood of strafgeluid. Gebruik NL/EN/DE lokalisatiebestanden; alle UI-tekst via string keys en geen tekst ingebakken in art.

## 22. Vier boosters, hint en anti-frustratie
1. **Steam Hint:** markeert één zinvolle volgende ring en toont met een subtiele boog de aanbevolen draairichting; verbruikt pas een booster als de hint daadwerkelijk verschijnt. Gratis contextuele uitleg van spelregels telt niet als booster.
2. **Bamboo Undo+:** herstelt de toestand van vóór de laatste voltooide zet, ook wanneer die een ring verwijderde; gewone undo blijft altijd gratis voor minstens één stap. De booster is alleen nodig voor optionele uitgebreidere undo-history als dit echt toegevoegde waarde heeft; maak hem niet kunstmatig noodzakelijk.
3. **Chef's Twist:** roteert één geselecteerde ring automatisch naar een legale volgende solverstap. Geen garantie dat het hele level daarmee klaar is; animatie en toestand zijn identiek aan een menselijke actie.
4. **Golden Steamer:** lost één geselecteerde blokkade of ring op via een door de solver gevalideerde legale actie; nooit een willekeurige state mutation die het level kan beschadigen.

Maak alle boosters transactioneel en idempotent: `requestId`, inventory-before, solver-validated effect, inventory-after en save-commit. Een onderbroken advertentie of app-background mag nooit tot dubbele grant of dubbele afschrijving leiden. Bied enkele verdiende boosters tijdens de campagne, optionele coin-aankoop en rewarded advertenties indien ingeschakeld. Nooit boostervereiste voor progressie. Toon na herhaald vastlopen een gratis tactische tip zonder advertentieverplichting.

## 23. Economie, AdMob, consent en Premium
Startconfiguratie: `ads.enabled=false`, `ads.banner.enabled=false`, `ads.interstitial.enabled=false`, `ads.rewarded.enabled=false`, `iap.enabled` alleen waar storeproduct/config werkelijk bestaat. Implementeer adapters `AdsProvider`, `ConsentProvider`, `PurchaseProvider` en fake providers voor tests. Lees app-ID's, unit-ID's en product-ID's uit per-platform buildconfig of secure CI secrets; geen echte identifiers in git of voorbeeldconfig. Development mag alleen officiële SDK-testads gebruiken. Ontbrekende configuratie = geen advertenties, geen crash en geen lege ruimte.

**Placementbeleid:** banners uitsluitend op niet-kritische menu- of resultaatgebieden als de layout dat toelaat; geen banners over het puzzelveld. Interstitials alleen na een level of hoofdstuk, nooit in tutorials 1–10, niet na een verlies, niet bij appstart, niet direct na Premium-aankoop en nooit op een moment dat de gebruiker nog een puzzelactie uitvoert. Initieel maximaal één interstitial per vier voltooide levels, minimaal 8 minuten tussen vertoningen en maximaal drie per speelsessie; maak deze caps lager of schakel ze uit als playtests frictie tonen. Rewarded uitsluitend na expliciete knopdruk voor duidelijk gecommuniceerde beloning; geef een gratis alternatief waar een beloning voor toegankelijkheid of voortgang relevant is. No-fill en offline = onmiddellijk terug naar normale UI, geen spinnerloop.

**Premium:** één niet-vervallende eenmalige IAP, geen abonnement. Verwijder alle banners en interstitials en ook de bijbehorende layout-ruimte; bied een bescheiden cosmetische bonus en een beperkte dagelijkse gratis boosterclaim. Premium mag rewarded ads vrijwillig gebruiken indien de speler dat wil, maar biedt ook de gratis claim zonder reclame. Productprijzen altijd uit de store ophalen; restore purchases; cache entitlement offline; valideer aankopen volgens actuele platform-SDK. Verwijder entitlement niet wegens tijdelijk netwerkverlies. Voor advertenties in EER/UK: implementeer UMP en actuele privacykeuzes, laad SDK/ads pas waar wettelijk en technisch toegestaan; ATT op iOS waar vereist. Behandel leeftijd en kindgerichte status conservatief en laat de uiteindelijke classificatie bij de eigenaar. Vul privacybeleid, App Store privacylabels, Play Data Safety en app-ads.txt pas met feitelijk gebruikte SDK-/datastromen. Geen eigen trackingserver nodig.

## 24. Savegame, dagelijkse puzzels en privacy
Local-first opslag met atomair schrijven en herstelkopie. Bewaar schemaVersion, laatste unlocked level, bestMoves/stars, chapter unlocks, coins, boosterinventaris, cosmetica, settings, dailyPuzzleHistory en Premium-entitlementcache. Save direct na release, levelcompletion, boostertransactie en aankoopwijziging. Migreer oude schemas met expliciete tests. Dagelijkse puzzel wordt offline deterministisch afgeleid van lokale kalenderdatum + ingebouwde seedlijst; geen server of streak-afstraffing nodig. Bij tijdzonewissel geen dubbele beloningen voor dezelfde dag-ID; bied bij ongeldige klok geen permanente lockout. Geen accountplicht, advertenties uit bij ontbrekende consent en analytics standaard privacyvriendelijk/uit tenzij correct geconfigureerd.

## 25. Audio, feedback en retention
Drie naadloos loopbare ambient tracks, één per wereld; layer subtiel extra instrumenten bij een hoofdstukfinale. SFX: deeg-tap, houten klik, ring-snap, stoom-pop, coin-chime, hoofdstukbel, zachte mislukte-release-feedback. Voorkom dat frequente snaps geluidsoverlast veroorzaken via volume-limiting en cooldown. Haptics optioneel, licht en per-platform correct. Engagement via dumplingcollectie, decoratieve unlocks, hoofdstukscènes, 3 sterren per level, dagelijks level en wereldherbezoek. Geen energie, geen verplichte push, geen login en geen FOMO-timers.

## 26. Technische modules en interfaces
Aanbevolen mappen:
```text
/game/core/{ring_model,rotation,release_rules,solver,level_loader}
/game/systems/{progression,economy,boosters,save,daily}
/game/platform/{ads,consent,purchases,notifications_stub}
/game/ui/{screens,components,layouts,accessibility}
/game/presentation/{render,animation,particles,audio,haptics}
/content/{levels,worlds,chapters,cosmetics,economy,locales}
/assets/{imported,generated,atlases,audio}
/tools/{asset_import,level_editor,level_generator,level_validator,qa_report}
/tests/{unit,integration,content,platform,visual}
/docs/{AUDIT,ARCHITECTURE,LEVEL_SCHEMA,SOLVER,ASSET_MANIFEST,
      AD_CONFIGURATION,PRIVACY,TEST_REPORT,RELEASE_CHECKLIST,IMPLEMENTATION_STATUS}
```
Scheid pure corelogica van rendering en platform-SDK's. Definieer `GameState`, `LegalAction`, `ActionResult`, `LevelDefinition`, `SolveResult`, `EntitlementState` en `RewardTransaction` als versieerbare types. Alle state-mutaties lopen via één action dispatcher zodat undo, replay, save en solver dezelfde regels gebruiken. Dependency injection voor ads/IAP/clock/RNG maakt offline en edge-case tests mogelijk. Render met interpolation en frame-onafhankelijke animatietijden; geen physicsafhankelijkheid voor puzzelcorrectheid.

## 27. Testmatrix en kwaliteitsdrempels
**Unit:** rotatie rond 0°/360°, gap-intervallen, locks, afhankelijkheden, release, wincondition, undo, reset, serialisatie, migraties, reward-idempotentie en Premium-state. **Property-based:** iedere door generator geproduceerde puzzel is oplosbaar zonder boosters; solveracties zijn legaal; replay van canonieke oplossing eindigt in win; undo van iedere actie herstelt exact de voorafgaande toestand. **Content:** precies 150 campagnelevels en 15 hoofdstukfinales; alle assets bestaan; geen onbedoelde dubbele layout; alle UI-stringkeys vertaald; iedere mechaniek heeft tutorial vóór combinaties. **Integratie:** vliegtuigmodus, no-fill, consent geweigerd, aankoop geannuleerd, aankoop onderbroken, restore, achtergrond/foreground, proceskill na save, lage opslag, tijdzonewissel. **UI:** screenshot-tests op kleine telefoon, grote telefoon, tablet portrait en landscape; controle op safe area, clipped tekst en bannerloze Premium-layout. **Performance:** mik op stabiele 60 fps op de ondersteunde middenklasse, snelle cold start, geen memory leaks en geen onnodig batterijverbruik.

Rapporteer in `docs/TEST_REPORT.md` per testcommando de datum, omgeving, werkelijk resultaat en eventuele blocker. Een desktopmock bewijst geen iOS/Android releasebuild; vermeld dat expliciet. Maak scripts `scripts/verify_all`, `scripts/build_android` en `scripts/build_ios` of de equivalenten van de gekozen engine. CI moet lint, unit, contentvalidatie, assetmanifestcontrole en reproduceerbare buildchecks uitvoeren. Storepublicatie, echte aankopen en echte advertentieimpressies zijn expliciet handmatige eindvalidatie.

## 28. Acceptatie per mijlpaal
**M0 Audit:** repository, assetlicenties en enginekeuze vastgelegd; schone build. **M1 Vertical slice:** 10 echte levels met werkende rotatie, release, solver, undo, saves en eerste wereldart. **M2 Systemen:** volledige UI, accessibility, vier boosters, economie, audio/haptics, daily mode en platformadapters. **M3 Content:** 150 afzonderlijke levels, alle solverchecks groen, 3 werelden, 15 hoofdstukfinales, collecties en lokalisatie. **M4 Monetisatie:** feature-flagged AdMob/UMP, testads, IAP/restore en Premium zonder lege ad-ruimte. **M5 Releasecandidate:** echte device-smoketests waar hardware beschikbaar is, privacy/releasechecklist, performance en bekende beperkingen. Een mijlpaal is pas voltooid wanneer de bijbehorende tests daadwerkelijk zijn uitgevoerd.

## 29. Wat de agent mag beslissen en wat niet
**Zelf beslissen:** codeorganisatie, renderdetails, passende engine bij greenfield, precieze assetatlasindeling, effecttimings binnen genoemde grenzen, individuele ringcoördinaten en levelseeds, zolang de productbijbel en QA-drempels intact blijven. **Niet zelfstandig wijzigen:** 3 werelden, 150 campagnelevels, 15 hoofdstukken, offline speelbaarheid, geen boosterpaywall, geen abonnement, geen verplichte advertenties, default-OFF productieads, bestaande IP niet kopiëren, Premium zonder lege adruimte, one-shot levering van broncode en QA-rapport. **Menselijke input nodig:** echte app signing, Apple/Google developeraccounts, IAP-productregistratie, AdMob-productie-ID's, privacybeleid-URL, finale storepublicatie en eventuele ontbrekende rechten op assets. Laat deze externe stappen nooit leiden tot het overslaan van lokale implementatie of testbare mocks.

## 30. Definitieve one-shot agentprompt — direct uitvoeren
> Jij bent de lead game engineer, technical artist, level designer, QA engineer en release engineer voor Shio Studios. Bouw **Dumpling Rings** volledig volgens deze gehele projectbijbel, inclusief de 150-level-roadmap uit Deel I en de uitvoeringsspecificaties uit Deel II. Begin onmiddellijk met een audit van de huidige repository en beschikbare Dumpling Diner-assets. Neem geen onnodige pauzes en stel geen vragen die uit de repository of dit document beantwoord kunnen worden. Werk autonoom in mijlpalen M0–M5, maak echte code en content, voer tests uit en herstel fouten voordat je verdergaat. Lever exact 3 werelden, 15 hoofdstukken en 150 unieke speelbare, solver-gevalideerde campagnelevels, plus alle beschreven UX, audio, art, boosters, saves, offline daily puzzle, Premium-IAP en veilige default-OFF AdMob/UMP-integratie. Hergebruik rechtmatig beschikbare bestaande assets met behoud van characterconsistentie; genereer ontbrekende art alleen via beschikbare geautoriseerde middelen en documenteer eventuele placeholders. Produceer Android- en iOS-builds waar toolchains/signing beschikbaar zijn; geef anders reproduceerbare buildinstructies en benoem de externe blocker. Activeer geen productieadvertenties, verricht geen echte aankopen en publiceer niet zonder mijn expliciete goedkeuring. Sluit af met `docs/IMPLEMENTATION_STATUS.md`, `docs/TEST_REPORT.md`, een lijst van werkelijk geleverde bestanden en uitsluitend de nog noodzakelijke externe account-/signingstappen. Noem iets alleen af als het aantoonbaar gebouwd en getest is.
