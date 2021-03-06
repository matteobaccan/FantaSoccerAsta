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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * @author Matteo Baccan
 */
@Slf4j
public class FantaSoccerAsta {

    private static final String SITEHOME = "https://www.fanta.soccer";
    private static final String USERAGENT = "Mozilla/5.0 (Windows NT 6.1; rv:52.0) Gecko/20100101 Firefox/52.0";

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

            // Login e get dei Cookie
            doLogin(username, password);

            // Prendo i giocatori della lega
            List<String> aCod = goGetPlayers();

            // Prendo i giocatori della lega
            List<String> aInj = goGetInjured();

            // Prendo i giocatori della lega
            List<String> aRig = getRigoristi();

            // Tutti i giocatori anche quelli senza voto
            List<Calciatore> aAll = getAllGiocatori(aInj, aRig);

            // Genero il report
            doGenerateReport(aCod, aInj, aRig, aAll);

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
        try ( RandomAccessFile RAF = new RandomAccessFile(cFile, "rw")) {
            RAF.write(buffer, 0, buffer.length);
            RAF.setLength(buffer.length);
        } catch (Exception ex) {
            log.error("Errore di scrittura file", ex);
        }
    }

    private String getPage(final String url) throws UnirestException {
        return Unirest.get(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", USERAGENT)
                .asString()
                .getBody();
    }

    private void doLogin(final String username, final String password) throws UnirestException, FantaException {
        // Chiamo la login per avere il cookie di sessione
        String login = getPage(SITEHOME + "/it/login/");

        // Prendo __VIEWSTATEGENERATOR e __VIEWSTATE
        Document loginDoc = Jsoup.parse(login);

        Element __VIEWSTATEGENERATOR = loginDoc.getElementsByAttributeValue("name", "__VIEWSTATEGENERATOR").first();
        String cE = __VIEWSTATEGENERATOR.attr("value");

        Element __VIEWSTATE = loginDoc.getElementsByAttributeValue("name", "__VIEWSTATE").first();
        String cV = __VIEWSTATE.attr("value");

        log.info("Homepage");
        // Faccio la post di login
        HttpResponse<String> homePage = Unirest.post(SITEHOME + "/it/login/")
                .queryString("lang", "it")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", USERAGENT)
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
                            rose.append(String.format("\"%s\";\"%s\"\r\n", nome, cCod));
                            log.info("{}-{}", cCod, nome);
                        }
                    }
                }
            }

            // In caso di debug scrivo le rose
            if (log.isDebugEnabled()) {
                log.debug("Scrivo rose");
                scriviFile(rose.toString().getBytes(), "FantaSoccer-rose.csv");
            }
        } else {
            log.info("Non trovo una lega privata da usare come parametro di filtraggio");
        }
        return aCod;
    }

    private void doGenerateReport(final List<String> aCod,
            final List<String> aInj,
            final List<String> aRig,
            final List<Calciatore> aAll) throws UnirestException {
        // Ora scarico la statistica 2011-2012 e ordino per i migliori dell'anno
        ArrayList<String> portieri = new ArrayList<>();
        ArrayList<String> difensori = new ArrayList<>();
        ArrayList<String> centrocampisti = new ArrayList<>();
        ArrayList<String> attaccanti = new ArrayList<>();

        // Prendo le statistiche
        String calciatoriPage = getStatistiche(false);
        List<Calciatore> corrente = generaCalciatori(calciatoriPage, aInj, aRig);

        String calciatoriPagePrevious = getStatistiche(true);
        List<Calciatore> previous = generaCalciatori(calciatoriPagePrevious, new ArrayList<>(), new ArrayList<>());

        // Unisco l'array giocatori con quelli che non hanno giocato
        mergeArray(aAll, corrente);

        log.info("Divido giocatori");
        corrente.forEach(calciatore -> {
            if (!aCod.contains(calciatore.getCodice())) {

                AtomicReference<Calciatore> statistichePrecedenti = new AtomicReference<>();
                previous.forEach(precedenteCalciatore -> {
                    if (precedenteCalciatore.getCodice().equals(calciatore.getCodice())) {
                        statistichePrecedenti.set(precedenteCalciatore);
                    }
                });

                String s = String.format("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"",
                        calciatore.getCodice(),
                        calciatore.getRuolo(),
                        calciatore.getNome(),
                        calciatore.getSquadra(),
                        calciatore.getFantamedia(),
                        calciatore.getPresenze());

                if (statistichePrecedenti.get() != null) {
                    s += String.format(";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"",
                            statistichePrecedenti.get().getRuolo(),
                            statistichePrecedenti.get().getNome(),
                            statistichePrecedenti.get().getSquadra(),
                            statistichePrecedenti.get().getFantamedia(),
                            statistichePrecedenti.get().getPresenze()
                    );
                } else {
                    s += ";\"\";\"\";\"\";\"\";\"\"";
                }

                s += String.format(";\"%s\";\"%s\";\"%s\"",
                        calciatore.getInfortunato(),
                        calciatore.getRigorista(),
                        calciatore.getEvidenzia());

                if (calciatore.getRuolo().equalsIgnoreCase("P")) {
                    portieri.add(s);
                } else if (calciatore.getRuolo().equalsIgnoreCase("D")) {
                    difensori.add(s);
                } else if (calciatore.getRuolo().equalsIgnoreCase("C")) {
                    centrocampisti.add(s);
                } else if (calciatore.getRuolo().equalsIgnoreCase("A")) {
                    attaccanti.add(s);
                }
            }
        });

        // Esporto i risultati
        goGeneraSvincolati(portieri, difensori, centrocampisti, attaccanti);
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
        aP.forEach((s) -> {
            svincolati.append(s).append("\r\n");
        });

        svincolati.append("\r\n").append("Difensori\r\n");
        aD.forEach((s) -> {
            svincolati.append(s).append("\r\n");
        });

        svincolati.append("\r\n").append("Centrocapisti\r\n");
        aC.forEach((s) -> {
            svincolati.append(s).append("\r\n");
        });

        svincolati.append("\r\n").append("Attaccanti\r\n");
        aA.forEach((s) -> {
            svincolati.append(s).append("\r\n");
        });

        log.info("Scrivo svincolati");
        scriviFile(svincolati.toString().getBytes(), "FantaSoccer-svincolati.csv");

    }

    private List<String> goGetInjured() throws UnirestException {
        List<String> ret = new ArrayList<>();

        log.info("Prendo la pagina degli infortunat");
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

    private List<String> getRigoristi() throws UnirestException {
        List<String> ret = new ArrayList<>();

        log.info("Prendo la pagina dei rigoristi");
        String rigoristiPage = getPage(SITEHOME + "/it/rubriche/573/i-rigoristi-della-serie-a-chi-scegliere-per-il-fantacalcio/");

        // Salvo l'infortunato
        Document doc = Jsoup.parse(rigoristiPage);
        Elements infoFantacalciatore = doc.getElementsByTag("strong");
        for (Element e : infoFantacalciatore) {
            String strong = e.text();
            if (strong.contains(":")) {
                strong = strong.substring(strong.indexOf(':') + 1);
            }
            strong = strong.replace("\\.", "")
                    .trim();
            if (strong.length() > 0) {
                ret.add(strong);
            }
        }

        ret.add("");

        return ret;
    }

    private List<Calciatore> getAllGiocatori(final List<String> aInj, final List<String> aRig) throws UnirestException {
        List<Calciatore> ret = new ArrayList<>();
        log.info("Prendo la pagina delle statistiche");
        String statistichePage = getPage(SITEHOME + "/it/statistiche/");

        int nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbGiornata\"");
        String select = "<option selected=\"selected\" value=\"";
        nPosFS1 = statistichePage.indexOf(select, nPosFS1);
        int nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + select.length());
        String giornata = statistichePage.substring(nPosFS1 + select.length(), nPosFS2);

        // https://www.fanta.soccer/ArchivioQuotazioni/QuotazioniExcel.aspx?lang=it&serie=A&stagione=2020-2021&giornata=4
        String fantaGiocatori = getPage(SITEHOME + "/ArchivioQuotazioni/QuotazioniExcel.aspx?lang=it&serie=A&stagione=2020-2021&giornata=" + giornata);

        Document docGiocatore = Jsoup.parse("<html><body>" + fantaGiocatori + "</body></html>");
        Elements tr = docGiocatore.getElementsByTag("tr");

        boolean skipFirst = true;
        for (Element e : tr) {
            Elements td = e.getElementsByTag("td");
            if (td.size() == 7) {
                if (skipFirst) {
                    skipFirst = false;
                } else {
                    String codice = "" + (Long.parseLong(td.get(0).text()) - 1000000);
                    String nome = td.get(1).text();// + " " + td.get(2).text();

                    String infortunato = "";
                    if (aInj.contains(codice)) {
                        infortunato = "INFORTUNATO";
                    }

                    String rigorista = "";
                    for (String rig : aRig) {
                        if (rig.toUpperCase().contains(nome.toUpperCase())) {
                            rigorista = "RIGORISTA";
                        }
                    }

                    Calciatore calciatore = new Calciatore();
                    calciatore.setCodice(codice);
                    calciatore.setNome(nome);
                    calciatore.setSquadra(td.get(3).text());
                    calciatore.setRuolo(td.get(4).text());
                    calciatore.setPresenze("0");
                    calciatore.setFantamedia("0");
                    calciatore.setInfortunato(infortunato);
                    calciatore.setRigorista(rigorista);
                    calciatore.setEvidenzia("");
                    ret.add(calciatore);
                }
            }
        }
        return ret;
    }

    private String getStatistiche(final boolean previous) throws UnirestException {
        log.info("Prendo la pagina delle statistiche");
        String statistichePage = getPage(SITEHOME + "/it/statistiche/");

        int nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbGiornata\"");
        String select = "<option selected=\"selected\" value=\"";
        nPosFS1 = statistichePage.indexOf(select, nPosFS1);
        int nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + select.length());
        String giornata = statistichePage.substring(nPosFS1 + select.length(), nPosFS2);
        if (previous) {
            giornata = "38";
        } else {
            // Il sito fornisce le statistiche sulla giornata precedente, quindi meglio prendere quelle della giornata successiva
            giornata = "" + (Long.parseLong(giornata) + 1);
        }

        nPosFS1 = statistichePage.indexOf("<select name=\"ctl00$MainContent$wuc_Default1$cmbStagione\"");
        select = "<option selected=\"selected\" value=\"";
        if (previous) {
            select = "<option value=\"";
        }
        nPosFS1 = statistichePage.indexOf(select, nPosFS1);
        nPosFS2 = statistichePage.indexOf("\"", nPosFS1 + select.length());
        String stagione = statistichePage.substring(nPosFS1 + select.length(), nPosFS2);

        String paginastatistiche = SITEHOME + "/it/statistiche/A/" + stagione + "/Tutti/Fantamedia/Full/fs/" + giornata + "/";
        log.info("Prendo le statitiche [{}]", paginastatistiche);
        return getPage(paginastatistiche);
    }

    private List<Calciatore> generaCalciatori(final String calciatori, final List<String> aInj, final List<String> aRig) {
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

            if (td.size() > 0) {
                String nome = td.get(1).text();
                int n1 = nome.indexOf("(");
                int n2 = nome.indexOf(")", n1);
                String ruolo = nome.substring(n1 + 1, n2);
                nome = nome.substring(0, n1).trim();

                String squadra = td.get(2).text();
                String fantamedia = td.get(3).text();
                String presenze = td.get(4).text();
                String evidenzia = "";

                int nPre;
                double nFM;
                try {
                    nPre = Double.valueOf(presenze).intValue();
                    nFM = Double.parseDouble(fantamedia.replace(",", "."));
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

                String codice = td.get(1).html();
                int n3 = codice.indexOf("/it/seriea/");
                int n4 = codice.indexOf("/", n3 + 12);
                codice = codice.substring(n3 + 11, n4);

                String infortunato = "";
                if (aInj.contains(codice)) {
                    infortunato = "INFORTUNATO";
                }

                String rigorista = "";
                for (String rig : aRig) {
                    if (rig.toUpperCase().contains(nome.toUpperCase())) {
                        rigorista = "RIGORISTA";
                    }
                }

                Calciatore calciatore = new Calciatore();
                calciatore.setCodice(codice);
                calciatore.setRuolo(ruolo);
                calciatore.setNome(nome);
                calciatore.setSquadra(squadra);
                calciatore.setFantamedia(fantamedia);
                calciatore.setPresenze(presenze);
                calciatore.setInfortunato(infortunato);
                calciatore.setRigorista(rigorista);
                calciatore.setEvidenzia(evidenzia);

                ret.add(calciatore);

            }
        }
        return ret;
    }

    private void mergeArray(final List<Calciatore> aAll, final List<Calciatore> corrente) {
        log.info("Aggiungo calciatori non presenti");

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

}
