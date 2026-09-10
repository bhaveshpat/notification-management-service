package com.meetbhavesh.notification.worker;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.Channel;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import com.meetbhavesh.notification.domain.enums.FailureReason;
import com.meetbhavesh.notification.domain.repository.AuditEventRepository;
import com.meetbhavesh.notification.domain.repository.DeliveryTaskRepository;
import com.meetbhavesh.notification.worker.provider.ChannelProvider;
import com.meetbhavesh.notification.worker.retry.RetryPolicy;
import com.meetbhavesh.notification.worker.status.NotificationStatusRollupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTaskPollerTest {

    @Mock private DeliveryTaskRepository deliveryTaskRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private ChannelProvider channelProvider;
    @Mock private RetryPolicy retryPolicy;
    @Mock private NotificationStatusRollupService rollupService;

    private DeliveryTaskPoller poller;

    @BeforeEach
    void setUp() {
        poller = new DeliveryTaskPoller(
                deliveryTaskRepository, auditEventRepository, channelProvider, retryPolicy, rollupService);
    }

    private DeliveryTask queuedTask() {
        return DeliveryTask.builder()
                .id("task-1")
                .notificationId("notif-1")
                .recipientId("user-1")
                .channel(Channel.EMAIL)
                .status(DeliveryTaskStatus.QUEUED)
                .attemptCount(0)
                .maxAttempts(5)
                .build();
    }

    @Test
    void successfulSend_marksTaskSucceededAndRollsUpStatus() {
        DeliveryTask task = queuedTask();
        when(deliveryTaskRepository.findDueForProcessing(anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(channelProvider.send(task)).thenReturn(ChannelProvider.Result.success("provider-msg-1"));
        when(deliveryTaskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));

        poller.pollAndProcess();

        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.SUCCEEDED);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getProviderMessageId()).isEqualTo("provider-msg-1");
        verify(rollupService).rollUp("notif-1");
    }

    @Test
    void retryableFailure_withAttemptsRemaining_schedulesRetry() {
        DeliveryTask task = queuedTask();
        when(deliveryTaskRepository.findDueForProcessing(anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(channelProvider.send(task)).thenReturn(ChannelProvider.Result.failure(FailureReason.TRANSIENT_PROVIDER_FAILURE));
        when(retryPolicy.isRetryable(FailureReason.TRANSIENT_PROVIDER_FAILURE)).thenReturn(true);
        when(retryPolicy.backoffFor(1)).thenReturn(Duration.ofSeconds(10));
        when(deliveryTaskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));

        poller.pollAndProcess();

        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.FAILED_RETRYABLE);
        assertThat(task.getNextAttemptAt()).isNotNull();
        assertThat(task.getLastFailureReason()).isEqualTo(FailureReason.TRANSIENT_PROVIDER_FAILURE);
    }

    @Test
    void nonRetryableFailure_goesStraightToTerminal_regardlessOfAttemptsRemaining() {
        DeliveryTask task = queuedTask();
        when(deliveryTaskRepository.findDueForProcessing(anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(channelProvider.send(task)).thenReturn(ChannelProvider.Result.failure(FailureReason.INVALID_RECIPIENT));
        when(retryPolicy.isRetryable(FailureReason.INVALID_RECIPIENT)).thenReturn(false);
        when(deliveryTaskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));

        poller.pollAndProcess();

        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.FAILED_TERMINAL);
        assertThat(task.getNextAttemptAt()).isNull();
    }

    @Test
    void retryableFailure_withAttemptsExhausted_goesTerminal() {
        DeliveryTask task = queuedTask();
        task.setAttemptCount(4); // this attempt becomes #5, the last one allowed
        task.setMaxAttempts(5);
        when(deliveryTaskRepository.findDueForProcessing(anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(channelProvider.send(task)).thenReturn(ChannelProvider.Result.failure(FailureReason.TRANSIENT_PROVIDER_FAILURE));
        when(retryPolicy.isRetryable(FailureReason.TRANSIENT_PROVIDER_FAILURE)).thenReturn(true);
        when(deliveryTaskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));

        poller.pollAndProcess();

        assertThat(task.getAttemptCount()).isEqualTo(5);
        assertThat(task.getStatus()).isEqualTo(DeliveryTaskStatus.FAILED_TERMINAL);
    }

    @Test
    void noDueTasks_doesNothing() {
        when(deliveryTaskRepository.findDueForProcessing(anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        poller.pollAndProcess();

        verifyNoInteractions(channelProvider, rollupService);
    }
}
