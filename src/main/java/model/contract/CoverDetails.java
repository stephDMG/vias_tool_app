package model.contract;

/**
 * CoverDetails
 * <p>
 * Dieses POJO bündelt die Detailinformationen eines einzelnen COVER-Vertrages
 * (strukturierte Zusammenfassung der Blöcke 1–7 aus LU_ALLE inkl. relevanter
 * Felder und Metadaten). Es dient als Zielobjekt für Detailansichten, Exporte
 * und weiterführende Analysen im Bereich „Vertrags- und Angebotsmanagement“.
 * <p>
 * Hinweis:
 * - Typen sind zunächst String/primitive Platzhalter; falls notwendig,
 * können Datums- und Zahlenfelder später auf geeignetere Typen umgestellt werden.
 * - Text-/Label-Auflösung (z. B. über Dictionaries) erfolgt in Service/Formatter.
 */
public class CoverDetails {

    // ---------------------------
    // Block 1 – Basis/Art/Risiko
    // ---------------------------
    private String vsn;                // Versicherungsschein-Nr (VSN)
    private String vsnVr;              // VSN-Versicherer (VSN_VR)
    private String vsnMakler;          // VSN-Makler (VSN_MAKLER)
    private String risId;              // Versicherungsart (RIS_ID)
    private String risText;            // Versicherungsart Text (RIS)
    private String art;                // Vertragsart Schlüssel (ART)
    private String artText;            // Vertragsart Text (ART_Text)
    private String bausteinRis;        // Baustein Typ (BAUST_RIS)

    // ---------------------------
    // Block 2 – Laufzeit/Status
    // ---------------------------
    private String neuvertragJn;       // NEUVERTRAG_JN (1/0)
    private String beg;                // Beginn (LU_BEG)
    private String begUhr;             // BEGUHR
    private String abl;                // Ablauf (LU_ABL)
    private String ablUhr;             // ABLUHR
    private String hfl;                // Hauptfälligkeit (HFL)
    private String lfz;                // Laufzeit in Jahren (LFZ)
    private String vertragArt;         // Laufender Vertrag / Art (VERTRAG_ART)
    private String a99Jn;              // Buchung nach Ablaufdatum (A99_JN)
    private String versGrund;          // Versionierungsgrund (VERS_GRUND)
    private String flg;                // Version von (FLG)
    private String flz;                // Version bis (FLZ)
    private String status;             // Status

    // ---------------------------------------------
    // Block 3 – Zusatzinfo bei CS-Beteiligungsgeschäft
    // ---------------------------------------------
    private String vufKdr;             // Führender Versicherer (VUF_KDR)
    private String fufAnteil;          // Anteil der VR % (FUF_ANTEIL)
    private String vufBem;             // Bemerkung (VUF_BEM)
    private String vuaKdr;             // Assekuradeur (VUA_KDR)
    private String vuaAnteil;          // Anteil Assek. % (VUA_ANTEIL)
    private String vuaBem;             // Bemerkung (VUA_BEM)

    // ---------------------------
    // Block 4 – Vorgang & Partner
    // ---------------------------
    private String vorgangId;          // VORGANG_ID
    private String ges;                // Gesellschaft Code (GES)
    private String gesText;            // Gesellschaft Name (GES_Text)
    private String vmt;                // Makler (VMT)
    private String vmt2;               // Altmakler (VMT2)
    private String courtageSatz;       // Courtagesatz (180)
    private String agt;                // Prov. Vereinbarung (AGT)

    // -------------------------------------------------
    // Block 5 – Status/Pool/Fronting/Bearbeitungsstand
    // -------------------------------------------------
    private String betStat;            // Beteiligungsform (BET_STAT) → Dict: MAP_ALLE_BETSTAT
    private String poolNr;             // POOLNR
    private String opz;                // Vertragsstatus (OPZ) → Dict: MAP_ALLE_OPZ
    private String sta;                // Vertragsstand (STA) → Dict: MAP_ALLE_STA
    private String frontingJn;         // FRONTING_JN
    private String baStand;            // Bearbeitungsstand (BASTAND)
    private String gBereich;           // OP-Gruppe (GBereich) → Dict: MAP_ALLE_GBEREICH
    private String kueFrivDat;         // Kündigungsfristverkürzung zum (KUEFRIV_DAT)
    private String kueFrivDurch;       // Veranlasst durch (KUEFRIV_DURCH)

