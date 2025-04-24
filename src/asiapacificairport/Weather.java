package asiapacificairport;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Weather extends Thread {
    public enum WeatherCondition {
        SUNNY("Sunny"),
        RAINY("Rainy"),
        THUNDERSTORM("Thunderstorm"),
        SNOWY("Snowy");

        private final String name;

        WeatherCondition(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Weather state variables
    private static volatile WeatherCondition currentWeather = WeatherCondition.SUNNY;
    private static final Random rand = new Random();

    // Locks and conditions for weather changes
    private static final ReentrantLock weatherLock = new ReentrantLock();
    private static final Condition weatherChange = weatherLock.newCondition();
    private static volatile boolean weatherChangeInProgress = false;

    // Weather check delays based on condition
    private static final int RAINY_DELAY = 4000; // 4 seconds
    private static final int SNOWY_DELAY = 4000; // 4 seconds
    private static final int THUNDERSTORM_DELAY = 7000; // 7 seconds
    private static final int NO_DELAY = 0; // No delay for sunny weather

    // Get current weather condition
    public static WeatherCondition getCurrentWeather() {
        return currentWeather;
    }

    // Check if weather permits immediate landing or departure
    public static boolean isFavorableWeather() {
        return currentWeather == WeatherCondition.SUNNY;
    }

    // Get delay time based on current weather
    public static int getWeatherDelay() {
        switch (currentWeather) {
            case RAINY:
                return RAINY_DELAY;
            case SNOWY:
                return SNOWY_DELAY;
            case THUNDERSTORM:
                return THUNDERSTORM_DELAY;
            default:
                return NO_DELAY;
        }
    }

    // Wait for weather if necessary (for landing)
    public static void waitForWeatherLanding(String planeID, boolean isEmergency) throws InterruptedException {
        weatherLock.lock();
        try {
            if (!isFavorableWeather() && !isEmergency) {
                int delay = getWeatherDelay();
                System.out.println("ATC     : Plane " + planeID + " holding position due to " + currentWeather +
                        " weather for " + (delay/1000) + " seconds");
                Thread.sleep(delay);
                System.out.println("ATC     : Plane " + planeID + " cleared to land after weather delay");
            }
        } finally {
            weatherLock.unlock();
        }
    }

    // Wait for weather if necessary (for departure)
    public static void waitForWeatherDeparture(String planeID) throws InterruptedException {
        weatherLock.lock();
        try {
            if (!isFavorableWeather()) {
                int delay = getWeatherDelay();
                System.out.println("ATC     : Plane " + planeID + " departure delayed due to " + currentWeather +
                        " weather for " + (delay/1000) + " seconds");
                Thread.sleep(delay);
                System.out.println("ATC     : Plane " + planeID + " cleared for departure after weather delay");
            }
        } finally {
            weatherLock.unlock();
        }
    }

    // Change weather with notification
    private void changeWeather() {
        weatherLock.lock();
        try {
            // Select new weather randomly
            WeatherCondition newWeather;
            do {
                newWeather = WeatherCondition.values()[rand.nextInt(WeatherCondition.values().length)];
            } while (newWeather == currentWeather); // Ensure weather actually changes

            weatherChangeInProgress = true;

            // Print divider and weather change notification
            System.out.println("\n-----Weather Update: " + newWeather + "-----\n");

            // Update current weather
            currentWeather = newWeather;

            // Signal all waiting threads
            weatherChange.signalAll();
            weatherChangeInProgress = false;
        } finally {
            weatherLock.unlock();
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Sleep for random time between 4-7 seconds
                int sleepTime = 4000 + rand.nextInt(3000);
                Thread.sleep(sleepTime);

                // Change weather
                changeWeather();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Weather monitoring interrupted");
        }
    }
}
