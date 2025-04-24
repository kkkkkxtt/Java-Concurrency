package asiapacificairport;

import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;

public class AsiaPacificAirport {
    // Shared resources
    public static final int MAX_GATES = 3; // Total 3 gates
    public static Semaphore runway = new Semaphore(1); // Only one runway
    public static Semaphore[] gates = new Semaphore[MAX_GATES]; // Individual gates
    public static Semaphore refuelingTruck = new Semaphore(1); // One refueling truck
    public static Semaphore planesOnGround = new Semaphore(3); // Max 3 planes on ground (gates + runway)
    public static ReentrantLock consoleLock = new ReentrantLock(); // For thread-safe console output
    
    // Emergency handling
    public static ReentrantLock landingLock = new ReentrantLock();
    public static Condition landingCondition = landingLock.newCondition();
    public static volatile boolean emergencyInProgress = false;
    public static volatile String emergencyPlaneID = null;
    
    // Gate status tracking
    private static boolean[] gateOccupied = new boolean[MAX_GATES];
    private static String[] gateAssignments = new String[MAX_GATES];
    private static final ReentrantLock gateStatusLock = new ReentrantLock();

    // Landing queue tracking
    private static final List<String> landingQueue = Collections.synchronizedList(new ArrayList<>());
    // Statistics
    private static int planesServed = 0;
    private static int totalPassengers = 0;
    private static long minWaitingTime = Long.MAX_VALUE;
    private static long maxWaitingTime = Long.MIN_VALUE;
    private static long totalWaitingTime = 0;
    private static long simulationStartTime;
    private static List<String> planeOperations = Collections.synchronizedList(new ArrayList<>());

    // Initialize gates
    static {
        for (int i = 0; i < MAX_GATES; i++) {
            gates[i] = new Semaphore(1);
            gateOccupied[i] = false;
            gateAssignments[i] = "None";
        }
        simulationStartTime = System.currentTimeMillis();
    }

    // Find available gate
    public static int findAvailableGate() {
        gateStatusLock.lock();
        try {
            for (int i = 0; i < MAX_GATES; i++) {
                if (!gateOccupied[i] && gates[i].availablePermits() > 0) {
                    return i;
                }
            }
            return -1; // No gate available
        } finally {
            gateStatusLock.unlock();
        }
    }

    // Add plane to landing queue
    public static void addToLandingQueue(Plane plane) {
        landingLock.lock();
        try {
            if (!landingQueue.contains(plane.getID())) {
                landingQueue.add(plane.getID());
                System.out.println("ATC     : Plane " + plane.getID() + 
                    (plane.isEmergency() ? " (EMERGENCY)" : "") + 
                    " added to landing queue. Current queue size: " + landingQueue.size());
                
                // If this is an emergency plane, notify all waiting planes
                if (plane.isEmergency()) {
                    emergencyInProgress = true;
                    emergencyPlaneID = plane.getID();
                    System.out.println("ATC     : EMERGENCY ALERT! Prioritizing landing for Plane " + plane.getID());
                    landingCondition.signalAll();
                }
            }
        } finally {
            landingLock.unlock();
        }
    }
    
    // Check if plane has permission to land based on current situation
    public static boolean hasPermissionToLand(Plane requestingPlane) {
        landingLock.lock();
        try {
            String planeID = requestingPlane.getID();
            boolean isEmergency = requestingPlane.isEmergency();

            // Always add to queue if not already in queue
            if (!landingQueue.contains(planeID)) {
                landingQueue.add(planeID);
                System.out.println("ATC     : Plane " + planeID +
                    (isEmergency ? " (EMERGENCY)" : "") +
                    " added to landing queue. Current queue size: " + landingQueue.size());

                // Set emergency status if applicable
                //check if it is emergency case
                if (isEmergency) {
                    emergencyInProgress = true;
                    emergencyPlaneID = planeID;
                    System.out.println("ATC     : EMERGENCY ALERT! Prioritizing landing for Plane " + planeID);
                    landingCondition.signalAll(); // Wake up all waiting threads to reevaluate
                }
            }

            // If no gates available or ground is full, no plane can land
            if (findAvailableGate() == -1 || planesOnGround.availablePermits() <= 0) {
                // If this is an emergency plane, wait for resources to free up
                if (isEmergency) {
                    // No return here - emergency planes keep trying
                } else {
                    // Non-emergency planes should pause if emergency in progress
                    while (emergencyInProgress && !emergencyPlaneID.equals(planeID)) {
                        System.out.println("ATC     : Plane " + planeID + " holding position for emergency Plane " + emergencyPlaneID);
                        landingCondition.await(); // Wait until emergency is cleared
                    }
                    return false; // Still no resources
                }
            }

            // Emergency planes get priority
            if (emergencyInProgress) {
                if (isEmergency && emergencyPlaneID.equals(planeID)) {
                    // This emergency plane gets priority
                    landingQueue.remove(planeID);
                    System.out.println("ATC     : EMERGENCY Plane " + planeID + " granted priority landing permission");
                    return true;
                } else {
                    // Other planes must wait
                    while (emergencyInProgress && !emergencyPlaneID.equals(planeID)) {
                        System.out.println("ATC     : Plane " + planeID + " holding position for emergency Plane " + emergencyPlaneID);
                        landingCondition.await(); // Wait until emergency is cleared
                    }
                }
            }

            // Normal FIFO queue processing if no emergency or emergency has been handled
            if (!landingQueue.isEmpty() && landingQueue.get(0).equals(planeID)) {
                landingQueue.remove(0);
                System.out.println("ATC     : Plane " + planeID + " granted landing permission. Current queue size: " + landingQueue.size());
                return true;
            }

            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            landingLock.unlock();
        }
    }