    // -----------------------------------------
    // Block 5 (Zuständigkeiten) – Sachbearbeiter
    // -----------------------------------------
    private String sbVertrag;          // SB Vertr. (VT)
    private String sbSchaden;          // SB Schad. (SC)
    private String sbRechnung;         // SB Rechnung (RG)
    private String sbGl;               // GL/Prokurist (GL)
    private String sbDoku;             // SB Doku (DOK)
    private String sbBuha;             // SB BuHa (BUH)

    // -----------------------------
    // Block 6 – Änderung/Storno
    // -----------------------------
    private String eda;                // Letzte Änderung (EDA)
    private String egr;                // Grund (EGR)
    private String sachbearbEgr;       // durch (SACHBEA_EGR)
    private String dst;                // Storno zum (DST)
    private String agr;                // Grund (AGR)
    private String sachbearbAgr;       // durch (SACHBEA_AGR)

    // -----------------------------
    // Block 7 – Wiedervorlagen
    // -----------------------------
    private String wvl1;               // Wiedervorlage 1 (WVL1)
    private String wvlg1;              // Grund 1 (WVLG1)
    private String sbWvg1;             // Durch 1 (SACHBEA_WVG1)
    private String wvg1Prio;           // Prio 1 (WVG1_PRIO)
    private String wvl2;               // Wiedervorlage 2 (WVL2)
    private String wvlg2;              // Grund 2 (WVLG2)
    private String sbWvg2;             // Durch 2 (SACHBEA_WVG2)
    private String wvg2Prio;           // Prio 2 (WVG2_PRIO)


    // ---------------------------
// Block 0 – Versicherungsnehmer
// ---------------------------
    private String insuredName;     // Name/Firma
    private String insuredFirstname;
    private String insuredName2;
    private String insuredName3;
    private String insuredStreet;
    private String insuredPlz;
    private String insuredCity;
    private String insuredNation;

    public String getInsuredName() {
        return insuredName;
    }

    public void setInsuredName(String insuredName) {
        this.insuredName = insuredName;
    }

    public String getInsuredFirstname() {
        return insuredFirstname;
    }

    public void setInsuredFirstname(String insuredFirstname) {
        this.insuredFirstname = insuredFirstname;
    }

    public String getInsuredName2() {
        return insuredName2;
    }

    public void setInsuredName2(String insuredName2) {
        this.insuredName2 = insuredName2;
    }

    public String getInsuredStreet() {
        return insuredStreet;
    }

    public void setInsuredStreet(String insuredStreet) {
        this.insuredStreet = insuredStreet;
    }

    public String getInsuredName3() {
        return insuredName3;
    }

    public void setInsuredName3(String insuredName3) {
        this.insuredName3 = insuredName3;
    }

    public String getInsuredPlz() {
        return insuredPlz;
    }

    public void setInsuredPlz(String insuredPlz) {
        this.insuredPlz = insuredPlz;
    }

    public String getInsuredCity() {
        return insuredCity;
    }

    public void setInsuredCity(String insuredCity) {
        this.insuredCity = insuredCity;
    }

    public String getInsuredNation() {
        return insuredNation;
    }

    public void setInsuredNation(String insuredNation) {
        this.insuredNation = insuredNation;
    }


    // --- Getter/Setter (aus Platzgründen gekürzt im Kommentar) ---

    public String getVsn() {
        return vsn;
    }

    public void setVsn(String vsn) {
        this.vsn = vsn;
    }

    public String getVsnVr() {
        return vsnVr;
    }

    public void setVsnVr(String vsnVr) {
        this.vsnVr = vsnVr;
    }

    public String getVsnMakler() {
        return vsnMakler;
    }

    public void setVsnMakler(String vsnMakler) {
        this.vsnMakler = vsnMakler;
    }

    public String getRisId() {
        return risId;
    }

    public void setRisId(String risId) {
        this.risId = risId;
    }

    public String getRisText() {
        return risText;
    }

    public void setRisText(String risText) {
        this.risText = risText;
    }

    public String getArt() {
        return art;
    }

