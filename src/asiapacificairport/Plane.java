package asiapacificairport;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Plane extends Thread {
    private String planeID;
    private int passengers;
    private boolean emergency;
    private boolean isLanded; // Initially in the air
    private long arrivalTime;
    private long landingTime;
    private long departureTime;
    private long requestTime;
    private long waitingTime;
    private int assignedGate = -1;
    private static final Random rand = new Random();
    private AtomicReference<String> operationSummary;
    private CountDownLatch operationsCompleted;

    // Constructor
    public Plane(String planeID, int passengers, boolean emergency) {
        this.planeID = planeID;
        this.passengers = passengers;
        this.emergency = emergency;
        this.isLanded = false;
        this.arrivalTime = System.currentTimeMillis();
        this.operationSummary = new AtomicReference<>("Requesting");
        this.operationsCompleted = new CountDownLatch(4); // 4 operations: passengers, refill, clean, refuel
        
        // Set higher priority for emergency planes
        if (emergency) {
            this.setPriority(Thread.MAX_PRIORITY);
        }
    }

    public String getID() {
        return planeID;
    }
    
    public boolean isEmergency() {
        return emergency;
    }
    
    public long getArrivalTime() {
        return arrivalTime;
    }

    // Request landing
    public void requestLanding() {
        System.out.println("Plane " + planeID + ": Requesting for landing in " +
                Weather.getCurrentWeather() + " weather" + (emergency ? " (EMERGENCY)" : ""));
        requestTime = System.currentTimeMillis();

        // Register with ATC for landing
        AsiaPacificAirport.addToLandingQueue(this);

        try {
            // Wait until ground and gates are available AND have permission to land
            boolean permissionGranted = false;
            while (!permissionGranted) {
                permissionGranted = AsiaPacificAirport.hasPermissionToLand(this);
                if (!permissionGranted) {
                    System.out.println("Plane " + planeID + ": Waiting in the air..." + (emergency ? " (EMERGENCY)" : ""));
                    Thread.sleep(2000); // Wait and retry
                }
            }

            // Check weather conditions and wait if necessary for landing
            Weather.waitForWeatherLanding(planeID, emergency);

            // Calculate waiting time only after permission has been granted and weather is clear
            waitingTime = System.currentTimeMillis() - requestTime;
            System.out.println("ATC     : Plane " + planeID + " cleared for landing (Wait Time: " + waitingTime + "ms)");

            // Acquire ground permit
            AsiaPacificAirport.planesOnGround.acquire();

            // Find and assign a gate
            assignedGate = AsiaPacificAirport.assignGate(planeID);
            updateOperationSummary(" - assigned to Gate " + (assignedGate + 1));

            // Land the plane
            land();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    // Emergency landing logic
    public void emergencyRequired() {
        if (!isLanded && emergency) {
            System.out.println("Plane " + planeID + ": EMERGENCY! Plane low fuel requiring emergency landing!!!");
            requestLanding(); // Proceed with landing request (priority handling in AsiaPacificAirport class)
        }
    }

    // Land on runway
    public void land() {
        try {
            AsiaPacificAirport.runway.acquire();
            System.out.println("Plane " + planeID + ": landing on runway...");
            landingTime = System.currentTimeMillis();
            Thread.sleep(1000); // Landing takes 1 second
            System.out.println("ATC     : Plane " + planeID + " landed successfully!");
            isLanded = true;
            updateOperationSummary(" - landed");
            
            AsiaPacificAirport.runway.release();
            
            // Acquire assigned gate
            System.out.println("Plane " + planeID + ": Coasting to Gate " + (assignedGate + 1));
            AsiaPacificAirport.gates[assignedGate].acquire();
            Thread.sleep(1000); // Coasting to gate takes 1 second
            System.out.println("Plane " + planeID + ": Docked at Gate " + (assignedGate + 1));
            System.out.println("ATC     : Plane " + planeID + " docked at Gate " + (assignedGate + 1));
            updateOperationSummary(" - docked at Gate " + (assignedGate + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Refill supplies and cleaning (concurrent operation)
    public void refillSupplies() {
        if (isLanded) {
            System.out.println("Airport : Refilling supplies for Plane " + planeID);
            try {
                Thread.sleep(1000); // Refilling takes 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Airport : Supplies refilled for Plane " + planeID);
            updateOperationSummary(" - refilled supplies");
            operationsCompleted.countDown();
        }
    }
    
    public void cleaningAircraft() {
        if (isLanded) {
            System.out.println("Airport : Cleaning aircraft for Plane " + planeID);
            try {
                Thread.sleep(1000); // Cleaning takes 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Airport : Plane " + planeID + " cleaned");
            updateOperationSummary(" - cleaned aircraft");
            operationsCompleted.countDown();
        }
    }

    // Passenger embark/disembark (concurrent operation)
    public void passengerBehavior() {
        if (isLanded) {
            System.out.println("Plane " + planeID + ": Disembarking " + passengers + " passengers ...");
            try {
                Thread.sleep(1000); // Disembarking takes 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Plane " + planeID + ": All passengers disembarked");
            updateOperationSummary(" - disembarked passengers");

            System.out.println("Plane " + planeID + ": Embarking " + passengers + " passengers ...");
            try {
                Thread.sleep(1000); // Embarking takes 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Plane " + planeID + ": All passengers embarked");
            updateOperationSummary(" - embarked passengers");
            operationsCompleted.countDown();
        }
    }
    
    // Refueling (exclusive operation)
    public void refuelAircraft() {
        if (isLanded) {
            try {
                AsiaPacificAirport.refuelingTruck.acquire();
                System.out.println("Refuel Truck: Refueling Plane " + planeID);
                Thread.sleep(1000); // Refueling takes 1 second
                System.out.println("Refuel Truck: Plane " + planeID + " refueled");
                updateOperationSummary(" - refueled");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                AsiaPacificAirport.refuelingTruck.release();
                operationsCompleted.countDown();
            }
        }
    }

    // Depart from runway
    public void depart() {
        if (isLanded) {
            try {
                System.out.println("Plane " + planeID + ": Requesting departure in " +
                        Weather.getCurrentWeather() + " weather");

                // Check weather conditions and wait if necessary for departure
                Weather.waitForWeatherDeparture(planeID);

                System.out.println("Plane " + planeID + ": Undocking from Gate " + (assignedGate + 1));
                Thread.sleep(1000); // Undocking takes 1 second
                System.out.println("Plane " + planeID + ": Coasting to runway...");
                Thread.sleep(1000); // Coasting to runway takes 1 second

                // Release the gate
                AsiaPacificAirport.gates[assignedGate].release();
                AsiaPacificAirport.releaseGate(assignedGate, planeID);
                updateOperationSummary(" - left Gate " + (assignedGate + 1));

                // Acquire runway for takeoff
                AsiaPacificAirport.runway.acquire();
                System.out.println("Plane " + planeID + ": Departed from airport");
                System.out.println("ATC     : Plane " + planeID + " departed successfully");
                isLanded = false;
                departureTime = System.currentTimeMillis();
                AsiaPacificAirport.runway.release();
                AsiaPacificAirport.planesOnGround.release();
                updateOperationSummary(" - departed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Helper method to update operation summary
    private void updateOperationSummary(String update) {
        operationSummary.updateAndGet(current -> current + update);
    }

    @Override
    public void run() {
        try {
            // Emergency landing if required
            if (emergency) {
                emergencyRequired();
            } else {
                requestLanding();
            }

            // Create threads for concurrent operations
            Thread passengerThread = new Thread(this::passengerBehavior, "Passenger-" + planeID);
            Thread refillThread = new Thread(this::refillSupplies, "Refill-" + planeID);
            Thread cleanThread = new Thread(this::cleaningAircraft, "Clean-" + planeID);
            Thread refuelThread = new Thread(this::refuelAircraft, "Refuel-" + planeID);

            // Start concurrent operations
            passengerThread.start();
            refillThread.start();
            cleanThread.start();
            refuelThread.start();

            // Wait for all operations to complete
            operationsCompleted.await();
            
            // Depart
            depart();

            // Update statistics
            long totalTime = departureTime - arrivalTime;
            AsiaPacificAirport.updateStatistics(planeID, totalTime, waitingTime, passengers, operationSummary.get());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
