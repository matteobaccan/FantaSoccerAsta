/*
 * Author Matteo Baccan
 * http://www.baccan.it
 *
 * Distributed under the GPL v3 software license, see the accompanying
 * file LICENSE or http://www.gnu.org/licenses/gpl.html.
 *
 */
package it.baccan.fantasoccerasta;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.cookie.DefaultCookieSpec;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * @author Matteo Baccan
 */
@Slf4j
public class FantaSoccerAsta {

    private static final String SITEHOME = "https://www.fanta.soccer";
    private static final String USERAGENT = "User-Agent";
    private static final String USERAGENT_VALUE = "Mozilla/5.0 (Windows NT 6.1; rv:52.0) Gecko/20100101 Firefox/52.0";
    private static final String SERIE_A = "A";
    private static final String SERIE_B = "B";
    private static final String ACCEPT = "Accept";
    private static final String ACCEPT_VALUE = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String IT_STATISTICHE = "/it/statistiche/";
    private static final String SELECT = "<option selected=\"selected\" value=\"";

    static {
        // Inizializza Unirest
        // Cookie store
        BasicCookieStore cookieStore = new BasicCookieStore();

        CookieSpecProvider csf = (HttpContext context) -> new DefaultCookieSpec() {
            @Override
            public void validate(Cookie cookie, CookieOrigin origin)
                    throws MalformedCookieException {
                // Allow all cookies
                log.debug("MalformedCookieException");
            }
        };

        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec("easy")
                .setConnectTimeout(10 * 1000)
                .setConnectionRequestTimeout(10 * 1000)
                .setSocketTimeout(10 * 1000)
                .build();

        // Client diretto
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieSpecRegistry(RegistryBuilder.<CookieSpecProvider>create()
                        .register("easy", csf).build())
                .setDefaultRequestConfig(requestConfig)
                .build();

        Unirest.setHttpClient(httpclient);
    }

