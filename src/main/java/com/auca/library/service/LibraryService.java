package com.auca.library.service;

import com.auca.library.dao.GenericDao;
import com.auca.library.domain.*;
import com.auca.library.exception.BorrowLimitExceededException;
import com.auca.library.util.HibernateUtil;
import org.hibernate.Session;

import java.util.*;

public class LibraryService {

    private GenericDao<Location> locationDao = new GenericDao<>(Location.class);
    private GenericDao<User> userDao = new GenericDao<>(User.class);
    private GenericDao<MembershipType> membershipTypeDao = new GenericDao<>(MembershipType.class);
    private GenericDao<Membership> membershipDao = new GenericDao<>(Membership.class);
    private GenericDao<Room> roomDao = new GenericDao<>(Room.class);
    private GenericDao<Shelf> shelfDao = new GenericDao<>(Shelf.class);
    private GenericDao<Book> bookDao = new GenericDao<>(Book.class);
    private GenericDao<Borrower> borrowerDao = new GenericDao<>(Borrower.class);

    // Requirement 1: Create Location Hierarchy
    public Location createLocation(Location location, UUID parentId) {
        if (parentId != null) {
            Location parent = locationDao.findById(parentId);
            location.setParent(parent);
        }
        locationDao.save(location);
        return location;
    }

    // Requirement 2: Village ID -> Province Name
    public String getProvinceNameByVillageId(UUID villageId) {
        Location location = locationDao.findById(villageId);
        if (location == null) return null;

        // Traverse up the chain until reaching the top parent (Province)
        while (location.getParent() != null) {
            location = location.getParent();
        }
        return location.getName();
    }

    // Requirement 3: Person ID -> Province Name
    public String getProvinceNameByPersonId(UUID personId) {
        User user = userDao.findById(personId);
        if (user == null || user.getLocation() == null) return null;

        return getProvinceNameByVillageId(user.getLocation().getId());
    }

    // Requirement 4: Authenticate User
    public boolean authenticate(String username, String password) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery("FROM User WHERE username = :u", User.class)
                    .setParameter("u", username)
                    .uniqueResult();

            return user != null && user.getPassword().equals(password);
        }
    }

    // Requirement 5: Register Membership
   public Membership registerMembership(UUID userId, UUID membershipTypeId) {
    User user = userDao.findById(userId);
    MembershipType type = membershipTypeDao.findById(membershipTypeId);

    Membership existing = findMembershipByUser(userId);
    if (existing != null && existing.getStatus() == EMembershipStatus.ACTIVE) {
        throw new IllegalStateException("User already has an active membership!");
    }

    Membership membership = new Membership("MEM-" + System.currentTimeMillis(), new Date(), user, type);
    membershipDao.save(membership);
    return membership;
}

private Membership findMembershipByUser(UUID userId) {
    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
        return session.createQuery("FROM Membership m WHERE m.user.id = :u", Membership.class)
                .setParameter("u", userId)
                .uniqueResult();
    }
}

public void seedMembershipTypes() {
    createTypeIfMissing(MembershipTierConstants.GOLD, MembershipTierConstants.GOLD_MAX_BOOKS, MembershipTierConstants.GOLD_DAILY_RATE);
    createTypeIfMissing(MembershipTierConstants.SILVER, MembershipTierConstants.SILVER_MAX_BOOKS, MembershipTierConstants.SILVER_DAILY_RATE);
    createTypeIfMissing(MembershipTierConstants.STRIVER, MembershipTierConstants.STRIVER_MAX_BOOKS, MembershipTierConstants.STRIVER_DAILY_RATE);
}

