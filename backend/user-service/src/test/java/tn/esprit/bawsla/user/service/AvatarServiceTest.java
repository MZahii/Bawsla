package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.entity.Avatar;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.AvatarRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-13T10:00:00Z");
    private static final CurrentUser MOI = new CurrentUser(5L, CurrentUser.ETUDIANT);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    @Mock
    private AvatarRepository avatarRepository;

    @Mock
    private UserRepository userRepository;

    private AvatarService service() {
        return new AvatarService(avatarRepository, userRepository, Clock.fixed(T0, ZoneId.of("UTC")));
    }

    private User moi() {
        User u = new User();
        u.setId(5L);
        u.setNom("N");
        u.setPrenom("P");
        u.setEmail("p@x.com");
        u.setRole(Role.ETUDIANT);
        return u;
    }

    @Test
    void typeImage_reconnaitLesSignatures_etIgnoreLeReste() {
        assertThat(AvatarService.typeImage(PNG)).isEqualTo("image/png");
        assertThat(AvatarService.typeImage(JPEG)).isEqualTo("image/jpeg");
        assertThat(AvatarService.typeImage(WEBP)).isEqualTo("image/webp");
        assertThat(AvatarService.typeImage("<svg onload=alert(1)>".getBytes())).isNull();
        assertThat(AvatarService.typeImage("%PDF-1.7".getBytes())).isNull();
        assertThat(AvatarService.typeImage(new byte[0])).isNull();
    }

    @Test
    void enregistrer_stockeLImage_etMetAJourLaVersion() {
        User u = moi();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(avatarRepository.findById(5L)).thenReturn(Optional.empty());

        // Nom et Content-Type mensongers : seule la signature compte.
        UserDto dto = service().enregistrer(MOI, new MockMultipartFile("fichier", "photo.gif", "image/gif", PNG));

        assertThat(dto.avatarVersion()).isEqualTo(T0.toEpochMilli());
        verify(avatarRepository).save(org.mockito.ArgumentMatchers.argThat((Avatar a) ->
                a.getUserId() == 5L && "image/png".equals(a.getTypeMime()) && a.getContenu().length == PNG.length));
    }

    @Test
    void enregistrer_refuseUnFichierQuiNEstPasUneImage() {
        assertThatThrownBy(() -> service().enregistrer(MOI,
                new MockMultipartFile("fichier", "photo.png", "image/png", "pas une image".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PNG, JPEG ou WebP");
        verify(avatarRepository, never()).save(any());
    }

    @Test
    void enregistrer_refuseAuDelaDe2Mo() {
        byte[] gros = new byte[(int) AvatarService.TAILLE_MAX + 1];
        System.arraycopy(PNG, 0, gros, 0, PNG.length);

        assertThatThrownBy(() -> service().enregistrer(MOI, new MockMultipartFile("fichier", "a.png", "image/png", gros)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 Mo");
    }

    @Test
    void enregistrer_refuseUnFichierAbsentOuVide() {
        assertThatThrownBy(() -> service().enregistrer(MOI, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service().enregistrer(MOI, new MockMultipartFile("fichier", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void supprimer_effaceLaPhoto_etLaVersion() {
        User u = moi();
        u.setAvatarVersion(123L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        assertThat(service().supprimer(MOI).avatarVersion()).isNull();
        verify(avatarRepository).deleteById(5L);
    }
}