    public void setArt(String art) {
        this.art = art;
    }

    public String getArtText() {
        return artText;
    }

    public void setArtText(String artText) {
        this.artText = artText;
    }

    public String getBausteinRis() {
        return bausteinRis;
    }

    public void setBausteinRis(String bausteinRis) {
        this.bausteinRis = bausteinRis;
    }

    public String getNeuvertragJn() {
        return neuvertragJn;
    }

    public void setNeuvertragJn(String neuvertragJn) {
        this.neuvertragJn = neuvertragJn;
    }

    public String getBeg() {
        return beg;
    }

    public void setBeg(String beg) {
        this.beg = beg;
    }

    public String getBegUhr() {
        return begUhr;
    }

    public void setBegUhr(String begUhr) {
        this.begUhr = begUhr;
    }

    public String getAbl() {
        return abl;
    }

    public void setAbl(String abl) {
        this.abl = abl;
    }

    public String getAblUhr() {
        return ablUhr;
    }

    public void setAblUhr(String ablUhr) {
        this.ablUhr = ablUhr;
    }

    public String getHfl() {
        return hfl;
    }

    public void setHfl(String hfl) {
        this.hfl = hfl;
    }

    public String getLfz() {
        return lfz;
    }

    public void setLfz(String lfz) {
        this.lfz = lfz;
    }

    public String getVertragArt() {
        return vertragArt;
    }

    public void setVertragArt(String vertragArt) {
        this.vertragArt = vertragArt;
    }

    public String getA99Jn() {
        return a99Jn;
    }

    public void setA99Jn(String a99Jn) {
        this.a99Jn = a99Jn;
    }

    public String getVersGrund() {
        return versGrund;
    }

    public void setVersGrund(String versGrund) {
        this.versGrund = versGrund;
    }

    public String getFlg() {
        return flg;
    }

    public void setFlg(String flg) {
        this.flg = flg;
    }

    public String getFlz() {
        return flz;
    }

