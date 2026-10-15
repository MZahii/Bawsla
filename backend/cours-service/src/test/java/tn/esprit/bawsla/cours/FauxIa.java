package tn.esprit.bawsla.cours;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import feign.Request;
import tn.esprit.bawsla.cours.client.AudioExplicationIaDto;
import tn.esprit.bawsla.cours.client.AudioExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.ExplicationIaDto;
import tn.esprit.bawsla.cours.client.ExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto.NotionJustifieeDto;
import tn.esprit.bawsla.cours.client.FideliteIaRequestDto;
import tn.esprit.bawsla.cours.client.IaClient;
import tn.esprit.bawsla.cours.client.MinutageIaDto;
import tn.esprit.bawsla.cours.client.ResumeLocalIaDto;
import tn.esprit.bawsla.cours.client.ResumeLocalIaRequestDto;
import tn.esprit.bawsla.cours.client.NotionIaDto;
import tn.esprit.bawsla.cours.client.SegmentIaDto;
import tn.esprit.bawsla.cours.client.TraductionIaRequestDto;
import tn.esprit.bawsla.cours.client.VisionIaDto;
import tn.esprit.bawsla.cours.client.VisionIaRequestDto;
import tn.esprit.bawsla.cours.common.ApiResponse;

/**
 * IA simulée, partagée par les tests : réponses fixes, appels et en-têtes enregistrés, pannes et blocages programmables.
 * Aucun appel réseau.
 */
public class FauxIa implements IaClient {

    public static final byte[] MP3 = "ID3-mp3-de-test".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MP3_EXPLICATION = "ID3-mp3-explication-de-test".getBytes(StandardCharsets.UTF_8);

    // ---- fiche de révision
    public final List<String> autorisations = new CopyOnWriteArrayList<>();
    public final List<FicheIaRequestDto> demandesFiche = new CopyOnWriteArrayList<>();
    public final List<AudioIaRequestDto> demandesAudio = new CopyOnWriteArrayList<>();
    public volatile String resume = "Résumé généré";
    public volatile RuntimeException panneFiche;
    public volatile RuntimeException panneAudio;
    public volatile RuntimeException panneFidelite;
    public volatile CountDownLatch blocageFiche;
    public volatile String mimeAudio = "audio/mpeg";
    public volatile String fournisseurAudio = "elevenlabs";

    // ---- explication orale
    public final List<ExplicationIaRequestDto> demandesScript = new CopyOnWriteArrayList<>();
    public final List<TraductionIaRequestDto> demandesTraduction = new CopyOnWriteArrayList<>();
    public final List<FideliteExplicationIaRequestDto> demandesFideliteExplication = new CopyOnWriteArrayList<>();
    public final List<AudioExplicationIaRequestDto> demandesAudioExplication = new CopyOnWriteArrayList<>();
    /** Délai de lecture (ms) donné à chaque appel de l'explication, dans l'ordre : script, traduction, fidélité, audio. */
    public final List<String> delais = new CopyOnWriteArrayList<>();
    public volatile String texteScript = "Texte du script";
    public volatile RuntimeException panneScript;
    public volatile RuntimeException panneTraduction;
    public volatile RuntimeException panneFideliteExplication;
    public volatile RuntimeException panneAudioExplication;
    public volatile CountDownLatch blocageScript;
    public volatile CountDownLatch blocageAudioExplication;
    public volatile String fournisseurExplication = "gemini";
    public volatile boolean minutageEstime = true;

    // ---- lecture d'images par vision
    public final List<VisionIaRequestDto> demandesVision = new CopyOnWriteArrayList<>();
    public volatile String texteVision = "Texte lu par l'IA : une limite décrit le comportement d'une fonction près d'un point.";
    public volatile boolean pageVideVision;
    public volatile RuntimeException panneVision;
    /** La panne ne se déclenche qu'après ce nombre d'appels réussis (0 : dès le premier). */
    public volatile int panneVisionApres;
    /** 0 : Gemini lit toutes les pages ; n : à partir du n-ième appel (1 = le premier), Gemini est indisponible et le modèle local lit à sa place. */
    public volatile int localDepuis;
    public volatile CountDownLatch blocageVision;

    // ---- résumé automatique local
    public final List<ResumeLocalIaRequestDto> demandesResumeLocal = new CopyOnWriteArrayList<>();
    public volatile String langueResume = "en";
    public volatile String modeResume = "abstractif";
    public volatile RuntimeException panneResumeLocal;
    public volatile CountDownLatch blocageResumeLocal;

