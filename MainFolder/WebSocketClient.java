package org.example;

public class WebSocketClient extends Thread {
    private Doctor doctor;

    public WebSocketClient(Doctor doctor) {
        this.doctor = doctor;
    }

    public void run() {
        // WebSocket connection logic (simulated)
        while (true) {
            // Simulate real-time updates
            try {
                Thread.sleep(5000); // Sleep for 5 seconds
                System.out.println("Real-time update: " + doctor.getName() + "'s availability changed!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String message) {
        // Send message to WebSocket client
        System.out.println("WebSocket message sent: " + message);
    }
}