    /**
     * Esegue l'analisi di mercato.
     *
     * @param username
     * @param password
     */
    public void run(final String username, final String password) {
        try {
            log.info("Login");

            // Prendo le età anagrafiche
            Map<String, String> aEta = getEta();

            // Login e get dei Cookie
            doLogin(username, password);

            // Se l'utente è iscritto a una lega privata: prendo i giocatori della lega
            List<String> aCod = goGetPlayers();

            // Prendo i giocatori infortunati
            List<String> aInj = goGetInjured();

            // Prendo i rigoristi
            Map<String, String> aRig = getRigoristi();

            // Prendo tutti i giocatori anche quelli senza voto
            List<Calciatore> aAll = getAllGiocatori(aInj, aRig, aEta);

            // Genero il report
            doGenerateReport(aCod, aInj, aRig, aAll, aEta);

            log.info("Statistiche generate: vai e vinci per me");
        } catch (Exception ex) {
            log.error("Errore di elaborazione", ex);
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel label = new JPanel(new GridLayout(0, 1, 2, 2));
        label.add(new JLabel("Username", SwingConstants.RIGHT));
        label.add(new JLabel("Password", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField username = new JTextField();
        controls.add(username);
        JPasswordField password = new JPasswordField();
        controls.add(password);
        panel.add(controls, BorderLayout.CENTER);

        JOptionPane.showConfirmDialog(null, panel, "Fanta.soccer login", JOptionPane.OK_CANCEL_OPTION);

        FantaSoccerAsta engine = new FantaSoccerAsta();
        engine.run(username.getText(), new String(password.getPassword()));
    }

    private void scriviFile(byte[] buffer, String cFile) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(cFile, "rw")) {
            randomAccessFile.write(buffer, 0, buffer.length);
            randomAccessFile.setLength(buffer.length);
        } catch (Exception ex) {
            log.error("Errore di scrittura file", ex);
        }
    }

    private String getPage(final String url) throws UnirestException {
        log.info(" Leggo pagina [{}]", url);
        return Unirest.get(url)
                .header(ACCEPT, ACCEPT_VALUE)
                .header(USERAGENT, USERAGENT_VALUE)
                .asString()
                .getBody();
    }

    private InputStream getPageStream(final String url) throws UnirestException {
        log.info(" Leggo pagina binaria [{}]", url);
        return Unirest.get(url)
                .header(ACCEPT, ACCEPT_VALUE)
                .header(USERAGENT, USERAGENT_VALUE)
                .asBinary()
                .getBody();
    }

    private void doLogin(final String username, final String password) throws UnirestException, FantaException {
        // Chiamo la login per avere il cookie di sessione
        String login = getPage(SITEHOME + "/it/login/");

        // Prendo __VIEWSTATEGENERATOR e __VIEWSTATE
        Document loginDoc = Jsoup.parse(login);

        Element viewStateGenerator = loginDoc.getElementsByAttributeValue("name", "__VIEWSTATEGENERATOR").first();
        String cE = viewStateGenerator.attr("value");

        Element viewState = loginDoc.getElementsByAttributeValue("name", "__VIEWSTATE").first();
        String cV = viewState.attr("value");

        log.info("Homepage");
        // Faccio la post di login
        HttpResponse<String> homePage = Unirest.post(SITEHOME + "/it/login/")
                .queryString("lang", "it")
                .header(ACCEPT, ACCEPT_VALUE)
                .header(USERAGENT, USERAGENT_VALUE)
                .field("__EVENTARGUMENT", "")
                .field("__EVENTTARGET", "")
                .field("__VIEWSTATE", cV)
                .field("__VIEWSTATEGENERATOR", cE)
                .field("ctl00$MainContent$wuc_Login1$btnLogin", "accedi")
                .field("ctl00$MainContent$wuc_Login1$username", username)
                .field("ctl00$MainContent$wuc_Login1$password", password)
                .asString();

        if (homePage.getStatus() != 200) {
            throw new FantaException("Errore di login");
        }
    }

    private List<String> goGetPlayers() throws UnirestException, FantaException {
        List<String> aCod = new ArrayList<>();

        // Prendo la lega privata
        String hrefLegaPrivata = doGetLegaPrivata();

        // Prendo i giocatori gia' in organico
        if (!hrefLegaPrivata.isEmpty()) {
            String cSQurl = SITEHOME + hrefLegaPrivata;

            log.info("Prendo i dati della lega privata [{}]", cSQurl);
            cSQurl = cSQurl.substring(0, cSQurl.indexOf("/homelega/")) + "/classifica/";

            // Prendo la lega
            String legaPage = getPage(cSQurl);

            // Ora cerco tutte le squadre
            // Prendo il link alla pagina della lega privata
            Document legaPageDoc = Jsoup.parse(legaPage);
            Elements alp = legaPageDoc.getElementsByTag("a");
            List<String> vSQ = new ArrayList<>();
            for (Element e : alp) {
                String idsquadra = e.attr("href");
                if (idsquadra.contains("/calciatori/") && idsquadra.startsWith("http") && !vSQ.contains(idsquadra)) {
                    vSQ.add(idsquadra);
                    log.info("Aggiungo la squadra [{}]", idsquadra);
                }
            }

            // Estraggo i giocatori
            StringBuilder rose = new StringBuilder();
            for (String cSQ : vSQ) {
                // Prendo la squadra
                String squadraURL = cSQ;
                log.info("Prendo la squadra [{}]", squadraURL);
                String squadraPage = getPage(squadraURL);

                Document doc = Jsoup.parse(squadraPage);
                Elements infoFantacalciatore = doc.getElementsByClass("info-fantacalciatore");
                for (Element e : infoFantacalciatore) {
                    Element nomeElement = e.getElementsByClass("strong").first();
                    if (nomeElement != null) {
                        String nome = nomeElement.text();
                        String cCod = nomeElement.attr("href");
                        cCod = cCod.substring("/it/seriea/".length());
                        if (cCod.contains("/")) {
                            cCod = cCod.substring(0, cCod.indexOf("/"));
                            aCod.add(cCod);
                            rose.append(String.format("\"%s\";\"%s\"%n", nome, cCod));
                            log.info("{}-{}", cCod, nome);
                        }
                    }
                }
            }

            // In caso di debug scrivo le rose
            log.debug("Scrivo le rose delle altre squadre");
            scriviFile(rose.toString().getBytes(), "FantaSoccer-rose.csv");
        } else {
            log.info("Non trovo una lega privata da usare come parametro di filtraggio");
        }
        return aCod;
    }

    private void doGenerateReport(final List<String> aCod,
            final List<String> aInj,
            final Map<String, String> aRig,
            final List<Calciatore> aAll,
            final Map<String, String> aEta) throws UnirestException {
        // Ora scarico la statistica e ordino per i migliori dell'anno
        Map<String, List<String>> giocatori = new HashMap<>();
        giocatori.put("P", new ArrayList<>());
        giocatori.put("D", new ArrayList<>());
        giocatori.put("C", new ArrayList<>());
        giocatori.put("A", new ArrayList<>());

        // Prendo le statistiche
        List<Calciatore> corrente = getStatisticaCorrente(aInj, aRig, aEta);
        List<Calciatore> previous = getStatisticaPrecedente(SERIE_A);
        previous.addAll(getStatisticaPrecedente(SERIE_B));

        // Unisco l'array giocatori con quelli che non hanno giocato
        mergeArray(aAll, corrente);

        log.info("Divido giocatori per ruolo");
        corrente.forEach(calciatore -> {
            if (!aCod.contains(calciatore.getCodice())) {

                AtomicReference<Calciatore> statistichePrecedenti = new AtomicReference<>();
                previous.forEach(precedenteCalciatore -> {
                    if (precedenteCalciatore.getCodice().equals(calciatore.getCodice())) {
                        statistichePrecedenti.set(precedenteCalciatore);
                    }
                });

                String s = String.format("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"",
                        calciatore.getRuolo(),
                        calciatore.getNome(),
                        calciatore.getSquadra(),
                        calciatore.getEta(),
                        calciatore.getFantamedia(),
                        calciatore.getPresenze());

                s += getRendimentoPrecedente(statistichePrecedenti.get(), calciatore);

                s += String.format(";\"%s\";\"%s\";\"%s\"",
                        calciatore.getInfortunato(),
                        calciatore.getRigorista(),
                        calciatore.getEvidenzia());

                giocatori.get(calciatore.getRuolo().toUpperCase()).add(s);
            }
        });

        // Esporto i risultati
        goGeneraSvincolati(giocatori.get("P"), giocatori.get("D"), giocatori.get("C"), giocatori.get("A"));
    }

    private List<Calciatore> getStatisticaCorrente(List<String> aInj, Map<String, String> aRig, Map<String, String> aEta) throws UnirestException {
        String calciatoriPage = getStatistiche(SERIE_A, false, false);
        List<Calciatore> corrente = generaCalciatori(SERIE_A, calciatoriPage, aInj, aRig, aEta);
        // Se non ho le statistiche aggiornate, prendo la giornata precedente
        if (corrente.isEmpty()) {
            log.info("Le statistiche della giornata corrente non sono pronte, prendo quelle della giornata precedente");
            calciatoriPage = getStatistiche(SERIE_A, false, true);
            corrente.addAll(generaCalciatori(SERIE_A, calciatoriPage, new ArrayList<>(), aRig, aEta));
        }
        log.info("Trovate statistiche per [{}] giocatori della stagione corrente", corrente.size());
        return corrente;
    }

    private List<Calciatore> getStatisticaPrecedente(String serie) throws UnirestException {
        String calciatoriPagePrevious = getStatistiche(serie, true, false);
        List<Calciatore> previous = generaCalciatori(serie, calciatoriPagePrevious, new ArrayList<>(), new HashMap<>(), new HashMap<>());
        log.info("Trovate statistiche per [{}] giocatori di serie [{}] della stagione precedente", previous.size(), serie);
        return previous;
    }

    private String getRendimentoPrecedente(Calciatore statistichePrecedenti, Calciatore calciatore) {
        String previusStat = ";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"";
        if (statistichePrecedenti != null) {
            String notable = "";
            String rendimento = "";

            if (Long.parseLong(statistichePrecedenti.getPresenze()) >= 30) {
                if (statistichePrecedenti.getSquadra().equalsIgnoreCase(calciatore.getSquadra())) {
                    notable = "**";
                } else {
                    notable = "*";
                }
            }

            if (Double.parseDouble(statistichePrecedenti.getFantamedia().replace(',', '.')) > Double.parseDouble(calciatore.getFantamedia().replace(',', '.'))) {
                rendimento = "\uD83E\uDC17"; // Down 15
            }

            previusStat = String.format(";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"",
                    notable,
                    rendimento,
                    statistichePrecedenti.getRuolo(),
                    statistichePrecedenti.getNome(),
                    statistichePrecedenti.getSquadra(),
                    statistichePrecedenti.getFantamedia(),
                    statistichePrecedenti.getPresenze(),
                    statistichePrecedenti.getSerie()
            );
        }
        return previusStat;
    }

    private String doGetLegaPrivata() throws UnirestException {
        String legaprivataPage = getPage(SITEHOME + "/it/lega/privata/");

        // Prendo il link alla pagina della lega privata
        Document legaprivataDoc = Jsoup.parse(legaprivataPage);
        Elements a = legaprivataDoc.getElementsByTag("a");
        String hrefLegaPrivata = "";
        for (Element e : a) {
            String href = e.attr("href");
            if (href.startsWith("/it/lega/privata/") && href.contains("/homelega/")) {
                hrefLegaPrivata = href;
                break;
            }
        }

        return hrefLegaPrivata;
    }

    private void goGeneraSvincolati(final List<String> aP,
            final List<String> aD,
            final List<String> aC,
            final List<String> aA) {
        StringBuilder svincolati = new StringBuilder();
        svincolati.append("Elenco giocatori svincolati per ruolo\r\n").append("\r\n").append("Portieri\r\n");
        aP.forEach(s -> svincolati.append(s).append("\r\n"));

        svincolati.append("\r\n").append("Difensori\r\n");
        aD.forEach(s -> svincolati.append(s).append("\r\n"));

        svincolati.append("\r\n").append("Centrocampisti\r\n");
        aC.forEach(s -> svincolati.append(s).append("\r\n"));

        svincolati.append("\r\n").append("Attaccanti\r\n");
        aA.forEach(s -> svincolati.append(s).append("\r\n"));

        log.info("Scrivo svincolati");
        scriviFile(svincolati.toString().getBytes(StandardCharsets.UTF_8), "FantaSoccer-svincolati.csv");

    }

    private List<String> goGetInjured() throws UnirestException {
        List<String> ret = new ArrayList<>();

        log.info("Prendo la pagina degli infortunati");
        String infortunatiPage = getPage(SITEHOME + "/it/seriea/infortunati/");

        // Salvo l'infortunato
        Document doc = Jsoup.parse(infortunatiPage);
        Elements infoFantacalciatore = doc.getElementsByTag("span");
        for (Element e : infoFantacalciatore) {
            if (e.id().startsWith("MainContent_wuc_Infortunati1_rptSquadre_lblInfortunati_")) {
                Elements linkFantacalciatore = e.getElementsByTag("a");
                for (Element calc : linkFantacalciatore) {
                    String cCod = calc.attr("href");
                    int n3 = cCod.indexOf("/it/seriea/");
                    int n4 = cCod.indexOf("/", n3 + 12);
                    cCod = cCod.substring(n3 + 11, n4);
                    ret.add(cCod);
                }
            }
        }

        return ret;
    }

    private Map<String, String> getRigoristi() throws UnirestException {
        Map<String, String> ret = new HashMap<>();

        log.info("Prendo la pagina dei rigoristi");
        String rigoristiPage = getPage("https://www.goal.com/it/notizie/fantacalcio-rigoristi-serie-a-2022-2023/blte4152d768d5a07d3");

        // Salvo l'infortunato
        Document doc = Jsoup.parse(rigoristiPage);
        Elements infoFantacalciatore = doc.getElementsByTag("li");
        for (Element e : infoFantacalciatore) {
            String rigoristi = e.text();
            if (rigoristi.contains(":")) {
                String squadra = rigoristi.substring(0, rigoristi.indexOf(':'));
                rigoristi = rigoristi.substring(rigoristi.indexOf(':') + 1);
                AtomicInteger pos = new AtomicInteger(0);
                Arrays.asList(rigoristi.split(",")).forEach(
                        rigorista -> ret.put(squadra.trim().toUpperCase() + ":" + rigorista.trim().toUpperCase(), "" + pos.incrementAndGet())
                );
            }
        }

        return ret;
    }

    private List<Calciatore> getAllGiocatori(final List<String> aInj, final Map<String, String> aRig, final Map<String, String> aEta) throws UnirestException {
        List<Calciatore> ret = new ArrayList<>();
        log.info("Prendo la pagina delle statistiche per l'elenco giocatori");
        String statistichePage = getPage(SITEHOME + IT_STATISTICHE);

        int nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbGiornata\"");
        nPosFS1 = statistichePage.indexOf(SELECT, nPosFS1);
        int nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + SELECT.length());

