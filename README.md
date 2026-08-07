# AUCA Library Management System

A Java + Hibernate + PostgreSQL console application for managing library operations at AUCA — locations, users, memberships, books, and borrowing, built for the Software Testing and Techniques course.

## Tech Stack

- Java 21
- Maven
- Hibernate ORM 6.5
- PostgreSQL
- JUnit 4

## Project Structure
src/main/java/com/auca/library/

├── domain/      # Entity classes (Location, User, Book, Membership, etc.)

├── dao/         # Generic Hibernate DAO for CRUD operations

├── service/     # Business logic (LibraryService)

├── exception/   # Custom exceptions (BorrowLimitExceededException)

├── util/        # HibernateUtil (SessionFactory setup)

└── App.java     # Console menu entry point

src/test/java/com/auca/library/
└── LibraryServiceTest.java   # JUnit 4 tests covering all requirements
## Database Setup

1. Install PostgreSQL and create a database named `auca_library_db`.
2. Update the connection settings in `src/main/resources/hibernate.cfg.xml` if your username/password differ from the defaults.
3. Hibernate will automatically create all tables on first run (`hbm2ddl.auto = update`).

## How to Run

Compile and run the console app:
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.auca.library.App"
```

Run the test suite:
```bash
mvn test
```

## Features

- **Location hierarchy** — Province → District → Sector → Cell → Village, with duplicate-code and parent validation
- **User registration & authentication**
- **Membership tiers** — Gold (50 RWF/day, 5 books), Silver (30 RWF/day, 3 books), Striver (10 RWF/day, 2 books), auto-seeded on startup
- **Book borrowing** — with borrow-limit enforcement based on membership tier
- **Shelf & room management** — assigning books to shelves, shelves to rooms, and querying book counts per room
- **Late fee calculation** — based on days late × membership daily rate
