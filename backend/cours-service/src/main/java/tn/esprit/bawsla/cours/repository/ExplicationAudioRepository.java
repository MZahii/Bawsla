package tn.esprit.bawsla.cours.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.entity.StatutFiche;

public interface ExplicationAudioRepository extends JpaRepository<ExplicationAudio, Long> {

    /** La plus récente, quel que soit son statut (ce que voit le formateur). */
    Optional<ExplicationAudio> findFirstByCoursIdAndLangueAndModeOrderByIdDesc(Long coursId, LangueExplication langue, ModeExplication mode);

    /** La plus récente validée (ce que voit l'apprenant). */
    Optional<ExplicationAudio> findFirstByCoursIdAndLangueAndModeAndStatutOrderByIdDesc(
            Long coursId, LangueExplication langue, ModeExplication mode, StatutFiche statut);

    List<ExplicationAudio> findByCoursId(Long coursId);
}