        String giornata = statistichePage.substring(nPosFS1 + SELECT.length(), nPosFS2);

        InputStream fantaGiocatori = getPageStream(SITEHOME + "/it/archivioquotazioni/A/" + getStagione(statistichePage, false) + "/" + giornata + "/exportexcel");

        try {
            Workbook workbook = new HSSFWorkbook(fantaGiocatori);
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                Cell valoreCodice = row.getCell(0);
                if (valoreCodice.getCellType() == CellType.NUMERIC) {
                    String codice = ("" + (valoreCodice.getNumericCellValue() - 1000000L)).replace(".0", "");
                    String cognome = row.getCell(1).getStringCellValue();
                    String squadra = row.getCell(3).getStringCellValue();

                    String infortunato = "";
                    if (aInj.contains(codice)) {
                        infortunato = "INF";
                    }

                    String rigorista = "";
                    String key = squadra.toUpperCase() + ":" + cognome.toUpperCase();
                    if (aRig.containsKey(key)) {
                        rigorista = "R:" + aRig.get(key);
                    }

                    AtomicReference<String> eta = new AtomicReference<>("");
                    aEta.forEach((t, u) -> {
                        if (eta.get().isEmpty()) {
                            if (t.toUpperCase().contains(cognome.toUpperCase()) && t.toUpperCase().contains(squadra.toUpperCase())) {
                                eta.set(u);
                            }
                        }
                    });
                    Calciatore calciatore = new Calciatore();
                    calciatore.setCodice(codice);
                    calciatore.setNome(cognome);
                    calciatore.setSquadra(squadra);
                    calciatore.setRuolo(row.getCell(4).getStringCellValue());
                    calciatore.setPresenze("0");
                    calciatore.setFantamedia("0");
                    calciatore.setInfortunato(infortunato);
                    calciatore.setRigorista(rigorista);
                    calciatore.setEvidenzia("");
                    calciatore.setEta(eta.get());
                    ret.add(calciatore);
                }
            }
            log.info("Trovati [{}] giocatori", ret.size());
        } catch (IOException iOException) {
            log.error("Errore di elaborazione file excel [{}]", iOException.getMessage());
        }

        return ret;
    }

    private String getStatistiche(final String serie, final boolean annoPrecedente, final boolean giornataPrecente) throws UnirestException {
        log.info("Prendo la pagina delle statistiche della giornata");
        String statistichePage = getPage(SITEHOME + IT_STATISTICHE);

        int nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbGiornata\"");

        nPosFS1 = statistichePage.indexOf(SELECT, nPosFS1);
        int nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + SELECT.length());
        String giornata = statistichePage.substring(nPosFS1 + SELECT.length(), nPosFS2);
        if (annoPrecedente) {
            giornata = "38";
        } else {
            // Il sito fornisce le statistiche sulla giornata precedente, a volte meglio prendere quelle della giornata successiva
            if (giornataPrecente) {
                giornata = "" + Long.parseLong(giornata);
            } else {
                giornata = "" + (Long.parseLong(giornata) + 1);
            }
        }

        String stagione = getStagione(statistichePage, annoPrecedente);

        String paginastatistiche = SITEHOME + IT_STATISTICHE + serie + "/" + stagione + "/Tutti/Fantamedia/Full/fs/" + giornata + "/";
        return getPage(paginastatistiche);
    }

    private String getStagione(final String statistichePage, final boolean previous) {
        int nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbStagione\"");
        String curSelect = SELECT;
        if (previous) {
            curSelect = "<option value=\"";
        }
        nPosFS1 = statistichePage.indexOf(curSelect, nPosFS1);
        int nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + curSelect.length());
        return statistichePage.substring(nPosFS1 + curSelect.length(), nPosFS2);
    }

    private List<Calciatore> generaCalciatori(final String serie, final String calciatori, final List<String> aInj, final Map<String, String> aRig, final Map<String, String> aEta) {
        List<Calciatore> ret = new ArrayList<>();

        int tableStart = calciatori.indexOf("<table class=\"table bg-default\" id=\"statistiche-calciatori\"");
        String calciatoriPage = calciatori.substring(tableStart, calciatori.indexOf("</table>", tableStart));

        int nPos = 0;
        while (true) {
            int nGio1 = calciatoriPage.indexOf("<tr>", nPos);
            int nGio2 = calciatoriPage.indexOf("</tr>", nGio1);
            if (nGio1 == -1 || nGio2 == -1) {
                break;
            }
            nPos = nGio2;

            String trGiocatore = calciatoriPage.substring(nGio1, nGio2 + 5);
            Document docGiocatore = Jsoup.parse("<html><body><table>" + trGiocatore + "</table></body></html>");
            Elements td = docGiocatore.getElementsByTag("td");

            if (!td.isEmpty()) {
                String nome = td.get(1).text();
                int n1 = nome.indexOf("(");
                int n2 = nome.indexOf(")", n1);
                String ruolo = nome.substring(n1 + 1, n2);
                final String cognome = nome.substring(0, n1).trim();

                final String squadra = td.get(2).text();
                String fantamedia = td.get(3).text();
                String presenze = td.get(4).text();
                String evidenzia = getEvidenzia(presenze, fantamedia);

                String codice = td.get(1).html();
                int n3 = codice.indexOf("/it/serie" + serie.toLowerCase() + "/");
                int n4 = codice.indexOf("/", n3 + 12);
                codice = codice.substring(n3 + 11, n4);

                String infortunato = "";
                if (aInj.contains(codice)) {
                    infortunato = "INF";
                }

                String rigorista = "";
                String key = squadra.toUpperCase() + ":" + cognome.toUpperCase();
                if (aRig.containsKey(key)) {
                    rigorista = "R:" + aRig.get(key);
                }

                AtomicReference<String> eta = new AtomicReference<>("");
                aEta.forEach((t, u) -> {
                    if (eta.get().isEmpty()) {
                        //log.info("[{}][{}][{}]", t.toUpperCase(), cognome.toUpperCase(), squadra.toUpperCase());
                        if (t.toUpperCase().contains(cognome.toUpperCase()) && t.toUpperCase().contains(squadra.toUpperCase())) {
                            eta.set(u);
                        }
                    }
                });

                Calciatore calciatore = new Calciatore();
                calciatore.setCodice(codice);
                calciatore.setRuolo(ruolo);
                calciatore.setNome(cognome);
                calciatore.setSquadra(squadra);
                calciatore.setFantamedia(fantamedia);
                calciatore.setPresenze(presenze);
                calciatore.setInfortunato(infortunato);
                calciatore.setRigorista(rigorista);
                calciatore.setEvidenzia(evidenzia);
                calciatore.setSerie(serie);
                calciatore.setEta(eta.get());

                ret.add(calciatore);

            }
        }
        return ret;
    }

    private void mergeArray(final List<Calciatore> aAll, final List<Calciatore> corrente) {
        aAll.forEach(calciatore -> {
            AtomicBoolean found = new AtomicBoolean(false);
            corrente.forEach(calciatoreConPrezenze -> {
                if (calciatore.getCodice().equals(calciatoreConPrezenze.getCodice())) {
                    found.set(true);
                }
            });
            if (!found.get()) {
                corrente.add(calciatore);
            }
        });
    }

    private String getEvidenzia(String presenze, String fantamedia) {
        String evidenzia = "";

        try {
            int nPre = Double.valueOf(presenze).intValue();
            double nFM = Double.parseDouble(fantamedia.replace(",", "."));
            if (nPre > 14 && nFM > 6) {
                evidenzia = "*";
            }
            if (nPre > 17 && nFM > 6) {
                evidenzia = "**";
            }
            if (nPre > 20 && nFM > 6) {
                evidenzia = "***";
            }
        } catch (NumberFormatException ex) {
            log.info("Errore", ex);
        }

        return evidenzia;
    }

    private Map<String, String> getEta() throws UnirestException {
        Map<String, String> ret = new HashMap<>();

        log.info("Prendo la pagina delle squadre");
        String squadrePage = getPage("https://www.transfermarkt.it/serie-a/startseite/wettbewerb/IT1");

        // Salvo l'infortunato
        Document doc = Jsoup.parse(squadrePage);
        Elements tabellaSquadre = doc.getElementsByClass("items");
        if (tabellaSquadre.isEmpty()) {
            log.error("Tabella squadre non trovata");
        } else {
            Elements rigaSquadra = tabellaSquadre.get(0).getElementsByClass("hauptlink");
            for (Element e : rigaSquadra) {
                Elements squadre = e.getElementsByTag("a");
                for (Element squadra : squadre) {
                    String href = squadra.attr("href");
                    if (href.startsWith("/")) {
                        String squadraPage = getPage("https://www.transfermarkt.it" + href);

                        Document docSquadra = Jsoup.parse(squadraPage);
                        String squadraNome = docSquadra.getElementsByTag("h1").get(0).text();
                        Elements tabellaGiocatori = docSquadra.getElementsByClass("items");

                        Element giocatore = tabellaGiocatori.get(0);
                        Elements tabellaGiocatoriTbody = giocatore.getElementsByTag("tbody");
                        for (Element tbody : tabellaGiocatoriTbody) {
                            Elements tabellaGiocatoriTr = tbody.getElementsByTag("tr");

                            for (Element tr : tabellaGiocatoriTr) {
                                if (!tr.getElementsByClass("zentriert").isEmpty()) {
                                    String born = tr.getElementsByClass("zentriert").get(1).text();
                                    born = born.substring(born.indexOf('(')).replace("(", "").replace(")", "");
                                    String name = tr.getElementsByClass("bilderrahmen-fixed").get(0).attr("alt");

                                    // Normalizzo
                                    name = name.replace("�", "i");

                                    ret.put(squadraNome + " " + name, born);

                                    //log.info(squadraNome + " " + name);
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    /*
    private static final Charset utf8charset = Charset.forName("UTF-8");
    private static final Charset iso88591charset = Charset.forName("ISO-8859-1");

    public static String toISO8859_1(String text) {
        try {

            ByteBuffer inputBuffer = ByteBuffer.wrap(text.getBytes(utf8charset));
            // decode UTF-8
            CharBuffer data = utf8charset.decode(inputBuffer);
            // encode ISO-8559-1
            ByteBuffer outputBuffer = iso88591charset.encode(data);
            byte[] outputData = outputBuffer.array();

            return new String(outputData, iso88591charset);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
     */
}
