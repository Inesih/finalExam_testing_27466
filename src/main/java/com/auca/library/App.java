package com.auca.library;

import com.auca.library.dao.GenericDao;
import com.auca.library.domain.*;
import com.auca.library.exception.BorrowLimitExceededException;
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

        libraryService.seedMembershipTypes();

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
                        registerMembershipForUser();
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
            } catch (BorrowLimitExceededException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n--- MAIN MENU ---");
        System.out.println("1. Add Location (Province/District/Sector/Cell/Village)");
        System.out.println("2. Register User");
        System.out.println("3. Test User Authentication");
        System.out.println("4. Register Membership for User (Gold/Silver/Striver)");
        System.out.println("5. Create Book & Borrow It");
        System.out.println("6. Exit");
    }

    private static void createLocationHierarchy() {
        System.out.print("Enter Province Code: ");
        String provinceCode = scanner.nextLine();
        System.out.print("Enter Province Name: ");
        String provinceName = scanner.nextLine();
        Location province = libraryService.createLocation(
                new Location(provinceCode, provinceName, ELocationType.PROVINCE, null), null);
        System.out.println("-> Created Province ID: " + province.getId());

        System.out.print("Enter District Code: ");
        String districtCode = scanner.nextLine();
        System.out.print("Enter District Name: ");
        String districtName = scanner.nextLine();
        Location district = libraryService.createLocation(
                new Location(districtCode, districtName, ELocationType.DISTRICT, null), province.getId());
        System.out.println("-> Created District ID: " + district.getId());

        System.out.print("Enter Sector Code: ");
        String sectorCode = scanner.nextLine();
        System.out.print("Enter Sector Name: ");
        String sectorName = scanner.nextLine();
        Location sector = libraryService.createLocation(
                new Location(sectorCode, sectorName, ELocationType.SECTOR, null), district.getId());
        System.out.println("-> Created Sector ID: " + sector.getId());

        System.out.print("Enter Cell Code: ");
        String cellCode = scanner.nextLine();
        System.out.print("Enter Cell Name: ");
        String cellName = scanner.nextLine();
        Location cell = libraryService.createLocation(
                new Location(cellCode, cellName, ELocationType.CELL, null), sector.getId());
        System.out.println("-> Created Cell ID: " + cell.getId());

        System.out.print("Enter Village Code: ");
        String villageCode = scanner.nextLine();
        System.out.print("Enter Village Name: ");
        String villageName = scanner.nextLine();
        Location village = libraryService.createLocation(
                new Location(villageCode, villageName, ELocationType.VILLAGE, null), cell.getId());
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
        user.setRole(ERole.STUDENT);

        if (!villageIdStr.isBlank()) {
            try {
                Location loc = new GenericDao<>(Location.class).findById(UUID.fromString(villageIdStr));
                if (loc != null) {
                    user.setLocation(loc);
                } else {
                    System.out.println("-> No location found for that UUID, saving without location.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("-> Invalid UUID format, saving without location.");
            }
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

    private static void registerMembershipForUser() {
        System.out.print("Enter User UUID: ");
        UUID userId = UUID.fromString(scanner.nextLine());

        System.out.println("Choose Membership Tier: 1) Gold  2) Silver  3) Striver");
        String tierChoice = scanner.nextLine().trim();

        String tierName;
        switch (tierChoice) {
            case "1": tierName = MembershipTierConstants.GOLD; break;
            case "2": tierName = MembershipTierConstants.SILVER; break;
            case "3": tierName = MembershipTierConstants.STRIVER; break;
            default:
                System.out.println("Invalid tier choice.");
                return;
        }

        MembershipType type = libraryService.findMembershipTypeByName(tierName);
        if (type == null) {
            System.out.println("Membership type not found — seeding may have failed.");
            return;
        }

        Membership membership = libraryService.registerMembership(userId, type.getId());
        System.out.println("-> Membership registered (status: " + membership.getStatus()
                + ")! Code: " + membership.getMembershipCode());
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