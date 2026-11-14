package com.sentinel.aml.detection_service.service.impl;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.detection_service.dto.AlertGeneratedEvent;
import com.sentinel.aml.detection_service.entity.AmlAlert;
import com.sentinel.aml.detection_service.service.AlertPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertPublisherServiceImpl implements AlertPublisherService {

    private final JmsTemplate jmsTemplate;

    @Override
    public void publishAlert(AmlAlert alert) {
        AlertGeneratedEvent event = new AlertGeneratedEvent(
                alert.getId(),
                alert.getTransactionId(),
                alert.getAccountId(),
                alert.getRuleTriggered(),
                alert.getAlertAmount(),
                alert.getCreatedAt().toString()
        );

        jmsTemplate.convertAndSend(MessagingConstants.ALERT_QUEUE, event);
        log.info("Published AlertGeneratedEvent to ActiveMQ queue '{}' for Alert ID: {}",
                MessagingConstants.ALERT_QUEUE, alert.getId());
    }
}