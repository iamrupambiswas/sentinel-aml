package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.entity.AmlAlert;

public interface AlertPublisherService {
    void publishAlert(AmlAlert alert);
}