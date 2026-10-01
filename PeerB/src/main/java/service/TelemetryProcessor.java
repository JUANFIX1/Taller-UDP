package service;

import model.TelemetryData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procesador de mensajes del protocolo de telemetría IoT sobre UDP.
 * 
 * Formato de mensajes recibidos:
 *   - Registro de telemetría: "DEVICE_ID;SENSOR_TYPE;VALUE" (ej. "sensor-01;TEMP;25.5")
 *   - Consulta de estado:      "STATUS;DEVICE_ID" (ej. "STATUS;sensor-01")
 */
public class TelemetryProcessor {

    private final Map<String, TelemetryData> lastReadings = new ConcurrentHashMap<>();

    /**
     * Procesa un mensaje de texto recibido por UDP y devuelve la respuesta
     * correspondiente según las reglas del protocolo de telemetría.
     * 
     * @param rawMessage Mensaje en texto plano recibido en el datagrama UDP.
     * @return Respuesta que será enviada de regreso al cliente emisor.
     */
    public String process(String rawMessage) {
        // Paso 1.1: validar nulo / vacío
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "ERROR;INVALID_FORMAT";
        }

        // Paso 1.2: separar por ";" (-1 conserva campos vacíos al final)
        String[] parts = rawMessage.trim().split(";", -1);
        if (parts.length == 0) {
            return "ERROR;INVALID_FORMAT";
        }

        // Paso 1.3: consulta de estado
        if (parts[0].trim().equalsIgnoreCase("STATUS")) {
            if (parts.length != 2 || parts[1].trim().isEmpty()) {
                return "ERROR;INVALID_FORMAT";
            }
            String queriedId = parts[1].trim();
            TelemetryData data = lastReadings.get(queriedId);
            if (data == null) {
                return "ERROR;DEVICE_NOT_FOUND";
            }
            return "STATUS_OK;" + data.getDeviceId() + ";" + data.getSensorType() + ";" + data.getValue();
        }

        // Paso 1.4: validar formato de telemetría
        if (parts.length != 3
                || parts[0].trim().isEmpty()
                || parts[1].trim().isEmpty()
                || parts[2].trim().isEmpty()) {
            return "ERROR;INVALID_FORMAT";
        }
        String deviceId = parts[0].trim();
        String sensorType = parts[1].trim();
        double value;
        try {
            value = Double.parseDouble(parts[2].trim());
        } catch (NumberFormatException e) {
            return "ERROR;INVALID_FORMAT";
        }

        // Paso 1.6 (parte 1): rechazar tipos no soportados antes de guardar
        if (!sensorType.equals("TEMP") && !sensorType.equals("HUMIDITY") && !sensorType.equals("BATTERY")) {
            return "ERROR;UNKNOWN_SENSOR_TYPE";
        }

        // Paso 1.5: guardar la lectura válida
        lastReadings.put(deviceId, new TelemetryData(deviceId, sensorType, value));

        // Paso 1.6 (parte 2): evaluar rangos
        switch (sensorType) {
            case "TEMP":
                if (value > 40.0) return "ALERT;HIGH_TEMPERATURE;" + value;
                if (value < 0.0)  return "ALERT;FREEZING_TEMPERATURE;" + value;
                return "OK;TEMP_RECORDED;" + value;
            case "HUMIDITY":
                if (value > 90.0) return "ALERT;HIGH_HUMIDITY;" + value;
                if (value < 20.0) return "ALERT;LOW_HUMIDITY;" + value;
                return "OK;HUMIDITY_RECORDED;" + value;
            default: // BATTERY
                if (value < 20.0) return "ALERT;LOW_BATTERY;" + value;
                return "OK;BATTERY_OK;" + value;
        }
    }

    public Map<String, TelemetryData> getLastReadings() {
        return lastReadings;
    }

    public void clear() {
        lastReadings.clear();
    }
}