private void createTypeIfMissing(String name, int maxBooks, int dailyRate) {
    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
        MembershipType existing = session.createQuery("FROM MembershipType WHERE name = :n", MembershipType.class)
                .setParameter("n", name)
                .uniqueResult();
        if (existing == null) {
            membershipTypeDao.save(new MembershipType(name, maxBooks, 0, dailyRate));
        }
    }
}
    // Requirement 6: Borrow Book
    public Borrower borrowBook(UUID readerId, UUID bookId) {
        validateBorrowLimit(readerId);

        Book book = bookDao.findById(bookId);
        User reader = userDao.findById(readerId);

        if (book.getStatus() != EBookStatus.AVAILABLE) {
            throw new IllegalStateException("Book is not available!");
        }

        // Set pickup today and due date in 14 days
        Date pickupDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(pickupDate);
        cal.add(Calendar.DAY_OF_MONTH, 14);
        Date dueDate = cal.getTime();

        Borrower borrower = new Borrower(pickupDate, dueDate, reader, book);
        book.setStatus(EBookStatus.BORROWED);

        bookDao.update(book);
        borrowerDao.save(borrower);

        return borrower;
    }

    // Requirement 7: Validate Borrow Limit
   public void validateBorrowLimit(UUID readerId) {
    Membership membership = null;
    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
        membership = session.createQuery("FROM Membership m WHERE m.user.id = :u", Membership.class)
                .setParameter("u", readerId)
                .uniqueResult();
    }

    if (membership == null || membership.getStatus() != EMembershipStatus.ACTIVE) {
        throw new BorrowLimitExceededException("User does not have an active membership!");
    }

    long activeBorrows = 0;
    try (Session session = HibernateUtil.getSessionFactory().openSession()) {
        Long count = session.createQuery("SELECT count(b) FROM Borrower b WHERE b.reader.id = :u AND b.returnDate IS NULL", Long.class)
                .setParameter("u", readerId)
                .uniqueResult();
        if (count != null) activeBorrows = count;
    }

    if (activeBorrows >= membership.getMembershipType().getMaxBooks()) {
        throw new BorrowLimitExceededException("Borrow limit reached for this membership level!");
    }
}
    // Requirement 8: Assign Book to Shelf
    public void assignBookToShelf(UUID bookId, UUID shelfId) {
        Book book = bookDao.findById(bookId);
        Shelf shelf = shelfDao.findById(shelfId);

        if (book != null && shelf != null) {
            book.setShelf(shelf);
            bookDao.update(book);

            shelf.setAvailableStock(shelf.getAvailableStock() + 1);
            shelfDao.update(shelf);
        }
    }

    // Requirement 9: Assign Shelf to Room
    public void assignShelfToRoom(UUID shelfId, UUID roomId) {
        Shelf shelf = shelfDao.findById(shelfId);
        Room room = roomDao.findById(roomId);

        if (shelf != null && room != null) {
            shelf.setRoom(room);
            shelfDao.update(shelf);
        }
    }

    // Requirement 10: Count Books in Room
    public int countBooksInRoom(UUID roomId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery("SELECT count(b) FROM Book b WHERE b.shelf.room.id = :r", Long.class)
                    .setParameter("r", roomId)
                    .uniqueResult();
            return count != null ? count.intValue() : 0;
        }
    }

    // Requirement 11: Find Room with Fewest Books
    public Room findRoomWithFewestBooks() {
        List<Room> rooms = roomDao.findAll();
        if (rooms.isEmpty()) return null;

        Room smallestRoom = null;
        int minBooks = Integer.MAX_VALUE;

        for (Room room : rooms) {
            int currentCount = countBooksInRoom(room.getId());
            if (currentCount < minBooks) {
                minBooks = currentCount;
                smallestRoom = room;
            }
        }
        return smallestRoom;
    }

    // Requirement 12: Calculate Late Fee
    public int calculateLateFee(UUID borrowerId) {
        Borrower borrower = borrowerDao.findById(borrowerId);
        if (borrower == null || borrower.getDueDate() == null) return 0;

        Date checkDate = borrower.getReturnDate() != null ? borrower.getReturnDate() : new Date();

        long diffInMillis = checkDate.getTime() - borrower.getDueDate().getTime();
        long daysLate = diffInMillis / (1000 * 60 * 60 * 24);

        if (daysLate <= 0) return 0;

        Membership membership = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            membership = session.createQuery("FROM Membership m WHERE m.user.id = :u", Membership.class)
                    .setParameter("u", borrower.getReader().getId())
                    .uniqueResult();
        }

        int rate = (membership != null && membership.getMembershipType() != null)
                ? membership.getMembershipType().getDailyRate()
                : 0;

        return (int) daysLate * rate;
    }
}
