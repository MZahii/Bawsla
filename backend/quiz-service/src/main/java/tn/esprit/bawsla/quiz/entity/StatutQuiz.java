package tn.esprit.bawsla.quiz.entity;

/** Un quiz (généré par l'IA ou saisi) reste en BROUILLON tant que le formateur ne l'a pas relu et publié. */
public enum StatutQuiz {
    BROUILLON,
    PUBLIE
}
