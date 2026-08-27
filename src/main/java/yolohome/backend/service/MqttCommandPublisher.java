package yolohome.backend.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import yolohome.backend.config.MqttProperties;
import yolohome.backend.dto.DeviceCommandRequest;
import yolohome.backend.dto.DeviceCommandResponse;
import yolohome.backend.exception.MqttPublishException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publish lenh dieu khien (bat/tat LED, Fan) xuong MQTT broker de Yolo:Bit nhan va thuc thi.
 * Backend KHONG ghi truc tiep led_state/fan_state vao DB - trang thai thuc te van do
 * Python insert lai sau khi thiet bi phan hoi, giu nguyen luong du lieu hien tai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttCommandPublisher {

    private final MqttClient mqttClient;
    private final MqttConnectOptions connectOptions;
    private final MqttProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceCommandResponse publishCommand(String deviceId, DeviceCommandRequest command) {
        String topic = properties.resolveCommandTopic(deviceId);
        String payload = buildPayload(command);

        try {
            ensureConnected();

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1); // dam bao broker/thiet bi nhan duoc it nhat 1 lan
            mqttClient.publish(topic, message);

            log.info("Da publish lenh toi topic {}: {}", topic, payload);
            return new DeviceCommandResponse(deviceId, topic, payload, "SENT");

        } catch (MqttException e) {
            log.error("Publish MQTT that bai cho device {}: {}", deviceId, e.getMessage(), e);
            throw new MqttPublishException(
                    "Khong the gui lenh toi thiet bi " + deviceId + ": " + e.getMessage(), e);
        }
    }

    private void ensureConnected() throws MqttException {
        if (!mqttClient.isConnected()) {
            synchronized (this) {
                if (!mqttClient.isConnected()) {
                    log.info("MQTT client chua ket noi, dang ket noi toi {}", properties.getBrokerUri());
                    mqttClient.connect(connectOptions);
                }
            }
        }
    }

    private String buildPayload(DeviceCommandRequest command) {
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            if (command.ledState() != null) {
                fields.put("led_state", command.ledState());
            }
            if (command.fanState() != null) {
                fields.put("fan_state", command.fanState());
            }
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the serialize lenh dieu khien", e);
        }
    }
}
