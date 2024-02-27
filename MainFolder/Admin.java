package org.example;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.mindrot.jbcrypt.BCrypt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Hashtable;
import java.util.Scanner;

import static org.example.Utils.resetPassword;

public class Admin {
    private Connection connection;
    private String username;
    private String password;
    private String outputDirectory = "D:/From_Telegram/New folder/JAVA";


    public Admin(Connection connection, String username, String password) {
        this.connection = connection;
        System.out.println("Connection object in Admin constructor: " + connection); // Debug statement
        this.username = username;
        this.password = password;
    }
    public boolean login(String enteredUsername, String enteredPassword) {
        return username.equals(enteredUsername) && password.equals(enteredPassword);
    }
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void manageSystem() {
//        while (true) {
            System.out.println("Admin Menu:");
            System.out.println("1. Add Doctor");
            System.out.println("2. Remove Doctor");
            System.out.println("3. Remove User");
            System.out.println("4. Filter doctor info");
            System.out.println("5. View All Doctors");
            System.out.println("6. View All Users");
            System.out.println("7. Reset Doctor Password");
            System.out.println("8. Reset User Password");
            System.out.println("9. Exist");
            Scanner scanner = new Scanner(System.in);
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume the newline character

            switch (choice) {
                case 1:
                    addDoctor();
                    break;
                case 2:
                    removeDoctor();
                    break;
                case 3:
                    removeUser();
                    break;
                case 4:
                    searchDoctors();
                    break;
                case 5:
                    viewAllDoctors();
                    break;
                case 6:
                    viewAllUsers();
                    break;
                case 7:
                    resetDoctorPassword();
                    break;

                case 8:
                    resetPassword(connection);
                    return;
                case 9:
                    System.out.println("Exiting admin menu.");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
//        }
    }

    public void viewAllDoctors() {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT email, name, specialty, contact_details, bio FROM doctors")) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                System.out.println("\n+-----------------------------------------------------------------------------------------------------------------------------------------+");
                System.out.println("|                                                                 All Doctors                                                                |");
                System.out.println("+-----------------------------------------------------------------------------------------------------------------------------------------+");
                System.out.println("| Index | Email                          | Name                           | Specialty                      | Contact Details                | Bio                                   |");
                System.out.println("+-----------------------------------------------------------------------------------------------------------------------------------------+");

                int index = 1;  // Initialize index for counting
                while (resultSet.next()) {
                    String email = resultSet.getString("email");
                    String name = resultSet.getString("name");
                    String specialty = resultSet.getString("specialty");
                    String contactDetails = resultSet.getString("contact_details");
                    String bio = resultSet.getString("bio");
                    System.out.printf("| %-5d | %-30s | %-30s | %-30s | %-30s | %-30s |\n", index, email, name, specialty, contactDetails, bio);
                    index++;  // Increment index for the next row
                }
                System.out.println("+-----------------------------------------------------------------------------------------------------------------------------------------+");
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving doctor data: " + e.getMessage());
            e.printStackTrace();
        }
    }




    public void viewAllUsers() {
        System.out.println("All Users:");
        System.out.println("+----------------------+------------------------+---------------------+");
        System.out.println("|      User Email      |      User Name         |    Registration Date|");
        System.out.println("+----------------------+------------------------+---------------------+");
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM users")) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    System.out.printf("| %-20s | %-22s | %-19s |\n",
                            resultSet.getString("email"),
                            resultSet.getString("name"),
                            resultSet.getDate("registration_date"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching users: " + e.getMessage());
        }
        System.out.println("+----------------------+------------------------+---------------------+");
    }



