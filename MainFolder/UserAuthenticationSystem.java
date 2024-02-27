package org.example;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.UserAuthenticationSystem.isValidPassword;

public class Utils {
    static Scanner scanner = new Scanner(System.in);
    static Map<String, Integer> loginAttempts = new HashMap<>();
    static final int MAX_LOGIN_ATTEMPTS = 3;
    public static void loginUser(Connection connection) {
        System.out.print("Enter email: ");
        String email = scanner.nextLine();
        if (isBlocked(email)) {
            System.out.println("Too many unsuccessful login attempts. Please try again later.");
            return;
        }
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        // Check if the user exists and the password is correct
        if (isValidUser(connection, email, password)) {
            System.out.println("Login successful!");
            int option;
            do {
                System.out.println("\nOptions:");
                System.out.println("1. Make appointment");
                System.out.println("2. Check scheduled appointments");
                System.out.println("3. Exit");
                System.out.print("Enter your choice: ");
                option = scanner.nextInt();
                scanner.nextLine(); // consume newline

                switch (option) {
                    case 1:
                        makeAppointment(connection, email); // Pass email here
                        break;
                    case 2:
                        checkScheduledAppointments(connection, email); // Pass email here
                        break;
                    case 3:
                        System.out.println("Exiting...");
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } while (option != 3);
        } else {
            System.out.println("Invalid email or password. Please try again.");
            updateLoginAttempts(email);
        }
    }
    private static class Appointment {
        private String doctorEmail;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public Appointment(String doctorEmail, LocalDateTime startTime, LocalDateTime endTime) {
            this.doctorEmail = doctorEmail;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getDoctorEmail() {
            return doctorEmail;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }
    }
    private static boolean isBlocked(String email) {
        int attempts = loginAttempts.getOrDefault(email, 0);
        return attempts >= MAX_LOGIN_ATTEMPTS;
    }
    private static void updateLoginAttempts(String email) {
        int attempts = loginAttempts.getOrDefault(email, 0);
        loginAttempts.put(email, attempts + 1);
        System.out.println("Remaining login attempts: " + (MAX_LOGIN_ATTEMPTS - attempts - 1));
    }
    private static boolean isValidUser(Connection connection, String email, String password) {
        String query = "SELECT COUNT(*) AS count FROM users WHERE email = ? AND password = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, email);
            preparedStatement.setString(2, password);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int count = resultSet.getInt("count");
                    return count > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class DoctorAvailableTime {
        private String email;
        private String name;
        private String specialty;
        private List<LocalTime> timeSlots;

        public DoctorAvailableTime(String email, String name, String specialty) {
            this.email = email;
            this.name = name;
            this.specialty = specialty;
            this.timeSlots = new ArrayList<>();
        }

        public void addTimeSlot(LocalTime timeSlot) {
            timeSlots.add(timeSlot);
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public String getSpecialty() {
            return specialty;
        }

        public List<LocalTime> getTimeSlots() {
            return timeSlots;
        }
    }
    public static void makeAppointment(Connection connection, String userEmail) {
        System.out.println("Enter your name: ");
        String userName = scanner.nextLine();

        // Show available doctors
        Map<Integer, DoctorAvailableTime> doctors = showAvailableDoctors(connection);

        // Prompt the user to select a doctor
        int doctorChoice = getUserChoice("Enter the number of the doctor you'd like to make an appointment with: ", doctors.size());
        if (doctorChoice == -1) {
            System.out.println("Invalid choice. Exiting...");
            return;
        }

        DoctorAvailableTime selectedDoctor = doctors.get(doctorChoice);

        // Show available times for the selected doctor
        System.out.println("Available times for Doctor " + selectedDoctor.getEmail() + ":");
        List<String> availableTimes = showAvailableTimes(connection, selectedDoctor.getEmail());

        // Prompt the user to select an available time
        int timeChoice = getUserChoice("Enter the number of the available time you'd like to select: ", availableTimes.size());
        if (timeChoice == -1) {
            System.out.println("Invalid choice. Exiting...");
            return;
        }

        // Extract start and end times from the selected appointment time
        String appointmentTime = availableTimes.get(timeChoice - 1);
        String[] timeTokens = appointmentTime.split(" to ");
        if (timeTokens.length != 2) {
            System.out.println("Invalid time format. Exiting...");
            return;
        }
        String startTime = timeTokens[0].trim();
        String endTime = timeTokens[1].trim();

        // Insert the appointment into the database
        if (insertAppointment(connection, userEmail, userName, selectedDoctor.getEmail(), startTime, endTime)) {
            System.out.println("Appointment successfully booked!");

            // Remove the selected time slot from the availability table
            removeAppointmentTime(connection, selectedDoctor.getEmail(), startTime, endTime);

            // Fetch the doctor's name and contact details
            String doctorName = selectedDoctor.getName();
            String doctorEmail = selectedDoctor.getEmail();
            String doctorSpecialty = selectedDoctor.getSpecialty();
            String doctorContact = getDoctorContact(connection, selectedDoctor.getEmail());
            if (doctorName == null || doctorContact == null) {
                System.out.println("Failed to retrieve doctor's information. QR code generation aborted.");
                return;
            }

            // Generate QR code with appointment details
            String appointmentDetails = "Doctor: " + doctorName + "\n"
                    + "Email: " + doctorEmail + "\n"
                    + "Contact: " + doctorContact + "\n"
                    + "Specialty: " + doctorSpecialty + "\n"
                    + "Time: " + startTime + " to " + endTime + "\n"
                    + "Patient: " + userName + "\n"
                    + "Email: " + userEmail;
            generateQRCode(appointmentDetails);
        } else {
            System.out.println("Failed to book appointment. Please try again.");
        }
    }

    private static int getUserChoice(String prompt, int maxChoice) {
        while (true) {
            System.out.print(prompt);
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice < 1 || choice > maxChoice) {
                    System.out.println("Invalid choice. Please enter a number between 1 and " + maxChoice);
                } else {
                    return choice;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }


    private static void removeAppointmentTime(Connection connection, String doctorEmail, String startTime, String endTime) {
        try {
            String query = "DELETE FROM availability WHERE doctor_email = ? AND start_time = ? AND end_time = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, doctorEmail);
            preparedStatement.setString(2, startTime);
            preparedStatement.setString(3, endTime);

            int rowsDeleted = preparedStatement.executeUpdate();
            if (rowsDeleted > 0) {
                System.out.println("Appointment time removed from availability.");
            } else {
                System.out.println("Failed to remove appointment time from availability.");
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while removing appointment time from availability: " + e.getMessage());
        }
    }

    public static Map<Integer, DoctorAvailableTime> showAvailableDoctors(Connection connection) {
        Map<Integer, DoctorAvailableTime> doctors = new HashMap<>();
        try {
            // Retrieve available doctors
            String query = "SELECT email, name, specialty FROM doctors";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Display available doctors
            System.out.println("Available Doctors:");
            int count = 1;
            while (resultSet.next()) {
                String doctorEmail = resultSet.getString("email");
                String doctorName = resultSet.getString("name");
                String specialty = resultSet.getString("specialty");
                String doctorInfo = String.format("%d. %s (%s) - Specialty: %s", count, doctorName, doctorEmail, specialty);
                System.out.println(doctorInfo);

                DoctorAvailableTime doctorAvailableTime = new DoctorAvailableTime(doctorEmail, doctorName, specialty);
                doctors.put(count, doctorAvailableTime);
                count++;
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving available doctors: " + e.getMessage());
        }
        return doctors;
    }

    private static List<String> showAvailableTimes(Connection connection, String doctorEmail) {
        List<String> availableTimes = new ArrayList<>();
        try {
            // Retrieve available times for the specified doctor
            String query = "SELECT start_time, end_time FROM availability WHERE doctor_email = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, doctorEmail);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Display available times for the doctor
            System.out.println("Available times for Doctor " + doctorEmail + ":");
            System.out.println();
            System.out.printf("%-3s%-15s%-12s%-15s%-12s%n", "No.", "Start Date", "Time", "End Date", "Time");
            int count = 1;
            while (resultSet.next()) {
                String startDate = resultSet.getString("start_time").split(" ")[0];
                String startTime = resultSet.getString("start_time").split(" ")[1];
                String endDate = resultSet.getString("end_time").split(" ")[0];
                String endTime = resultSet.getString("end_time").split(" ")[1];

                String formattedTime = String.format("%s %s to %s %s", startDate, startTime, endDate, endTime);
                availableTimes.add(formattedTime); // Add the formatted time to the list
                System.out.printf("%-3d%-15s%-12s%-15s%-12s%n", count, startDate, startTime, endDate, endTime);
                count++;
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving available times: " + e.getMessage());
        }
        return availableTimes;
    }

    private static boolean insertAppointment(Connection connection, String userEmail, String userName,
                                             String selectedDoctorEmail, String startTime, String endTime) {
        try {
            String query = "INSERT INTO appointments (user_email, user_name, doctor_email, start_time, end_time) " +
                    "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, userEmail);
            preparedStatement.setString(2, userName);
            preparedStatement.setString(3, selectedDoctorEmail);
            preparedStatement.setString(4, startTime);
            preparedStatement.setString(5, endTime);

            int rowsInserted = preparedStatement.executeUpdate();
            return rowsInserted > 0;
        } catch (SQLException e) {
            System.out.println("An error occurred while inserting appointment: " + e.getMessage());
            return false;
        }
    }
    private static String getDoctorContact(Connection connection, String doctorEmail) {
        String query = "SELECT contact_details FROM doctors WHERE email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, doctorEmail);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("contact_details");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void checkScheduledAppointments(Connection connection, String userEmail) {
        // Implement logic to show scheduled appointments for the user
        // Option to generate QR code, book another one, cancel, or go back
        int option;
        do {
            System.out.println("\nScheduled Appointments:");
            List<Appointment> appointments = getScheduledAppointments(connection, userEmail);
            displayAppointments(appointments);

            System.out.println("\nOptions:");
            System.out.println("1. Generate QR code for an appointment");
            System.out.println("2. Book another appointment");
            System.out.println("3. Cancel an appointment");
            System.out.println("4. Go back");
            System.out.print("Enter your choice: ");
            option = scanner.nextInt();
            scanner.nextLine(); // consume newline
            switch (option) {
                case 1:
                    generateQRCodeForAppointment(connection, appointments, userEmail);
                    break;
                case 2:
                    makeAppointment(connection, userEmail);
                    break;
                case 3:
                    cancelAppointment(connection, userEmail); // Pass userEmail here
                    break;
                case 4:
                    System.out.println("Going back...");
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } while (option != 4);
    }
    private static void displayAppointments(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            System.out.println("No scheduled appointments.");
        } else {
            System.out.println("\nScheduled Appointments:");
            System.out.println("+------+-----------------------------------------+");
            System.out.printf("| %-4s | %-10s | %-20s | %-20s |\n", "Row", "Doctor", "Start Time", "End Time");
            System.out.println("+------+-----------------------------------------+");

            for (int i = 0; i < appointments.size(); i++) {
                Appointment appointment = appointments.get(i);
                String doctorEmail = appointment.getDoctorEmail();
                LocalDateTime startTime = appointment.getStartTime();
                LocalDateTime endTime = appointment.getEndTime();

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                String formattedStartTime = startTime.format(formatter);
                String formattedEndTime = endTime.format(formatter);

                System.out.printf("| %-4d | %-10s | %-20s | %-20s |\n", i + 1, doctorEmail, formattedStartTime, formattedEndTime);
            }

            System.out.println("+------+-----------------------------------------+");
        }
    }



    private static List<Appointment> getScheduledAppointments(Connection connection, String userEmail) {
        List<Appointment> appointments = new ArrayList<>();
        String query = "SELECT * FROM appointments WHERE user_email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, userEmail);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String doctorEmail = resultSet.getString("doctor_email");
                    LocalDateTime startTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                    LocalDateTime endTime = resultSet.getTimestamp("end_time").toLocalDateTime();
                    appointments.add(new Appointment(doctorEmail, startTime, endTime));
                }
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving scheduled appointments: " + e.getMessage());
        }
        return appointments;
    }

    private static String getDoctorName(Connection connection, String doctorEmail) {
        String query = "SELECT name FROM doctors WHERE email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, doctorEmail);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("name");
                }
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving doctor's name: " + e.getMessage());
        }
        return null;
    }
    private static String getDoctorContactDetail(Connection connection, String doctorEmail) {
        String query = "SELECT contact_details FROM doctors WHERE email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, doctorEmail);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("contact_details");
                }
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while retrieving doctor's contact detail: " + e.getMessage());
        }
        return null;
    }

    private static void generateQRCodeForAppointment(Connection connection, List<Appointment> appointments, String userEmail) {
        // Method implementation
        System.out.print("Enter the row number of the appointment you want to generate a QR code for: ");
        int appointmentIndex = scanner.nextInt();
        scanner.nextLine(); // consume newline

        if (appointmentIndex < 1 || appointmentIndex > appointments.size()) {
            System.out.println("Invalid appointment row number. Exiting...");
            return;
        }

        Appointment selectedAppointment = appointments.get(appointmentIndex - 1);
        String doctorEmail = selectedAppointment.getDoctorEmail();
        LocalDateTime startTime = selectedAppointment.getStartTime();
        LocalDateTime endTime = selectedAppointment.getEndTime();

        String doctorName = getDoctorName(connection, doctorEmail);
        if (doctorName == null) {
            System.out.println("Failed to retrieve doctor's information. QR code generation aborted.");
            return;
        }

        String contactDetail = getDoctorContactDetail(connection, doctorEmail);
        if (contactDetail == null) {
            System.out.println("Failed to retrieve doctor's contact detail. QR code generation aborted.");
            return;
        }

        String appointmentDetails = "Doctor: " + doctorName + "\n"
                + "Email: " + doctorEmail + "\n"
                + "Contact Detail: " + contactDetail + "\n"
                + "Time: " + startTime + " to " + endTime + "\n"
                + "Patient: " + userEmail;

        generateQRCode(appointmentDetails);
    }
    private static void addAppointmentTime(Connection connection, String doctorEmail, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = "INSERT INTO availability (doctor_email, start_time, end_time) VALUES (?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, doctorEmail);
            preparedStatement.setTimestamp(2, Timestamp.valueOf(startTime));
            preparedStatement.setTimestamp(3, Timestamp.valueOf(endTime));

            int rowsInserted = preparedStatement.executeUpdate();
            if (rowsInserted > 0) {
                System.out.println("Cancelled appointment time added back to availability.");
            } else {
                System.out.println("Failed to add cancelled appointment time back to availability.");
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while adding cancelled appointment time back to availability: " + e.getMessage());
        }
    }

    private static void cancelAppointment(Connection connection, String userEmail) {
        // Retrieve the list of scheduled appointments for the user
        List<Appointment> appointments = getScheduledAppointments(connection, userEmail);
        if (appointments.isEmpty()) {
            System.out.println("No scheduled appointments to cancel.");
            return;
        }

        // Display the appointments with row numbers
        System.out.println("Scheduled Appointments:");
        displayAppointmentsWithRowNumbers(appointments);

        // Prompt the user to select which appointment to cancel
        System.out.print("Enter the row number of the appointment you want to cancel: ");
        int appointmentIndex = scanner.nextInt();
        scanner.nextLine(); // Consume newline
        if (appointmentIndex < 1 || appointmentIndex > appointments.size()) {
            System.out.println("Invalid appointment row number. Cancelling appointment aborted.");
            return;
        }

        // Confirm the cancellation
        Appointment appointmentToCancel = appointments.get(appointmentIndex - 1);
        System.out.println("Are you sure you want to cancel the following appointment?");
        System.out.println("Doctor Email: " + appointmentToCancel.getDoctorEmail());
        System.out.println("Start Time: " + appointmentToCancel.getStartTime());
        System.out.println("End Time: " + appointmentToCancel.getEndTime());
        System.out.print("Enter 'yes' to confirm: ");
        String confirmation = scanner.nextLine().trim().toLowerCase();
        if (!confirmation.equals("yes")) {
            System.out.println("Appointment cancellation aborted.");
            return;
        }

        // Delete the selected appointment from the database
        String doctorEmail = appointmentToCancel.getDoctorEmail();
        LocalDateTime startTime = appointmentToCancel.getStartTime();
        LocalDateTime endTime = appointmentToCancel.getEndTime();
        if (deleteAppointment(connection, userEmail, doctorEmail, startTime, endTime)) {
            // Add the cancelled appointment time slot back to availability
            addAppointmentTime(connection, doctorEmail, startTime, endTime);
            System.out.println("Appointment cancelled successfully.");
        } else {
            System.out.println("Failed to cancel appointment. Please try again.");
        }
    }
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static void displayAppointmentsWithRowNumbers(List<Appointment> appointments) {
        System.out.println("Scheduled Appointments:");
        System.out.println("+------+------------------+---------------------+---------------------+");
        System.out.println("| Row  | Doctor Email     | Start Time          | End Time            |");
        System.out.println("+------+------------------+---------------------+---------------------+");
        for (int i = 0; i < appointments.size(); i++) {
            Appointment appointment = appointments.get(i);
            String startTimeFormatted = appointment.getStartTime().format(DATE_TIME_FORMATTER);
            String endTimeFormatted = appointment.getEndTime().format(DATE_TIME_FORMATTER);
            System.out.printf("| %-4d | %-16s | %-19s | %-19s |%n",
                    i + 1,
                    appointment.getDoctorEmail(),
                    startTimeFormatted,
                    endTimeFormatted);
        }
        System.out.println("+------+------------------+---------------------+---------------------+");
    }
    private static boolean deleteAppointment(Connection connection, String userEmail, String doctorEmail, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String query = "DELETE FROM appointments WHERE user_email = ? AND doctor_email = ? AND start_time = ? AND end_time = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, userEmail);
            preparedStatement.setString(2, doctorEmail);
            preparedStatement.setTimestamp(3, Timestamp.valueOf(startTime));
            preparedStatement.setTimestamp(4, Timestamp.valueOf(endTime));

            int rowsDeleted = preparedStatement.executeUpdate();
            return rowsDeleted > 0;
        } catch (SQLException e) {
            System.out.println("An error occurred while cancelling appointment: " + e.getMessage());
            return false;
        }
    }


    public static boolean isEmailExists(Connection connection, String email) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public static void generateQRCode(String appointmentDetails) {
        // QR code parameters
        int width = 300;
        int height = 300;
        String format = "png";
        String filePath = "D:\\From_Telegram\\New folder\\JAVA\\appointment_qr.png"; // Path to save the QR code image

        // Create the QR code writer
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        try {
            // Encode the appointment details as a BitMatrix
            BitMatrix bitMatrix = qrCodeWriter.encode(appointmentDetails, BarcodeFormat.QR_CODE, width, height);

            // Create the QR code image
            BufferedImage qrImage = toBufferedImage(bitMatrix);
            File outputFile = new File(filePath);
            ImageIO.write(qrImage, format, outputFile);

            System.out.println("QR code generated successfully at: " + filePath);
        } catch (WriterException | IOException e) {
            System.err.println("Error generating QR code: " + e.getMessage());
        }
    }
    private static void generateQRCodePassword (String data) {
        int width = 300;
        int height = 300;
        String format = "png";
        Map<EncodeHintType, ErrorCorrectionLevel> hintMap = new HashMap<>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hintMap);
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            bufferedImage.createGraphics();

            Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (bitMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1);
                    }
                }
            }

            File qrCodeFile = new File("password_qr_code.png");
            ImageIO.write(bufferedImage, format, qrCodeFile);
            System.out.println("QR code generated successfully.");
        } catch (WriterException | IOException e) {
            System.out.println("Error generating QR code: " + e.getMessage());
        }
    }
    private static BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return image;
    }
    private static boolean updatePassword(Connection connection, String email, String newPassword) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE users SET password = ? WHERE email = ?")) {
            preparedStatement.setString(1, newPassword);
            preparedStatement.setString(2, email);
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    private static String generateNewPassword() {
        // Implement your logic to generate a new password
        return "new_password"; // For demonstration purposes only
    }
    static void resetPassword(Connection connection) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Password Reset:");
        System.out.print("Enter your email: ");
        String email = scanner.nextLine();

        // Check if the email exists in the database
        if (!isEmailExists(connection, email)) {
            System.out.println("Email not found. Please register or enter a valid email.");
            return;
        }

        // Check if the user has reached the maximum number of password reset attempts for the day
        if (isMaxResetAttemptsReached(connection, email)) {
            System.out.println("You have reached the maximum number of password reset attempts for today.");
            displayResetAttemptTimes(connection, email); // Show user attempt times
            return;
        }

        // Ask the user whether they want to generate a new password automatically or enter a new password
        System.out.println("Do you want to:");
        System.out.println("1. Automatically generate a new password");
        System.out.println("2. Enter a new password yourself");
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume the newline character

        String newPassword;
        switch (choice) {
            case 1:
                newPassword = generateNewPassword();
                System.out.println("Generated new password: " + newPassword);
                break;
            case 2:
                System.out.print("Enter your new password: ");
                newPassword = scanner.nextLine();
                break;
            default:
                System.out.println("Invalid choice. Please try again.");
                return;
        }

        if (updatePassword(connection, email, newPassword)) {
            System.out.println("Password reset successful. Your new password has been set.");
            generateQRCodePassword(newPassword); // Generate QR code for the user-entered password
            updateResetAttempts(connection, email); // Update the reset attempts count for the user
        } else {
            System.out.println("Failed to reset password. Please try again later.");
        }
    }

    private static boolean isMaxResetAttemptsReached(Connection connection, String email) {
        // Get the current date
        LocalDate currentDate = LocalDate.now();

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM password_reset_attempts WHERE email = ? AND reset_date = ?")) {
            preparedStatement.setString(1, email);
            preparedStatement.setDate(2, java.sql.Date.valueOf(currentDate));
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) >= 3; // Return true if reset attempts are >= 3 for the day
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    private static boolean isResetAttemptExists(Connection connection, String email) {
        // Check if an entry exists for the given email and current date
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM password_reset_attempts WHERE email = ? AND reset_date = ?")) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0; // Return true if an entry exists
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    private static void updateResetAttempts(Connection connection, String email) {
        // Check if an entry with the given email and current date already exists
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM password_reset_attempts WHERE email = ? AND reset_date = CURDATE()")) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    // If no entry exists, insert a new one
                    try (PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO password_reset_attempts (email, reset_date, reset_time) VALUES (?, CURDATE(), ?)")) {
                        insertStatement.setString(1, email);
                        insertStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                        insertStatement.executeUpdate();
                    } catch (SQLException e) {
                        // Check if it's a duplicate entry exception
                        if (e instanceof SQLIntegrityConstraintViolationException) {
                            System.out.println("Duplicate entry detected. Skipping insertion.\n" +
                                    "--------------------------------------------------------------\n");
                        } else {
                            e.printStackTrace();
                        }
                    }
                } else {
                    System.out.println("Reset attempt already exists for today. Skipping insertion.\n" +
                            "--------------------------------------------------------------\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private static void displayResetAttemptTimes(Connection connection, String email) {
        // Display the reset attempt times for the user
        System.out.println("Your reset attempt times today:");
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT reset_time FROM password_reset_attempts WHERE email = ? AND reset_date = ?")) {
            preparedStatement.setString(1, email);
            preparedStatement.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    LocalTime resetTime = resultSet.getTime("reset_time").toLocalTime();
                    System.out.println("Attempt time: " + resetTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    static void main(String[] args) {
        String name = getNameFromUser();
        System.out.println("Valid name entered: " + name);
    }

    static String getNameFromUser() {
        String name;
        boolean isValid = false;

        do {
            System.out.print("Enter name (uppercase, min 6, max 25 chars, no numbers/special chars, can have spaces): ");
            name = scanner.nextLine().trim().toUpperCase();
            // Validate name
            if (name.matches("^[A-Z][A-Za-z\\s]{5,24}$")) {
                isValid = true;
            } else {
                System.out.println("Invalid name format. Please try again.");
            }
        } while (!isValid);

        return name;
    }
    static String getEmailFromUser(Connection connection) {
        while (true) {
            System.out.print("Enter email (should have @gmail.com): ");
            String userEmail = scanner.nextLine();
            // Validate email
            if (!userEmail.matches(".+@gmail\\.com")) {
                System.out.println("Invalid email format. Please try again.");
                continue;
            }
            // Check for duplicate email
            if (isEmailExists(connection, userEmail)) {
                System.out.println("Email already exists. Please use a different email.");
                continue;
            }
            return userEmail;
        }
    }
    static String getPasswordFromUser() {
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        // Validate password strength
        if (!isValidPassword(password)) {
            System.out.println("Weak password. Please use a stronger password.");
            return null;
        }
        return password;
    }

    static String getPhoneFromUser(Connection connection) {
        while (true) {
            System.out.print("Enter phone number: ");
            String phoneNumber = scanner.nextLine().trim();

            // Validate phone number format
            if (phoneNumber.length() < 8) {
                System.out.println("Invalid phone number format. Please enter a phone number with at least 10 digits.");
                continue;
            } else if (!phoneNumber.matches("\\d+")) {
                System.out.println("Invalid phone number format. Please enter digits only.");
                continue;
            }
            // Check for duplicate phone number
            if (isPhoneExists(connection, phoneNumber)) {
                System.out.println("Phone number already exists. Please use a different phone number.");
                continue;
            }
            return phoneNumber;
        }
    }
    static boolean isPhoneExists(Connection connection, String phoneNumber) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE phone = ?")) {
            preparedStatement.setString(1, phoneNumber);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int count = resultSet.getInt(1);
                    return count > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    static String getAddressFromUser() {
        System.out.println("Please select your district in Phnom Penh:");
        System.out.println("1. Chamkar Mon");
        System.out.println("2. Dangkao");
        System.out.println("3. Meanchey");
        System.out.println("4. Prampir Makara");
        System.out.println("5. Russei Keo");
        System.out.println("6. Sen Sok");
        System.out.println("7. Tuol Kouk");

        int districtChoice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        String address = "";
        switch (districtChoice) {
            case 1:
                address = selectSection("Chamkar Mon");
                break;
            case 2:
                address = selectSection("Dangkao");
                break;
            case 3:
                address = selectSection("Meanchey");
                break;
            case 4:
                address = selectSection("Prampir Makara");
                break;
            case 5:
                address = selectSection("Russei Keo");
                break;
            case 6:
                address = selectSection("Sen Sok");
                break;
            case 7:
                address = selectSection("Tuol Kouk");
                break;
            default:
                System.out.println("Invalid choice. Please try again.");
                return getAddressFromUser();
        }

        System.out.println("Please enter your address in Phnom Penh (village and street number):");
        String additionalAddress = scanner.nextLine().trim();
        if (!additionalAddress.isEmpty()) {
            address += ", " + additionalAddress;
        }
        return address;
    }


    private static String selectSection(String district) {
        System.out.println("Please select your section in " + district + ":");
        switch (district) {
            case "Chamkar Mon":
                return selectFromList("Chamkar Mon", new String[]{"Boeung Trabek", "Boeung Keng Kang 1", "Boeung Keng Kang 2", "Boeung Keng Kang 3", "Olympic", "Toul Svay Prey 1", "Toul Svay Prey 2", "Toul Tom Poung 1", "Toul Tom Poung 2"});
            case "Dangkao":
                return selectFromList("Dangkao", new String[]{"Dangkao", "Kakab", "Krang Thnong", "Pong Teuk", "Preaek Kampues", "Prey Sar", "Samraong Kraom", "Spean Thma"});
            case "Meanchey":
                return selectFromList("Meanchey", new String[]{"Boeung Tumpun", "Chak Angrae Kraom", "Chak Angrae Leu", "Chakto Mukh", "Stueng Mean Chey", "Tomnob Tek", "Veal Sbov"});
            case "Prampir Makara":
                return selectFromList("Prampir Makara", new String[]{"Monorom", "O'Russei 1", "O'Russei 2", "Phsar Depo 1", "Phsar Depo 2", "Phsar Thmei 1", "Phsar Thmei 2", "Prampir Makara", "Srah Chak", "Teuk La'ak 1", "Teuk La'ak 2", "Toek La'ak 3"});
            case "Russei Keo":
                return selectFromList("Russei Keo", new String[]{"Cheung Aek", "Chrang Chamreh Ti Muoy", "Chrang Chamreh Ti Pir", "Kilomet Lekh Prammuoy", "Kilomet Lekh Pir", "Preaek Lieb"});
            case "Sen Sok":
                return selectFromList("Sen Sok", new String[]{"Khmuonh", "Phnom Penh Thmey", "Pong Tuek", "Teuk Thla"});
            case "Tuol Kouk":
                return selectFromList("Tuol Kouk", new String[]{"Boeung Kak 1", "Boeung Kak 2", "Teuk La'ak 1", "Teuk La'ak 2", "Teuk Thla"});
            default:
                return "";
        }
    }

    private static String selectFromList(String district, String[] sections) {
        System.out.println("Sections in " + district + ":");
        for (int i = 0; i < sections.length; i++) {
            System.out.println((i + 1) + ". " + sections[i]);
        }
        int sectionChoice = scanner.nextInt();
        scanner.nextLine(); // Consume newline
        if (sectionChoice >= 1 && sectionChoice <= sections.length) {
            return sections[sectionChoice - 1] + ", " + district;
        } else {
            System.out.println("Invalid choice. Please try again.");
            return selectFromList(district, sections);
        }
    }


    static String getDateOfBirthFromUser() {
        System.out.print("Enter date of birth (YYYY-MM-DD): ");
        String dateOfBirth = scanner.nextLine();
        // Validate date of birth (you can add more specific validation if needed)
        if (!dateOfBirth.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("Invalid date of birth format. Please enter in YYYY-MM-DD format.");
            return null;
        }
        return dateOfBirth;
    }

    static String getMedicalHistoryFromUser() {
        System.out.print("Do you have any medical history? (yes/no): ");
        String answer = scanner.nextLine().trim().toLowerCase();

        if (answer.equals("yes")) {
            System.out.print("Enter your medical history (max 255 characters): ");
            String medicalHistory = scanner.nextLine().trim();

            // Validate medical history length
            while (medicalHistory.length() > 255) {
                System.out.println("Medical history is too long. Please enter a maximum of 255 characters.");
                System.out.print("Enter your medical history (max 255 characters): ");
                medicalHistory = scanner.nextLine().trim();
            }

            return medicalHistory;
        } else if (answer.equals("no")) {
            return "None";
        } else {
            System.out.println("Invalid response. Please enter 'yes' or 'no'.");
            return getMedicalHistoryFromUser(); // Retry until a valid response is provided
        }
    }
}
