package com.meetbhavesh.notification.domain.repository;

import com.meetbhavesh.notification.domain.entity.Notification;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import com.meetbhavesh.notification.domain.enums.Priority;
import com.meetbhavesh.notification.domain.enums.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification newNotification(String idempotencyKey) {
        return Notification.builder()
                .idempotencyKey(idempotencyKey)
                .sourceSystem("test-system")
                .eventId("evt-1")
                .notificationType("TEST")
                .severity(Severity.MEDIUM)
                .priority(Priority.NORMAL)
                .recipients(List.of("user-1"))
                .requestedChannels(List.of(Channel.EMAIL))
                .build();
    }

    @Test
    void savesAndFindsById() {
        Notification saved = notificationRepository.save(newNotification("key-1"));

        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.RECEIVED);
        assertThat(found.get().getRecipients()).containsExactly("user-1");
    }

    @Test
    void findsByIdempotencyKey() {
        notificationRepository.save(newNotification("key-dedup"));

        assertThat(notificationRepository.findByIdempotencyKey("key-dedup")).isPresent();
        assertThat(notificationRepository.findByIdempotencyKey("no-such-key")).isEmpty();
    }

    @Test
    void rejectsDuplicateIdempotencyKeyAtTheDatabaseLevel() {
        notificationRepository.saveAndFlush(newNotification("dup-key"));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(newNotification("dup-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
