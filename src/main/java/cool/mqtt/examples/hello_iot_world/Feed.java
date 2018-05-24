package cool.mqtt.examples.hello_iot_world;

import org.eclipse.paho.client.mqttv3.*;
import java.util.concurrent.*;

/** The feed task for publishing telemetry data at fixed rate to the MQTT broker. */
public class Feed implements Runnable {

  /** Speed limit in Km/h to determine speed variation range */
  private static final int SPEED_LIMIT_KMH = 135;

  /** Base speed limit to determine speed variation range */
  private static final int BASE_SPEED_KMH = 215;

  /** RPM base value */
  private static final int BASE_RPM = 2000;

  /** Fixed thresholds to determine RPM calculation */
  private static final int[] GEAR_THRESHOLDS = { 90, 150, 220, 300, 320 };

  /** Gear ratios to determine RPM calculation */
  private static final int[] GEAR_RATIOS = { 250, 400, 300, 250, 1000, 660 };

  /** Initial simulated speed in Km/h, also to used to save last determined speed */
  private long lastSpeedKmH = 130;

  /** Reference to the MqttClient instance connected to the MQTT broker */
  private MqttClient client;

  Feed(MqttClient client) {
    this.client = client;
  }

  /**
   * Utility method for converting a long value into a string representation to be
   * used as the message payload.
   *
   * @param value
   * @return
   */
  static byte[] toBytes(long value) {
    return String.valueOf(value).getBytes();
  }

  @Override
  public void run() {
    long speedKmH = simulateSpeed();
    long rpm = simulateRPM(speedKmH);

    // Publish data to the MQTT broker.
    try {
      client.publish("telemetry/speed", toBytes(speedKmH), 0, false);
      client.publish("telemetry/rpm", toBytes(rpm), 0, false);
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  /**
   * Calculates and returns the simulated speed.
   *
   * @return the speed
   */
  long simulateSpeed() {
    // Simulate the speed variation.
    double ratio = (double) (lastSpeedKmH - BASE_SPEED_KMH) / SPEED_LIMIT_KMH;
    int direction = 1;
    if (ratio < 0) {
      direction = -1;
    }
    ratio = Math.min(Math.abs(ratio), 1);
    double weight = (ratio * ratio * ratio);
    double prob = (1 - weight) / 2;
    if (!(Math.random() < prob)) {
      direction = direction * -1;
    }
    long difference = Math.round(Math.random() * 3) * direction;

    // Get current speed and save it for next simulation.
    long speedKmH = lastSpeedKmH + difference;
    lastSpeedKmH = speedKmH;
    return speedKmH;
  }

  /**
   * Calculates and returns the simulated engine RPM.
   *
   * @return the RPM
   */
  long simulateRPM(long speedKmH) {
    // Calculate current RPM.
    long diff = speedKmH;
    int i = 0;
    for (i = 0; i < GEAR_THRESHOLDS.length; i++) {
      if (speedKmH < GEAR_THRESHOLDS[i]) {
        break;
      }
    }
    if (i > 0) {
      diff = speedKmH - GEAR_THRESHOLDS[i - 1];
    }
    return BASE_RPM + GEAR_RATIOS[i] * diff;
  }

  public static void main(String[] args) throws MqttException {
    if (args.length == 0) {
      System.err.println("Please specifiy a validr broker url");
      System.exit(1);
    }

    // Create a client connection to the MQTT broker running at the specified
    // url
    String brokerUrl = args[0];
    MqttClient client = new MqttClient(brokerUrl, "telemetry-feed");
    client.connect();

    // Once connected, generate and publish simulated telemetry data every 100 ms
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(new Feed(client), 0, 100, TimeUnit.MILLISECONDS);
  }
}