    private void addDoctor() {
        System.out.println("Enter doctor details:");
        Scanner scanner = new Scanner(System.in);

        // Validate Email
        String email;
        do {
            System.out.print("Email: ");
            email = scanner.nextLine();

            if (email.isBlank()) {
                System.out.println("Email cannot be blank.");
            } else if (!isValidEmailFormat(email)) {
                System.out.println("Invalid email format. Please enter an email ending with '@gmail.com'.");
            } else if (isEmailExists(connection, email, "doctors")) {
                System.out.println("Email already exists. Please enter a different email.");
            }
        } while (email.isBlank() || !isValidEmailFormat(email) || isEmailExists(connection, email, "doctors"));

        // Validate Name (duplicate check removed)
        String name;
        do {
            System.out.print("Name: ");
            name = scanner.nextLine();

            if (name.isBlank()) {
                System.out.println("Name cannot be blank.");
            } else if (!isValidNameFormat(name)) {
                System.out.println("Invalid name format. Please enter a name with uppercase letters, spaces, and a length between 6 and 25 characters.");
            }
        } while (name.isBlank() || !isValidNameFormat(name));

        // Validate Specialty
        String specialty;
        do {
            System.out.print("Specialty: ");
            specialty = scanner.nextLine();

            if (specialty.isBlank()) {
                System.out.println("Specialty cannot be blank.");
            } else if (!isValidSpecialtyFormat(specialty)) {
                System.out.println("Invalid specialty format. Please enter a specialty without spaces and a maximum of 25 characters.");
            }
        } while (specialty.isBlank() || !isValidSpecialtyFormat(specialty));

        // Validate Contact Details
        String contactDetails;
        boolean contactExists = false; // Initialize to false
        do {
            System.out.print("Contact Details: ");
            contactDetails = scanner.nextLine();

            if (contactDetails.isBlank()) {
                System.out.println("Contact details cannot be blank.");
                continue;
            } else if (!isValidContactDetailsFormat(contactDetails)) {
                System.out.println("Invalid contact details format. Please enter only numbers with a length between 5 and 12 digits.");
                continue;
            }

            // Check if contact details already exist
            contactExists = isContactDetailsExists(connection, contactDetails, "doctors");
            if (contactExists) {
                System.out.println("Contact details already exist. Please enter a different contact number.");
            }
        } while (contactDetails.isBlank() || !isValidContactDetailsFormat(contactDetails) || contactExists);


        // Validate Bio (optional)
        String bio;
        System.out.print("Bio (optional): ");
        bio = scanner.nextLine();

        if (bio.length() > 255) {
            System.out.println("Bio exceeds maximum length of 255 characters. Truncating to 255 characters.");
            bio = bio.substring(0, 255);
        }

        // Get and validate password
        String password;
        do {
            System.out.print("Password: ");
            password = scanner.nextLine();

            if (!isValidPasswordFormat(password)) {
                System.out.println("Invalid password format. Please meet the specified requirements.\n(must be at least 8 characters, contain a mix of uppercase letters, lowercase letters, numbers, and symbols)");
            }
        } while (!isValidPasswordFormat(password));

        // Hash the password for security
//        String hashedPassword = hashPassword(password);

        // Get doctor details and add to the database
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO doctors (email, name, specialty, contact_details, bio, password) VALUES (?, ?, ?, ?, ?, ?)")) {
            preparedStatement.setString(1, email);
            preparedStatement.setString(2, name);
            preparedStatement.setString(3, specialty);
            preparedStatement.setString(4, contactDetails);
            preparedStatement.setString(5, bio);
            preparedStatement.setString(6, password);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to add doctor.");
        }

