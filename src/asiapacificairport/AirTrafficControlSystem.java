package asiapacificairport;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class AirTrafficControlSystem {
    private static final int TOTAL_PLANES = 6;
    private static final int MAX_PASSENGERS = 50;
    private static final Random rand = new Random();
    private static CountDownLatch planeCompletionLatch;

    public static void main(String[] args) {
        planeCompletionLatch = new CountDownLatch(TOTAL_PLANES);

        System.out.println("*** Asia Pacific Airport Simulation Started ***\n");
        System.out.println("Initializing airport with 3 gates and 1 runway...\n");
        System.out.println("Current Weather: " + Weather.getCurrentWeather() + "\n");

        // Start the weather monitoring system
        Weather weatherMonitor = new Weather();
        weatherMonitor.setDaemon(true); // Set as daemon so it terminates with main thread
        weatherMonitor.start();

        // Create and start plane threads with completion tracking
        for (int i = 1; i <= TOTAL_PLANES; i++) {
            int passengers = rand.nextInt(MAX_PASSENGERS) + 1; // 1–50 passengers
            boolean emergency = (i == 5); // 5th plane has emergency landing (for congested scenario)

            PlaneWithCompletion plane = new PlaneWithCompletion("P" + i, passengers, emergency, planeCompletionLatch);
            plane.start();

            try {
                // If we're about to create plane 5 (emergency), ensure we have congestion
                if (i == 4) {
                    System.out.println("ATC     : Two planes approaching the airport");
                    Thread.sleep(1000); // Ensure some congestion before emergency plane arrives
                } else {
                    Thread.sleep(rand.nextInt(2000)); // Random arrival every 0–2 seconds
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for all planes to complete
        try {
            planeCompletionLatch.await();

            // Give small delay to ensure all operations finish
            Thread.sleep(500);

            // Print statistics
            System.out.println("\n***All planes processed, generating report...***");
            AsiaPacificAirport.printStatistics();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("ATC     : Simulation interrupted");
        }
    }
    
    // Inner class to track plane completion
    private static class PlaneWithCompletion extends Plane {
        private CountDownLatch completionLatch;
        
        public PlaneWithCompletion(String planeID, int passengers, boolean emergency, CountDownLatch latch) {
            super(planeID, passengers, emergency);
            this.completionLatch = latch;
        }
        
        @Override
        public void run() {
            try {
                super.run();
            } finally {
                completionLatch.countDown();
            }
        }
    }
}