    public void reinitialiser() {
        autorisations.clear();
        demandesFiche.clear();
        demandesAudio.clear();
        resume = "Résumé généré";
        panneFiche = panneAudio = panneFidelite = null;
        blocageFiche = null;
        mimeAudio = "audio/mpeg";
        fournisseurAudio = "elevenlabs";

        demandesScript.clear();
        demandesTraduction.clear();
        demandesFideliteExplication.clear();
        demandesAudioExplication.clear();
        delais.clear();
        texteScript = "Texte du script";
        panneScript = panneTraduction = panneFideliteExplication = panneAudioExplication = null;
        blocageScript = blocageAudioExplication = null;
        fournisseurExplication = "gemini";
        minutageEstime = true;

        demandesVision.clear();
        texteVision = "Texte lu par l'IA : une limite décrit le comportement d'une fonction près d'un point.";
        pageVideVision = false;
        panneVision = null;
        panneVisionApres = 0;
        localDepuis = 0;
        blocageVision = null;

        demandesResumeLocal.clear();
        langueResume = "en";
        modeResume = "abstractif";
        panneResumeLocal = null;
        blocageResumeLocal = null;
    }

    // ------------------------------------------------------------------ fiche

    @Override
    public ApiResponse<FicheIaDto> fiche(String authorization, FicheIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        this.delais.add("fiche=" + delais.readTimeoutMillis());
        demandesFiche.add(corps);
        attendre(blocageFiche);
        if (panneFiche != null) {
            throw panneFiche;
        }
        FicheIaDto fiche = new FicheIaDto(resume, List.of("jointure", "LEFT JOIN"),
                List.of(new NotionIaDto("INNER JOIN", "Ne garde que les correspondances.", "ne garde que les lignes", true),
                        new NotionIaDto("Notion douteuse", "Inventée.", "phrase absente du cours", false)),
                corps.niveau(), 90, List.of("Notion « Notion douteuse » : extrait introuvable."), "v3", "gemini-test");
        return ApiResponse.ok(fiche);
    }

    @Override
    public ApiResponse<FideliteIaDto> fidelite(String authorization, FideliteIaRequestDto corps) {
        autorisations.add(authorization);
        if (panneFidelite != null) {
            throw panneFidelite;
        }
        List<NotionJustifieeDto> notions = corps.fiche().notions().stream()
                .map(n -> new NotionJustifieeDto(n.titre(), !n.titre().contains("suspecte"))).toList();
        return ApiResponse.ok(new FideliteIaDto(77, List.of("Alerte recalculée"), notions));
    }

    @Override
    public ResponseEntity<byte[]> audio(String authorization, AudioIaRequestDto corps) {
        autorisations.add(authorization);
        demandesAudio.add(corps);
        if (panneAudio != null) {
            throw panneAudio;
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, mimeAudio).header("X-TTS-Fournisseur", fournisseurAudio)
                .header("X-TTS-Duree-Ms", "12500").body(MP3);
    }

    // ------------------------------------------------------------------ explication orale

    @Override
    public ApiResponse<ExplicationIaDto> explication(String authorization, ExplicationIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        demandesScript.add(corps);
        this.delais.add("script=" + delais.readTimeoutMillis());
        attendre(blocageScript);
        if (panneScript != null) {
            throw panneScript;
        }
        List<String> alertes = new ArrayList<>();
        if (corps.notions().isEmpty()) {
            alertes.add("Notions choisies par l'IA : aucune fiche de révision validée ne fournit de plan pour ce cours.");
        }
        List<SegmentIaDto> segments = List.of(
                segment(1, "intro", "prof", "Bonjour. " + texteScript, null, null, null),
                segment(2, "explication", "prof", "Première idée. " + texteScript, "INNER JOIN", "ne garde que les lignes", 1),
                segment(3, "analogie", "prof", "Imagine une liste d'invités.", "INNER JOIN", null, null),
                segment(4, "exemple", "prof", "Un cas concret. " + texteScript, "INNER JOIN", "ne garde que les lignes", 1),
                segment(5, "question", corps.mode().equals("dialogue") ? "etudiant" : "prof", "Pourquoi ?", null, null, null),
                segment(6, "recap", "prof", "Retiens bien. " + texteScript, null, null, null));
        return ApiResponse.ok(new ExplicationIaDto(corps.langue(), corps.mode(), segments, 92, alertes, "explication_v1", "gemini-test", 40, 16,
                corps.notions().isEmpty()));
    }

    @Override
    public ApiResponse<ExplicationIaDto> traduireExplication(String authorization, TraductionIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        demandesTraduction.add(corps);
        this.delais.add("traduction=" + delais.readTimeoutMillis());
        if (panneTraduction != null) {
            throw panneTraduction;
        }
        // Comme l'IA : seul le texte change ; type, locuteur, notion, page et extrait FR sont recopiés.
        List<SegmentIaDto> segments = corps.segments().stream()
                .map(s -> new SegmentIaDto(s.ordre(), s.type(), s.locuteur(), s.texte() + " [EN]", s.notion(), s.extraitSource(), s.page(),
                        true, "analogie".equals(s.type())))
                .toList();
        return ApiResponse.ok(new ExplicationIaDto(corps.langueCible(), corps.mode(), segments, 88, List.of(), "traduction_v1", "gemini-test", 40, 16,
                corps.notions().isEmpty()));
    }