        System.out.println("Doctor added successfully.");
    }

    public void resetDoctorPassword() {
        try {
            System.out.print("Enter doctor email: ");
            Scanner scanner = new Scanner(System.in);
            String email = scanner.nextLine();

            String query = "SELECT * FROM doctors WHERE email = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, email);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        String newPassword = generateRandomPassword();
                        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(6));
                        updateDoctorPassword(email, hashedPassword);

                        System.out.println("Doctor password reset successfully. The new password is: " + newPassword);
                        System.out.println("Generating QR code...");
                        saveQRCode(newPassword);
                    } else {
                        System.out.println("Doctor not found.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to reset doctor password.");
        }
    }

    private void updateDoctorPassword(String email, String hashedPassword) throws SQLException {
        String updateQuery = "UPDATE doctors SET password = ? WHERE email = ?";
        try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
            updateStatement.setString(1, hashedPassword);
            updateStatement.setString(2, email);
            updateStatement.executeUpdate();
        }
    }

    private void saveQRCode(String password) {
        try {
            BufferedImage qrCodeImage = generateQRCodeImage(password);
            File outputDir = new File("D:/From_Telegram/New folder/JAVA");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            File outputFile = new File(outputDir, "qr_code.png");
            ImageIO.write(qrCodeImage, "png", outputFile);
            System.out.println("QR code saved to: " + outputFile.getAbsolutePath());
        } catch (WriterException | IOException e) {
            e.printStackTrace();
            System.out.println("Failed to generate and save QR code.");
        }
    }
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[6]; // increase the size to ensure at least 12 characters
        random.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    private BufferedImage generateQRCodeImage(String data) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 256, 256, hints);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        File outputFile = new File(outputDirectory, "qr_code.png");
        ImageIO.write(image, "png", outputFile);
        return image;
    }
    private boolean isValidPasswordFormat(String password) {
        // Enforce a strong password policy
        // Example: At least 8 characters, mix of uppercase, lowercase, numbers, and symbols
        // Adjust the regex pattern as needed for specific password requirements
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    }

    private boolean isValidEmailFormat(String email) {
        return email.matches("^[a-zA-Z0-9._%+-]+@gmail\\.com$");
    }

    private boolean isValidNameFormat(String name) {
        return name.matches("^[A-Z\\s]{6,25}$");
    }

    private boolean isValidSpecialtyFormat(String specialty) {
        return specialty.matches("^[a-zA-Z0-9]{1,25}$");
    }

    private boolean isValidContactDetailsFormat(String contactDetails) {
        return contactDetails.matches("^[0-9]{5,12}$");
    }


    private boolean isValidEmail(String email) {
        // Add your email validation logic here
        // Example: You can use regular expressions to validate the email format
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    private void removeDoctor() {
        System.out.print("Enter doctor's email to remove: ");
        Scanner scanner = new Scanner(System.in);
        String email = scanner.nextLine();

        if (!isDoctorExists(connection, email)) {
            System.out.println("Doctor not found.");
            return;
        }

        // Remove doctor from the database
        try (PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM doctors WHERE email = ?")) {
            preparedStatement.setString(1, email);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to remove doctor.");
        }

        System.out.println("Doctor removed successfully.");
    }

    private void removeUser() {
        System.out.print("Enter user's email to remove: ");
        Scanner scanner = new Scanner(System.in);
        String email = scanner.nextLine();

        if (!isEmailExists(connection, email, "users")) {
            System.out.println("User not found.");
            return;
        }

        // Remove user from the database
        try (PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
            preparedStatement.setString(1, email);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Failed to remove user.");
        }

        System.out.println("User removed successfully.");
    }

    private void searchDoctors() {
        try {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Search doctors by:\n1. Name\n2. Specialty\n3. Contact Detail\n4. Exit");
                int searchOption = scanner.nextInt();
                scanner.nextLine(); // Consume the newline character

                switch (searchOption) {
                    case 1:
                        searchDoctorsByName();
                        break;
                    case 2:
                        searchDoctorsBySpecialty();
                        break;
                    case 3:
                        searchDoctorsByContactDetail();
                        break;
                    case 4:
                        System.out.println("Exiting search menu.");
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
                System.out.println("Do you want to search again? (yes/no)");
                String continueSearch = scanner.nextLine().trim().toLowerCase();
                if (!continueSearch.equals("yes")) {
                    break;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void searchDoctorsByContactDetail() throws SQLException {
        try {
            System.out.print("Enter doctor's contact detail to search: ");
            Scanner scanner = new Scanner(System.in);
            String contactDetail = scanner.nextLine();

            // Perform search based on the contact detail
            String query = "SELECT * FROM doctors WHERE contact_details LIKE ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, "%" + contactDetail + "%");
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    // Display search results
                    System.out.println("\nSearch Results:");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                    System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", "Doctor Email", "Name", "Specialty", "Contact Details", "Bio");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");

                    while (resultSet.next()) {
                        String email = resultSet.getString("email");
                        String doctorName = resultSet.getString("name");
                        String doctorSpecialty = resultSet.getString("specialty");
                        String contactDetails = resultSet.getString("contact_details");
                        String bio = resultSet.getString("bio");

                        // Truncate long values for display
                        doctorName = truncateString(doctorName, 35);
                        doctorSpecialty = truncateString(doctorSpecialty, 35);
                        contactDetails = truncateString(contactDetails, 35);
                        bio = truncateString(bio, 35);

                        System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", email, doctorName, doctorSpecialty, contactDetails, bio);
                    }
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void searchDoctorsByName() {
        try {
            System.out.print("Enter doctor's name to search: ");
            Scanner scanner = new Scanner(System.in);
            String name = scanner.nextLine();

            // Perform search based on the name
            String query = "SELECT * FROM doctors WHERE name LIKE ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, "%" + name + "%");
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    // Display search results
                    System.out.println("\nSearch Results:");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                    System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", "Doctor Email", "Name", "Specialty", "Contact Details", "Bio");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");

                    while (resultSet.next()) {
                        String email = resultSet.getString("email");
                        String doctorName = resultSet.getString("name");
                        String specialty = resultSet.getString("specialty");
                        String contactDetails = resultSet.getString("contact_details");
                        String bio = resultSet.getString("bio");

                        // Truncate long values for display
                        doctorName = truncateString(doctorName, 35);
                        specialty = truncateString(specialty, 35);
                        contactDetails = truncateString(contactDetails, 35);
                        bio = truncateString(bio, 35);

                        System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", email, doctorName, specialty, contactDetails, bio);
                    }
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }


    private void searchDoctorsBySpecialty() {
        try {
            System.out.print("Enter doctor's specialty to search: ");
            Scanner scanner = new Scanner(System.in);
            String specialty = scanner.nextLine();

            // Perform search based on the specialty
            String query = "SELECT * FROM doctors WHERE specialty LIKE ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, "%" + specialty + "%");
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    // Display search results
                    System.out.println("\nSearch Results:");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                    System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", "Doctor Email", "Name", "Specialty", "Contact Details", "Bio");
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");

                    while (resultSet.next()) {
                        String email = resultSet.getString("email");
                        String doctorName = resultSet.getString("name");
                        String doctorSpecialty = resultSet.getString("specialty");
                        String contactDetails = resultSet.getString("contact_details");
                        String bio = resultSet.getString("bio");

                        // Truncate long values for display
                        doctorName = truncateString(doctorName, 35);
                        doctorSpecialty = truncateString(doctorSpecialty, 35);
                        contactDetails = truncateString(contactDetails, 35);
                        bio = truncateString(bio, 35);

                        System.out.printf("| %-36s | %-36s | %-36s | %-36s | %-36s |\n", email, doctorName, doctorSpecialty, contactDetails, bio);
                    }
                    System.out.println("+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+--------------------------------------+");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isEmailExists(Connection connection, String email, String tableName) {
        String query = "SELECT * FROM " + tableName + " WHERE email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    private boolean isContactDetailsExists(Connection connection, String contactDetails, String tableName) {
        String query = "SELECT * FROM " + tableName + " WHERE contact_details = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, contactDetails);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    private boolean isDoctorExists(Connection connection, String email) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM doctors WHERE email = ?")) {
            preparedStatement.setString(1, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


}
