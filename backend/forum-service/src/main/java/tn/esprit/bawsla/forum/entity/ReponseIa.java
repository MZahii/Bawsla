package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.ModeVigie;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;

/**
 * Réponse de La Vigie : dans un fil (discussionId) ou dans l'assistant privé (conversationId).
 * niveau COURS / FORUM : sourcée et vérifiée ; GENERAL : sans source, uniquement à la demande de l'apprenant.
 * La confiance est CALCULÉE (récupération, part citée, citations vérifiées), jamais déclarée par le modèle.
 */
@Entity
@Table(name = "reponse_ia",
        indexes = {
                @Index(name = "ix_reponse_ia_discussion", columnList = "discussionId"),
                @Index(name = "ix_reponse_ia_conversation", columnList = "conversationId"),
                @Index(name = "ix_reponse_ia_cours", columnList = "coursId, creeLe")})
public class ReponseIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long discussionId;

    private Long conversationId;

    @Column(nullable = false)
    private Long coursId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private NiveauReponseIa niveau;

    @Column(columnDefinition = "TEXT")
    private String texte;

    /** Sources citées, en JSON (cours, chapitre, page, passage, citation). */
    @Column(columnDefinition = "TEXT")
    private String sourcesJson;

    /** Indices du mode Guide-moi, en JSON. */
    @Column(columnDefinition = "TEXT")
    private String indicesJson;

    /** 0 à 100 ; null pour une réponse générale (jamais de confiance affichée). */
    private Integer confiance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StatutReponseIa statut = StatutReponseIa.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ModeVigie mode = ModeVigie.DIRECTE;

    /** Correction du formateur ; le texte d'origine est gardé. */
    @Column(columnDefinition = "TEXT")
    private String texteCorrige;

    private Long corrigeeParId;

    private LocalDateTime corrigeeLe;

    @Column(nullable = false)
    private int utileOui;

    @Column(nullable = false)
    private int utileNon;

    @Column(length = 20)
    private String versionPrompt;

    @Column(length = 80)
    private String modele;

    @Column(length = 20)
    private String fournisseur;

    private Long dureeMs;

    private Integer jetonsEstimes;

    @Column(length = 300)
    private String erreur;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public NiveauReponseIa getNiveau() { return niveau; }
    public void setNiveau(NiveauReponseIa niveau) { this.niveau = niveau; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }

    public String getIndicesJson() { return indicesJson; }
    public void setIndicesJson(String indicesJson) { this.indicesJson = indicesJson; }

    public Integer getConfiance() { return confiance; }
    public void setConfiance(Integer confiance) { this.confiance = confiance; }

    public StatutReponseIa getStatut() { return statut; }
    public void setStatut(StatutReponseIa statut) { this.statut = statut; }

    public ModeVigie getMode() { return mode; }
    public void setMode(ModeVigie mode) { this.mode = mode; }

    public String getTexteCorrige() { return texteCorrige; }
    public void setTexteCorrige(String texteCorrige) { this.texteCorrige = texteCorrige; }

    public Long getCorrigeeParId() { return corrigeeParId; }
    public void setCorrigeeParId(Long corrigeeParId) { this.corrigeeParId = corrigeeParId; }

    public LocalDateTime getCorrigeeLe() { return corrigeeLe; }
    public void setCorrigeeLe(LocalDateTime corrigeeLe) { this.corrigeeLe = corrigeeLe; }

    public int getUtileOui() { return utileOui; }
    public void setUtileOui(int utileOui) { this.utileOui = utileOui; }

    public int getUtileNon() { return utileNon; }
    public void setUtileNon(int utileNon) { this.utileNon = utileNon; }

    public String getVersionPrompt() { return versionPrompt; }
    public void setVersionPrompt(String versionPrompt) { this.versionPrompt = versionPrompt; }

    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }

    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }

    public Long getDureeMs() { return dureeMs; }
    public void setDureeMs(Long dureeMs) { this.dureeMs = dureeMs; }

    public Integer getJetonsEstimes() { return jetonsEstimes; }
    public void setJetonsEstimes(Integer jetonsEstimes) { this.jetonsEstimes = jetonsEstimes; }

    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}
