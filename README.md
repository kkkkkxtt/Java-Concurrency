# Java-Concurrency
Utilizing Java Concurrency technique to prioritize handling emergency case (Single Thread) among multiple concurrently running threads.

## Java Concurrency: Airport Traffic Control Simulation

A simulation of an airport traffic control system that demonstrates Java concurrency techniques, with special handling for emergency cases among multiple concurrent threads.

## Project Description

This project simulates an airport with:
- 1 runway
- 3 gates
- Multiple planes arriving/departing concurrently
- Weather conditions affecting operations
- Priority handling for emergency landings

Key concurrency features demonstrated:
- `Semaphore` for resource management (runway, gates)
- `ReentrantLock` and `Condition` for emergency prioritization
- `CountDownLatch` for operation synchronization
- Thread prioritization
- Concurrent operations (passenger handling, refueling, cleaning)

## Features

- **Emergency Handling**: Prioritizes emergency landings over regular operations
- **Weather System**: Dynamic weather changes affect operations
- **Resource Management**: Thread-safe allocation of limited airport resources
- **Statistics Tracking**: Records and reports performance metrics
- **Concurrent Operations**: Multiple ground services operate simultaneously

## How to Run

1. Compile all Java files:
```bash
javac src/asiapacificairport/*.java
```
2. Run this simulation
```bash
java -cp src asiapacificairport.AirTrafficControlSystem
```

## Sample Output
*** Asia Pacific Airport Simulation Started ***

Initializing airport with 3 gates and 1 runway...

Current Weather: Sunny

Plane P1: Requesting for landing in Sunny weather 

ATC     : Plane P1 added to landing queue. Current queue size: 1

ATC     : Plane P1 granted landing permission. Current queue size: 0

...

## Concurrency Techniques Used
`Semaphore` -	Manage limited resources (runway, gates)

`ReentrantLock` -	Ensure thread-safe operations

`Condition` -	Handle emergency prioritization

`CountDownLatch` -	Synchronize plane operations


## Class Structure
AirTrafficControlSystem: Main simulation driver

AsiaPacificAirport: Manages shared resources and coordination

Plane: Represents each aircraft with its operations

Weather: Simulates dynamic weather conditions
