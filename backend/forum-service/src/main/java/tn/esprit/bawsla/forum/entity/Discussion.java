package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/**
 * Fil du forum : question, discussion libre ou annonce, rattaché à un cours (chapitre et page facultatifs).
 * coursId, chapitreId et auteurId sont des IDENTIFIANTS d'autres services : jamais de clé étrangère.
 * Les compteurs (score, nbReponses…) sont dénormalisés pour trier la liste sans jointure.
 * Index FULLTEXT(titre, contenu) créé au démarrage (JPA ne sait pas le déclarer).
 */
@Entity
@Table(name = "discussion",
        indexes = {
                @Index(name = "ix_discussion_cours", columnList = "coursId, derniereActiviteLe"),
                @Index(name = "ix_discussion_auteur", columnList = "auteurId"),
                @Index(name = "ix_discussion_activite", columnList = "derniereActiviteLe")})
public class Discussion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    private Long chapitreId;

    private Integer page;

    @Column(nullable = false, updatable = false)
    private Long auteurId;

    /** Masque l'auteur aux autres apprenants (le formateur du cours et l'admin le voient). */
    @Column(nullable = false)
    private boolean anonyme;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TypeDiscussion type;

    @Column(nullable = false, length = 200)
    private String titre;

    /** Markdown. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StatutDiscussion statut = StatutDiscussion.OUVERTE;

    @Column(nullable = false)
    private boolean epinglee;

    @Column(nullable = false)
    private boolean verrouillee;

    @Column(nullable = false)
    private boolean masquee;

    /** Entrée de FAQ du cours (fil résolu transformé par le formateur). */
    @Column(nullable = false)
    private boolean faq;

    private Long reponseAccepteeId;

    /** Doublon fusionné : la discussion devient un lien vers l'originale. */
    private Long fusionneeDansId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int nbReponses;

    @Column(nullable = false)
    private int nbVues;

    @Column(nullable = false)
    private int nbAbonnes;

    @Column(nullable = false)
    private LocalDateTime derniereActiviteLe;

    /** Auteur de la dernière activité (« Imen a répondu il y a 2 h »). */
    private Long dernierActeurId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    /** Dernière édition du titre ou du contenu par l'auteur (mention « modifié »). */
    private LocalDateTime modifieLe;

    /** Suppression logique. */
    private LocalDateTime supprimeeLe;

    /** Vecteur normalisé (norme 1) du titre et du début du contenu. */
    @Convert(converter = VecteurConverter.class)
    @Column(columnDefinition = "BLOB")
    private float[] embedding;

    @Column(length = 80)
    private String modeleEmbedding;

    @Column(length = 10)
    private String langue;

    @ManyToMany
    @JoinTable(name = "discussion_etiquette",
            joinColumns = @JoinColumn(name = "discussion_id"),
            inverseJoinColumns = @JoinColumn(name = "etiquette_id"))
    private Set<Etiquette> etiquettes = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
        if (derniereActiviteLe == null) {
            derniereActiviteLe = creeLe;
        }
    }

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getChapitreId() { return chapitreId; }
    public void setChapitreId(Long chapitreId) { this.chapitreId = chapitreId; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Long getAuteurId() { return auteurId; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }

    public boolean isAnonyme() { return anonyme; }
    public void setAnonyme(boolean anonyme) { this.anonyme = anonyme; }

    public TypeDiscussion getType() { return type; }
    public void setType(TypeDiscussion type) { this.type = type; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public StatutDiscussion getStatut() { return statut; }
    public void setStatut(StatutDiscussion statut) { this.statut = statut; }

    public boolean isEpinglee() { return epinglee; }
    public void setEpinglee(boolean epinglee) { this.epinglee = epinglee; }

    public boolean isVerrouillee() { return verrouillee; }
    public void setVerrouillee(boolean verrouillee) { this.verrouillee = verrouillee; }

    public boolean isMasquee() { return masquee; }
    public void setMasquee(boolean masquee) { this.masquee = masquee; }

    public boolean isFaq() { return faq; }
    public void setFaq(boolean faq) { this.faq = faq; }

    public Long getReponseAccepteeId() { return reponseAccepteeId; }
    public void setReponseAccepteeId(Long reponseAccepteeId) { this.reponseAccepteeId = reponseAccepteeId; }

    public Long getFusionneeDansId() { return fusionneeDansId; }
    public void setFusionneeDansId(Long fusionneeDansId) { this.fusionneeDansId = fusionneeDansId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getNbReponses() { return nbReponses; }
    public void setNbReponses(int nbReponses) { this.nbReponses = nbReponses; }

    public int getNbVues() { return nbVues; }
    public void setNbVues(int nbVues) { this.nbVues = nbVues; }

    public int getNbAbonnes() { return nbAbonnes; }
    public void setNbAbonnes(int nbAbonnes) { this.nbAbonnes = nbAbonnes; }

    public LocalDateTime getDerniereActiviteLe() { return derniereActiviteLe; }
    public void setDerniereActiviteLe(LocalDateTime derniereActiviteLe) { this.derniereActiviteLe = derniereActiviteLe; }

    public Long getDernierActeurId() { return dernierActeurId; }
    public void setDernierActeurId(Long dernierActeurId) { this.dernierActeurId = dernierActeurId; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }

    public LocalDateTime getModifieLe() { return modifieLe; }
    public void setModifieLe(LocalDateTime modifieLe) { this.modifieLe = modifieLe; }

    public LocalDateTime getSupprimeeLe() { return supprimeeLe; }
    public void setSupprimeeLe(LocalDateTime supprimeeLe) { this.supprimeeLe = supprimeeLe; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getModeleEmbedding() { return modeleEmbedding; }
    public void setModeleEmbedding(String modeleEmbedding) { this.modeleEmbedding = modeleEmbedding; }

    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }

    public Set<Etiquette> getEtiquettes() { return etiquettes; }
    public void setEtiquettes(Set<Etiquette> etiquettes) { this.etiquettes = etiquettes; }
}