    @Override
    public ApiResponse<FideliteExplicationIaDto> fideliteExplication(String authorization, FideliteExplicationIaRequestDto corps,
                                                                      Request.Options delais) {
        autorisations.add(authorization);
        demandesFideliteExplication.add(corps);
        this.delais.add("fidelite=" + delais.readTimeoutMillis());
        if (panneFideliteExplication != null) {
            throw panneFideliteExplication;
        }
        List<SegmentIaDto> segments = corps.segments().stream()
                .map(s -> new SegmentIaDto(s.ordre(), s.type(), s.locuteur(), s.texte(), s.notion(), s.extraitSource(),
                        s.extraitSource() == null ? null : 1, !s.texte().contains("inventé"), "analogie".equals(s.type())))
                .toList();
        return ApiResponse.ok(new FideliteExplicationIaDto(70, List.of("Alerte recalculée"), segments, 40, 16));
    }

    @Override
    public ApiResponse<AudioExplicationIaDto> audioExplication(String authorization, AudioExplicationIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        demandesAudioExplication.add(corps);
        this.delais.add("audio=" + delais.readTimeoutMillis());
        attendre(blocageAudioExplication);
        if (panneAudioExplication != null) {
            throw panneAudioExplication;
        }
        List<MinutageIaDto> minutage = new ArrayList<>();
        for (int i = 0; i < corps.segments().size(); i++) {
            minutage.add(new MinutageIaDto(corps.segments().get(i).ordre(), i * 10_000L, (i + 1) * 10_000L - 500));
        }
        return ApiResponse.ok(new AudioExplicationIaDto(fournisseurExplication, "audio/mpeg",
                Base64.getEncoder().encodeToString(MP3_EXPLICATION), corps.segments().size() * 10_000L, minutageEstime, minutage));
    }

    @Override
    public ApiResponse<VisionIaDto> vision(String authorization, VisionIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        this.delais.add("vision=" + delais.readTimeoutMillis());
        attendre(blocageVision);
        if (panneVision != null && demandesVision.size() >= panneVisionApres) {
            throw panneVision;
        }
        demandesVision.add(corps);
        if (corps.forcerLocal() || (localDepuis > 0 && demandesVision.size() >= localDepuis)) {
            return ApiResponse.ok(new VisionIaDto(texteVision + " (page " + corps.numero() + ", locale)", false, "microsoft/Florence-2-base",
                    "local · Florence-2", "vision_v1"));
        }
        if (pageVideVision) {
            return ApiResponse.ok(new VisionIaDto("", true, "gemini-test", "gemini", "vision_v1"));
        }
        return ApiResponse.ok(new VisionIaDto(texteVision + " (page " + corps.numero() + ")", false, "gemini-test", "gemini", "vision_v1"));
    }

    @Override
    public ApiResponse<ResumeLocalIaDto> resumeLocal(String authorization, ResumeLocalIaRequestDto corps, Request.Options delais) {
        autorisations.add(authorization);
        demandesResumeLocal.add(corps);
        this.delais.add("resume=" + delais.readTimeoutMillis());
        attendre(blocageResumeLocal);
        if (panneResumeLocal != null) {
            throw panneResumeLocal;
        }
        boolean extractif = "extractif".equals(modeResume);
        List<ResumeLocalIaDto.PhraseIaDto> phrases = List.of(
                new ResumeLocalIaDto.PhraseIaDto("The observer pattern defines a one-to-many dependency.", "Observer: one-to-many dependency between objects", 39, true),
                new ResumeLocalIaDto.PhraseIaDto("Strategy lets algorithms vary.", "Strategy lets the algorithm vary independently", 33, extractif));
        return ApiResponse.ok(new ResumeLocalIaDto(langueResume, extractif ? "extractif-textrank" : "sshleifer/distilbart-cnn-12-6", modeResume,
                "The observer pattern defines a one-to-many dependency. Strategy lets algorithms vary.", phrases, 72, extractif ? 31 : null,
                extractif ? List.of("Résumé reformulé peu fidèle : remplacé par les phrases clés du cours") : List.of("1 phrase(s) du résumé ne ressemblent à aucune phrase du cours."),
                4200L));
    }

    private static SegmentIaDto segment(int ordre, String type, String locuteur, String texte, String notion, String extrait, Integer page) {
        return new SegmentIaDto(ordre, type, locuteur, texte, notion, extrait, page, extrait != null || !"explication".equals(type), "analogie".equals(type));
    }

    private static void attendre(CountDownLatch verrou) {
        if (verrou != null) {
            try {
                verrou.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