    // Check if a plane can land (based on available gates and ground limit)
    public static boolean canPlaneArrive() {
        return findAvailableGate() != -1 && planesOnGround.availablePermits() > 0;
    }

    // Assign gate to plane
    public static int assignGate(String planeID) {
        gateStatusLock.lock();
        try {
            int gateNumber = findAvailableGate();
            if (gateNumber != -1) {
                gateOccupied[gateNumber] = true;
                gateAssignments[gateNumber] = planeID;
                System.out.println("ATC     : Plane " + planeID + " assigned to Gate " + (gateNumber + 1));

                // If this was an emergency plane, clear the emergency status
                landingLock.lock();
                try {
                    if (emergencyInProgress && emergencyPlaneID.equals(planeID)) {
                        emergencyInProgress = false;
                        emergencyPlaneID = null;
                        landingCondition.signalAll(); // Wake up all waiting planes
                    }
                } finally {
                    landingLock.unlock();
                }

                return gateNumber;
            }
            return -1;
        } finally {
            gateStatusLock.unlock();
        }
    }

    // Release gate
    public static void releaseGate(int gateNumber, String planeID) {
        gateStatusLock.lock();
        try {
            if (gateNumber >= 0 && gateNumber < MAX_GATES) {
                gateOccupied[gateNumber] = false;
                gateAssignments[gateNumber] = "None";
                System.out.println("ATC     : Gate " + (gateNumber + 1) + " is now empty");

                // Notify waiting planes that a gate is available
                landingLock.lock();
                try {
                    landingCondition.signalAll();
                } finally {
                    landingLock.unlock();
                }
            }
        } finally {
            gateStatusLock.unlock();
        }
    }

    // Update statistics (thread-safe)
    public static void updateStatistics(String planeID, long totalTime, long waitingTime, int passengers, String operationSummary) {
        consoleLock.lock();
        try {
            planesServed++;
            totalPassengers += passengers;
            minWaitingTime = Math.min(minWaitingTime, waitingTime);
            maxWaitingTime = Math.max(maxWaitingTime, waitingTime);
            totalWaitingTime += waitingTime;
            planeOperations.add("Plane " + planeID + ": " + operationSummary + 
                " (Wait time: " + waitingTime + "ms, Total time: " + totalTime + "ms)");
        } finally {
            consoleLock.unlock();
        }
    }

    // Print statistics and perform sanity checks
    public static void printStatistics() {
        consoleLock.lock();
        try {
            long simulationEndTime = System.currentTimeMillis();
            long totalRunningTime = simulationEndTime - simulationStartTime;
            
            System.out.println("\n--- Simulation Statistics ---");
            System.out.println("Sanity Check - Gates Empty:");
            boolean allGatesEmpty = true;
            for (int i = 0; i < MAX_GATES; i++) {
                boolean isEmpty = !gateOccupied[i];
                System.out.println("Gate " + (i + 1) + ": " + (isEmpty ? "Empty" : "Occupied by " + gateAssignments[i]));
                if (!isEmpty) allGatesEmpty = false;
            }
            System.out.println("All Gates Empty: " + (allGatesEmpty ? "YES" : "NO"));
       
            System.out.println("\n--- Service Statistics ---");
            System.out.println("Planes Served: " + planesServed);
            System.out.println("Total Passengers Boarded: " + totalPassengers);
            
            System.out.println("\n--- Waiting Time Statistics ---");
            long avgWaitingTime = (planesServed > 0) ? totalWaitingTime / planesServed : 0;
            System.out.println("Minimum Waiting Time: " + (minWaitingTime == Long.MAX_VALUE ? 0 : minWaitingTime) + " ms");
            System.out.println("Maximum Waiting Time: " + (maxWaitingTime == Long.MIN_VALUE ? 0 : maxWaitingTime) + " ms");
            System.out.println("Average Waiting Time: " + avgWaitingTime + " ms");
            System.out.println("Total Waiting Time: " + totalWaitingTime + " ms");
            System.out.println("Total Simulation Time: " + totalRunningTime + " ms");
            
            System.out.println("\n--- Plane Operations Summary ---");
            for (String operation : planeOperations) {
                System.out.println(operation);
            }
        } finally {
            consoleLock.unlock();
        }
    }
}
