# FantaSoccerAsta

[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/matteobaccan/FantaSoccerAsta.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/matteobaccan/FantaSoccerAsta/context:java)
[![Coverage Status](https://coveralls.io/repos/github/matteobaccan/FantaSoccerAsta/badge.svg?branch=master)](https://coveralls.io/github/matteobaccan/FantaSoccerAsta?branch=master)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/matteobaccan/FantaSoccerAsta.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/matteobaccan/FantaSoccerAsta/alerts/)
[![Build Status](https://travis-ci.org/matteobaccan/FantaSoccerAsta.svg?branch=master)](https://travis-ci.org/matteobaccan/FantaSoccerAsta)
[![security status](https://www.meterian.io/badge/gh/matteobaccan/FantaSoccerAsta/security)](https://www.meterian.io/report/gh/matteobaccan/FantaSoccerAsta)
[![stability status](https://www.meterian.io/badge/gh/matteobaccan/FantaSoccerAsta/stability)](https://www.meterian.io/report/gh/matteobaccan/FantaSoccerAsta)
[![DepShield Badge](https://depshield.sonatype.org/badges/matteobaccan/FantaSoccerAsta/depshield.svg)](https://depshield.github.io)

[![CodeQL](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/codeql-analysis.yml)
[![GraalVM Build](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/graalvm.yml/badge.svg)](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/graalvm.yml)
[![Java CI with Maven](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/maven.yml/badge.svg)](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/maven.yml)
[![Meterian vulnerability scan workflow](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/meterian.yml/badge.svg)](https://github.com/matteobaccan/FantaSoccerAsta/actions/workflows/meterian.yml)

Calcolo automatico liste Fanta.soccer

## Il problema
Con alcuni amici giochiamo, ogni anno, a Fantacalcio.

Ci sono due momenti importanti durante un campionato di fantacalcio:

* L'asta di inizio campionato
* L'asta di riparazione

Questi due momenti si svolgono normalmente a ottobre e febbraio.

Uno degli aspetti che fa la differenza durante l'asta è il fatto di arrivare preparati: sapere chi comperare puo' fare la differenza fra concludere o meno il campionato.

## La soluzione
Il sito che usiamo normalmente [https://www.fanta.soccer] fornisce una statistica in tempo reale del giocatori: presenze, fantamedia, gol fatti e così via.

Mancavano però alcuni dati che poteva essere interessante avere durante l'asta

* Informazioni su giocatori infortunati
* Informazioni sui rigoristi
* Statisiche comparate rispetto all'anno precedente
* Eventuali cambi di squadra
* Analisi dei soli giocatori svincolati e non di tutti i giocatori disponibili

Per questo motivo ho scritto un programma in grado di colmare questa lacuna.

In questo modo arrivare preparati sarà molto semplice
