package com.auca.library;

import com.auca.library.dao.GenericDao;
import com.auca.library.domain.*;
import com.auca.library.service.LibraryService;

import java.util.Scanner;
import java.util.UUID;

public class App {

    private static final LibraryService libraryService = new LibraryService();
    private static final GenericDao<User> userDao = new GenericDao<>(User.class);
    private static final GenericDao<MembershipType> membershipTypeDao = new GenericDao<>(MembershipType.class);
    private static final GenericDao<Book> bookDao = new GenericDao<>(Book.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println(" Welcome to Library Management System ");
        System.out.println("=========================================");

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option (1-6): ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        createLocationHierarchy();
                        break;
                    case "2":
                        registerUser();
                        break;
                    case "3":
                        authenticateUser();
                        break;
                    case "4":
                        createMembershipTypeAndRegister();
                        break;
                    case "5":
                        addAndBorrowBook();
                        break;
                    case "6":
                        running = false;
                        System.out.println("\nExiting application. Goodbye!");
                        break;
                    default:
                        System.out.println("Invalid choice. Please enter 1-6.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n--- MAIN MENU ---");
        System.out.println("1. Add Location (Province/Village)");
        System.out.println("2. Register User");
        System.out.println("3. Test User Authentication");
        System.out.println("4. Create Membership Type & Assign to User");
        System.out.println("5. Create Book & Borrow It");
        System.out.println("6. Exit");
    }

    private static void createLocationHierarchy() {
        System.out.print("Enter Province Name (e.g., Kigali City): ");
        String provinceName = scanner.nextLine();
        Location province = libraryService.createLocation(new Location(provinceName, null), null);

        System.out.print("Enter Village Name under " + provinceName + ": ");
        String villageName = scanner.nextLine();
        Location village = libraryService.createLocation(new Location(villageName, null), province.getId());

        System.out.println("-> Created Village ID: " + village.getId());
        System.out.println("-> Top Province retrieved: " + libraryService.getProvinceNameByVillageId(village.getId()));
    }

    private static void registerUser() {
        System.out.print("Enter First Name: ");
        String firstName = scanner.nextLine();
        System.out.print("Enter Last Name: ");
        String lastName = scanner.nextLine();
        System.out.print("Enter Username: ");
        String username = scanner.nextLine();
        System.out.print("Enter Password: ");
        String password = scanner.nextLine();
        System.out.print("Enter Village Location UUID (or press Enter to skip): ");
        String villageIdStr = scanner.nextLine();

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setPassword(password);

        if (!villageIdStr.isBlank()) {
            Location loc = new GenericDao<>(Location.class).findById(UUID.fromString(villageIdStr));
            user.setLocation(loc);
        }

        userDao.save(user);
        System.out.println("-> User saved successfully! User ID: " + user.getId());
    }

    private static void authenticateUser() {
        System.out.print("Enter Username: ");
        String username = scanner.nextLine();
        System.out.print("Enter Password: ");
        String password = scanner.nextLine();

        boolean result = libraryService.authenticate(username, password);
        if (result) {
            System.out.println("-> Authentication SUCCESSFUL!");
        } else {
            System.out.println("-> Authentication FAILED! Check username/password.");
        }
    }

    private static void createMembershipTypeAndRegister() {
        System.out.print("Enter Membership Type Name (e.g., Gold): ");
        String name = scanner.nextLine();
        System.out.print("Enter Max Books allowed: ");
        int maxBooks = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter Daily Late Rate (RWF): ");
        int dailyRate = Integer.parseInt(scanner.nextLine());

        MembershipType type = new MembershipType();
        type.setName(name);
        type.setMaxBooks(maxBooks);
        type.setDailyRate(dailyRate);
        membershipTypeDao.save(type);

        System.out.print("Enter User UUID to assign this membership: ");
        UUID userId = UUID.fromString(scanner.nextLine());

        Membership membership = libraryService.registerMembership(userId, type.getId());
        System.out.println("-> Membership registered successfully! Code: " + membership.getMembershipCode());
    }

    private static void addAndBorrowBook() {
        System.out.print("Enter Book Title: ");
        String title = scanner.nextLine();

        Book book = new Book();
        book.setTitle(title);
        book.setStatus(EBookStatus.AVAILABLE);
        bookDao.save(book);

        System.out.print("Enter Borrower (User) UUID: ");
        UUID userId = UUID.fromString(scanner.nextLine());

        Borrower record = libraryService.borrowBook(userId, book.getId());
        System.out.println("-> Book borrowed! Pickup Date: " + record.getPickupDate() + " | Due Date: " + record.getDueDate());
    }
}