package org.example;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Doctor {
    private static String email;
    private static String contactDetail = null;
    private String name;
    private String specialty;
    private String contactDetails;
    private String bio;
    private Set<AvailabilitySlot> availability;

    public Doctor(String contactDetail) {

        this.contactDetail = contactDetail;
    }

    // Constructors, getters, and setters
    public String getName() {
        return name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getContactDetails() {
        return contactDetails;
    }

    public String getBio() {
        return bio;
    }

    public Set<AvailabilitySlot> getAvailability() {
        return availability;
    }

    public String getEmail() {
        return this.email;
    }
    public String setEmail(String email) {
        this.email = email;
        return email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public void setContactDetails(String contactDetails) {
        this.contactDetails = contactDetails;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setAvailability(Set<AvailabilitySlot> availability) {
        this.availability = availability;
    }

    public void updateAvailability(Set<AvailabilitySlot> newAvailability, Connection connection) {
        // Update the doctor's availability in the database
        availability = newAvailability;
        updateAvailabilityInDatabase(connection);

        // Notify subscribers about the availability update
        notifySubscribers();
    }
    private int id; // Add the ID property

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    private void updateAvailabilityInDatabase(Connection connection) {
        // Delete existing availability records
        try (PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM availability WHERE doctor_email = ?")) {
            preparedStatement.setString(1, getEmail());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Insert new availability records
        try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO availability (doctor_email, start_time, end_time) VALUES (?, ?, ?)")) {
            for (AvailabilitySlot slot : availability) {
                preparedStatement.setString(1, getEmail());
                preparedStatement.setTimestamp(2, new Timestamp(slot.getStartTime().getTime()));
                preparedStatement.setTimestamp(3, new Timestamp(slot.getEndTime().getTime()));
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void notifySubscribers() {
        Set<WebSocketClient> subscribers = DoctorAppointmentSystem.doctorSubscribers.getOrDefault(getEmail(), new HashSet<>());

        for (WebSocketClient client : subscribers) {
            client.sendMessage("Availability updated!");
        }
    }


    static void showUserAppointments(Connection connection, Doctor doctor) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Scheduled Appointments:");
        System.out.println("+------+-------------------+---------------------+---------------------+");
        System.out.println("| Row  | Patient Email     | Start Time          | End Time            |");
        System.out.println("+------+-------------------+---------------------+---------------------+");

        try {
            String query = "SELECT * FROM appointments WHERE doctor_email = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, doctor.getEmail());
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    int rowNumber = 1;
                    List<Integer> appointmentIds = new ArrayList<>();
                    while (resultSet.next()) {
                        String patientEmail = resultSet.getString("user_email");
                        Timestamp startTime = resultSet.getTimestamp("start_time");
                        Timestamp endTime = resultSet.getTimestamp("end_time");
                        int appointmentId = resultSet.getInt("id");

                        appointmentIds.add(appointmentId);

                        System.out.printf("| %-4d | %-17s | %-19s | %-19s |\n", rowNumber++, patientEmail, startTime, endTime);
                    }

                    System.out.println("+------+-------------------+---------------------+---------------------+");
                    System.out.println("Options:");
                    System.out.println("1. Cancel an appointment");
                    System.out.println("2. Go back");
                    System.out.print("Enter your choice: ");
                    int choice = scanner.nextInt();

                    switch (choice) {
                        case 1:
                            System.out.print("Enter the row number of the appointment you want to cancel: ");
                            int appointmentIndex = scanner.nextInt();
                            if (appointmentIndex >= 1 && appointmentIndex <= appointmentIds.size()) {
                                cancelAppointment(connection, appointmentIds.get(appointmentIndex - 1));
                            } else {
                                System.out.println("Invalid row number.");
                            }
                            break;
                        case 2:
                            // Go back option
                            // You can add implementation here if needed
                            break;
                        default:
                            System.out.println("Invalid choice.");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching appointments: " + e.getMessage());
        }
    }

    private static void cancelAppointment(Connection connection, int appointmentId) {
        try {
            String query = "DELETE FROM appointments WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, appointmentId);
                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Appointment cancelled successfully.");
                } else {
                    System.out.println("Failed to cancel appointment.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error cancelling appointment: " + e.getMessage());
        }
    }

    public static Doctor fromResultSet(ResultSet rs) throws SQLException {
        Doctor doctor = new Doctor(contactDetail);
        doctor.email = rs.getString("email");
        doctor.name = rs.getString("name");
        doctor.specialty = rs.getString("specialty");
        doctor.contactDetails = rs.getString("contact_details");
        doctor.bio = rs.getString("bio");
        return doctor;
    }

    public void retrieveAvailabilityFromDatabase(Connection connection) {
        Set<AvailabilitySlot> availabilitySlots = new HashSet<>();

        try {
            String query = "SELECT start_time, end_time FROM availability WHERE doctor_email = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, getEmail());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        LocalDateTime startTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                        LocalDateTime endTime = resultSet.getTimestamp("end_time").toLocalDateTime();

                        AvailabilitySlot slot = new AvailabilitySlot();
                        slot.setStartTime(Timestamp.valueOf(startTime));
                        slot.setEndTime(Timestamp.valueOf(endTime));

                        availabilitySlots.add(slot);
                    }
                }
            }

            setAvailability(availabilitySlots);
        } catch (SQLException e) {
            System.out.println("Error fetching available time slots from the database: " + e.getMessage());
        }
    }

    static void showAvailableTimeListings(Connection connection, Doctor doctor) {
        System.out.println("\n** Available Time Listings for " + doctor.getName() + " **");  // Include doctor's name

        try {
            String query = "SELECT start_time, end_time FROM availability WHERE doctor_email = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, doctor.getEmail());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.isBeforeFirst()) {
                        System.out.println("No available time slots found.");
                    } else {
                        System.out.println("--------------------------------------------------");
                        System.out.printf("%-20s %-20s %-20s %-20s\n", "Start Date", "Start Time", "End Date", "End Time");
                        System.out.println("--------------------------------------------------");

                        while (resultSet.next()) {
                            LocalDateTime startTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                            LocalDateTime endTime = resultSet.getTimestamp("end_time").toLocalDateTime();

                            System.out.printf("%-20s %-20s %-20s %-20s\n",
                                    startTime.toLocalDate(), startTime.toLocalTime(),
                                    endTime.toLocalDate(), endTime.toLocalTime());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching available time slots: " + e.getMessage());  // Concise error message
        }
    }

    static void updateAvailableTime(Connection connection, Doctor doctor) throws SQLException {
        Scanner scanner = new Scanner(System.in);

        // Retrieve existing availability slots for the doctor from the database
        doctor.retrieveAvailabilityFromDatabase(connection);

        // Display existing availability slots to the user
        Set<AvailabilitySlot> availabilitySlots = doctor.getAvailability();
        System.out.println("\nExisting Available Time Slots:");
        System.out.println("---------------------------------------------");
        System.out.printf("%-5s %-20s %-20s\n", "Index", "Start Time", "End Time");
        System.out.println("---------------------------------------------");

        int index = 1;
        for (AvailabilitySlot slot : availabilitySlots) {
            System.out.printf("%-5s %-20s %-20s\n", index++, slot.getStartTime(), slot.getEndTime());
        }
        System.out.println("---------------------------------------------");

        // Prompt the user to select the slot to update
        System.out.print("Enter the index of the slot to update: ");
        int slotIndex = scanner.nextInt();
        scanner.nextLine(); // Consume the newline character

        if (slotIndex < 1 || slotIndex > availabilitySlots.size()) {
            System.out.println("Invalid slot index.");
            return;
        }

        // Prompt the user to enter the new start and end times for the selected slot
        System.out.print("Enter new start time (yyyy-MM-dd HH:mm:ss): ");
        String newStartTimeStr = scanner.nextLine().trim(); // Trim leading and trailing spaces
        System.out.print("Enter new end time (yyyy-MM-dd HH:mm:ss): ");
        String newEndTimeStr = scanner.nextLine().trim(); // Trim leading and trailing spaces

        // Parse the new start and end times and convert them to Timestamp objects
        Timestamp newStartTime = Timestamp.valueOf(newStartTimeStr);
        Timestamp newEndTime = Timestamp.valueOf(newEndTimeStr);

        // Update the selected availability slot in memory
        AvailabilitySlot selectedSlot = availabilitySlots.stream().skip(slotIndex - 1).findFirst().orElse(null);
        if (selectedSlot != null) {
            selectedSlot.setStartTime(newStartTime);
            selectedSlot.setEndTime(newEndTime);
        } else {
            System.out.println("Failed to find the selected slot.");
            return;
        }

        // Update the availability in the database
        doctor.updateAvailability(availabilitySlots, connection);

        System.out.println("Availability slot updated successfully.");
    }

    private static Set<LocalDateTime> addedTimeSlots = new HashSet<>();
    static void addAvailableTime(Connection connection, Doctor doctor) throws SQLException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Option Selected: 1 (Add available time)\n");

        // Prompt for start time
        System.out.print("Enter start date-time (yyyy-MM-dd HH:mm:ss): ");
        String startDateTimeStr = scanner.nextLine();
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.parse(startDateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date-time format. Please enter date-time in yyyy-MM-dd HH:mm:ss format.");
            return;
        }

        // Check if start time is before the current date-time
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (startTime.isBefore(currentDateTime)) {
            System.out.println("Start time cannot be before the current date-time.");
            return;
        }

        // Check if the slot has already been added
        if (addedTimeSlots.contains(startTime)) {
            System.out.println("This time slot has already been added.");
            return;
        }

        // Prompt for duration of each slot
        System.out.print("Enter duration of each slot in minutes: ");
        int durationMinutes = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        // Prompt for number of slots to add
        System.out.print("Enter number of slots to add: ");
        int numSlots = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        // Calculate end time based on start time and duration
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

        // Insert available time slots into the database
        for (int i = 0; i < numSlots; i++) {
            if (insertAvailabilityIntoDatabase(connection, doctor.getEmail(), doctor.getName(), doctor.getSpecialty(), startTime, endTime)) {
                // Add the current slot to the HashSet of added slots
                addedTimeSlots.add(startTime);
                System.out.println("\nSuccessfully added a new time slot:");
                System.out.println("Start Time: " + startTime.format(DateTimeFormatter.ofPattern("MMMM d, yyyy, hh:mm a")));
                System.out.println("End Time: " + endTime.format(DateTimeFormatter.ofPattern("MMMM d, yyyy, hh:mm a")));
            } else {
                System.out.println("Failed to add slot: " + startTime + " - " + endTime);
            }
            // Move to next slot
            startTime = endTime;
            endTime = startTime.plusMinutes(durationMinutes);
        }
    }
    private static boolean insertAvailabilityIntoDatabase(Connection connection, String doctorEmail, String doctorName, String doctorSpecialty, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        String query = "INSERT INTO availability (doctor_email, doctor_name, doctor_specialty, start_time, end_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, doctorEmail);
            preparedStatement.setString(2, doctorName);
            preparedStatement.setString(3, doctorSpecialty);
            preparedStatement.setTimestamp(4, Timestamp.valueOf(startDateTime));
            preparedStatement.setTimestamp(5, Timestamp.valueOf(endDateTime));
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.out.println("An error occurred while inserting availability into the database: " + e.getMessage());
            return false;
        }
    }

    private static boolean addAppointment(Connection connection, String doctorEmail, Timestamp startTime, Timestamp endTime) throws SQLException {
        String query = "INSERT INTO appointments (start_time, end_time, doctor_email) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setTimestamp(1, startTime);
            preparedStatement.setTimestamp(2, endTime);
            preparedStatement.setString(3, doctorEmail);
            return preparedStatement.executeUpdate() > 0;
        }
    }
    public Doctor(String email, String name, String contactDetail, String specialty) {
        this.email = email;
        this.name = name;
        this.contactDetail = contactDetail;
        this.specialty = specialty;
    }

    public String getContactDetail() {
        return contactDetail;
    }
}
