package tn.esprit.bawsla.cours.entity;

/** Cycle de vie d'un cours : seul PUBLIE est visible par les apprenants. */
public enum StatutCours {
    BROUILLON,
    PUBLIE,
    ARCHIVE
}