    public void setFlz(String flz) {
        this.flz = flz;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVufKdr() {
        return vufKdr;
    }

    public void setVufKdr(String vufKdr) {
        this.vufKdr = vufKdr;
    }

    public String getFufAnteil() {
        return fufAnteil;
    }

    public void setFufAnteil(String fufAnteil) {
        this.fufAnteil = fufAnteil;
    }

    public String getVufBem() {
        return vufBem;
    }

    public void setVufBem(String vufBem) {
        this.vufBem = vufBem;
    }

    public String getVuaKdr() {
        return vuaKdr;
    }

    public void setVuaKdr(String vuaKdr) {
        this.vuaKdr = vuaKdr;
    }

    public String getVuaAnteil() {
        return vuaAnteil;
    }

    public void setVuaAnteil(String vuaAnteil) {
        this.vuaAnteil = vuaAnteil;
    }

    public String getVuaBem() {
        return vuaBem;
    }

    public void setVuaBem(String vuaBem) {
        this.vuaBem = vuaBem;
    }

    public String getVorgangId() {
        return vorgangId;
    }

    public void setVorgangId(String vorgangId) {
        this.vorgangId = vorgangId;
    }

    public String getGes() {
        return ges;
    }

    public void setGes(String ges) {
        this.ges = ges;
    }

    public String getGesText() {
        return gesText;
    }

    public void setGesText(String gesText) {
        this.gesText = gesText;
    }

    public String getVmt() {
        return vmt;
    }

    public void setVmt(String vmt) {
        this.vmt = vmt;
    }

    public String getVmt2() {
        return vmt2;
    }

    public void setVmt2(String vmt2) {
        this.vmt2 = vmt2;
    }

    public String getCourtageSatz() {
        return courtageSatz;
    }

    public void setCourtageSatz(String courtageSatz) {
        this.courtageSatz = courtageSatz;
    }

    public String getAgt() {
        return agt;
    }

    public void setAgt(String agt) {
        this.agt = agt;
    }

    public String getBetStat() {
        return betStat;
    }

    public void setBetStat(String betStat) {
        this.betStat = betStat;
    }

    public String getPoolNr() {
        return poolNr;
    }

    public void setPoolNr(String poolNr) {
        this.poolNr = poolNr;
    }

    public String getOpz() {
        return opz;
    }

    public void setOpz(String opz) {
        this.opz = opz;
    }

    public String getSta() {
        return sta;
    }

    public void setSta(String sta) {
        this.sta = sta;
    }

    public String getFrontingJn() {
        return frontingJn;
    }

    public void setFrontingJn(String frontingJn) {
        this.frontingJn = frontingJn;
    }

    public String getBaStand() {
        return baStand;
    }

    public void setBaStand(String baStand) {
        this.baStand = baStand;
    }

    public String getgBereich() {
        return gBereich;
    }

    public void setgBereich(String gBereich) {
        this.gBereich = gBereich;
    }

    public String getKueFrivDat() {
        return kueFrivDat;
    }

    public void setKueFrivDat(String kueFrivDat) {
        this.kueFrivDat = kueFrivDat;
    }

    public String getKueFrivDurch() {
        return kueFrivDurch;
    }

    public void setKueFrivDurch(String kueFrivDurch) {
        this.kueFrivDurch = kueFrivDurch;
    }

    public String getSbVertrag() {
        return sbVertrag;
    }

    public void setSbVertrag(String sbVertrag) {
        this.sbVertrag = sbVertrag;
    }

    public String getSbSchaden() {
        return sbSchaden;
    }

    public void setSbSchaden(String sbSchaden) {
        this.sbSchaden = sbSchaden;
    }

    public String getSbRechnung() {
        return sbRechnung;
    }

    public void setSbRechnung(String sbRechnung) {
        this.sbRechnung = sbRechnung;
    }

    public String getSbGl() {
        return sbGl;
    }

    public void setSbGl(String sbGl) {
        this.sbGl = sbGl;
    }

    public String getSbDoku() {
        return sbDoku;
    }

    public void setSbDoku(String sbDoku) {
        this.sbDoku = sbDoku;
    }

    public String getSbBuha() {
        return sbBuha;
    }

    public void setSbBuha(String sbBuha) {
        this.sbBuha = sbBuha;
    }

    public String getEda() {
        return eda;
    }

    public void setEda(String eda) {
        this.eda = eda;
    }

    public String getEgr() {
        return egr;
    }

    public void setEgr(String egr) {
        this.egr = egr;
    }

    public String getSachbearbEgr() {
        return sachbearbEgr;
    }

    public void setSachbearbEgr(String sachbearbEgr) {
        this.sachbearbEgr = sachbearbEgr;
    }

    public String getDst() {
        return dst;
    }

    public void setDst(String dst) {
        this.dst = dst;
    }

    public String getAgr() {
        return agr;
    }

    public void setAgr(String agr) {
        this.agr = agr;
    }

    public String getSachbearbAgr() {
        return sachbearbAgr;
    }

    public void setSachbearbAgr(String sachbearbAgr) {
        this.sachbearbAgr = sachbearbAgr;
    }

    public String getWvl1() {
        return wvl1;
    }

    public void setWvl1(String wvl1) {
        this.wvl1 = wvl1;
    }

    public String getWvlg1() {
        return wvlg1;
    }

    public void setWvlg1(String wvlg1) {
        this.wvlg1 = wvlg1;
    }

    public String getSbWvg1() {
        return sbWvg1;
    }

    public void setSbWvg1(String sbWvg1) {
        this.sbWvg1 = sbWvg1;
    }

    public String getWvg1Prio() {
        return wvg1Prio;
    }

    public void setWvg1Prio(String wvg1Prio) {
        this.wvg1Prio = wvg1Prio;
    }

    public String getWvl2() {
        return wvl2;
    }

    public void setWvl2(String wvl2) {
        this.wvl2 = wvl2;
    }

    public String getWvlg2() {
        return wvlg2;
    }

    public void setWvlg2(String wvlg2) {
        this.wvlg2 = wvlg2;
    }

    public String getSbWvg2() {
        return sbWvg2;
    }

    public void setSbWvg2(String sbWvg2) {
        this.sbWvg2 = sbWvg2;
    }

    public String getWvg2Prio() {
        return wvg2Prio;
    }

    public void setWvg2Prio(String wvg2Prio) {
        this.wvg2Prio = wvg2Prio;
    }
}
