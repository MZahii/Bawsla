package tn.esprit.bawsla.forum.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DiscussionRequest;
import tn.esprit.bawsla.forum.dto.DiscussionResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;

@Service
@Transactional(readOnly = true)
public class DiscussionService {

    private final DiscussionRepository discussionRepository;

    public DiscussionService(DiscussionRepository discussionRepository) {
        this.discussionRepository = discussionRepository;
    }

    public List<DiscussionResponse> findAll(Long coursId) {
        List<Discussion> discussions = coursId == null
                ? discussionRepository.findAllByOrderByDateCreationDesc()
                : discussionRepository.findByCoursIdOrderByDateCreationDesc(coursId);
        return discussions.stream().map(DiscussionResponse::from).toList();
    }

    public DiscussionResponse findById(Long id) {
        return DiscussionResponse.from(getOrThrow(id));
    }

    /** Tout utilisateur connecté peut ouvrir une discussion. */
    @Transactional
    public DiscussionResponse create(DiscussionRequest request, CurrentUser user) {
        Discussion discussion = new Discussion();
        apply(discussion, request);
        discussion.setAuteurId(user.id());
        return DiscussionResponse.from(discussionRepository.save(discussion));
    }

    @Transactional
    public DiscussionResponse update(Long id, DiscussionRequest request, CurrentUser user) {
        Discussion discussion = getOrThrow(id);
        user.requireOwnerOrAdmin(discussion.getAuteurId());
        apply(discussion, request);
        return DiscussionResponse.from(discussion);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Discussion discussion = getOrThrow(id);
        user.requireOwnerOrAdmin(discussion.getAuteurId());
        discussionRepository.delete(discussion);
    }

    private Discussion getOrThrow(Long id) {
        return discussionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Discussion", id));
    }

    private static void apply(Discussion d, DiscussionRequest r) {
        d.setTitre(r.titre());
        d.setContenu(r.contenu());
        d.setCoursId(r.coursId());
    }
}
