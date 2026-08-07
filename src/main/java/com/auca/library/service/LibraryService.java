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

    public Location createLocation(Location location, UUID parentId) {
        if (location.getCode() != null && findLocationByCode(location.getCode()) != null) {
            throw new IllegalArgumentException("Location code already exists: " + location.getCode());
        }

        if (location.getType() == ELocationType.PROVINCE) {
            location.setParent(null);
        } else {
            if (parentId == null) {
                throw new IllegalArgumentException("Parent location is required for type " + location.getType());
            }
            Location parent = locationDao.findById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Parent location not found");
            }
            location.setParent(parent);
        }

        locationDao.save(location);
        return location;
    }

    private Location findLocationByCode(String code) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Location WHERE code = :c", Location.class)
                    .setParameter("c", code)
                    .uniqueResult();
        }
    }

    public String getProvinceNameByVillageId(UUID villageId) {
        Location location = locationDao.findById(villageId);
        if (location == null) return null;

        while (location.getParent() != null) {
            location = location.getParent();
        }
        return location.getName();
    }

    public String getProvinceNameByPersonId(UUID personId) {
        User user = userDao.findById(personId);
        if (user == null || user.getLocation() == null) return null;

        return getProvinceNameByVillageId(user.getLocation().getId());
    }

    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.createQuery("FROM User WHERE username = :u", User.class)
                    .setParameter("u", username)
                    .uniqueResult();

            return user != null && user.getPassword().equals(password);
        }
    }

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

    public MembershipType findMembershipTypeByName(String name) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM MembershipType WHERE name = :n", MembershipType.class)
                    .setParameter("n", name)
                    .uniqueResult();
        }
    }

    public Borrower borrowBook(UUID readerId, UUID bookId) {
        validateBorrowLimit(readerId);

        Book book = bookDao.findById(bookId);
        User reader = userDao.findById(readerId);

        if (book.getStatus() != EBookStatus.AVAILABLE) {
            throw new IllegalStateException("Book is not available!");
        }

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

    public void assignShelfToRoom(UUID shelfId, UUID roomId) {
        Shelf shelf = shelfDao.findById(shelfId);
        Room room = roomDao.findById(roomId);

        if (shelf != null && room != null) {
            shelf.setRoom(room);
            shelfDao.update(shelf);
        }
    }

    public int countBooksInRoom(UUID roomId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery("SELECT count(b) FROM Book b WHERE b.shelf.room.id = :r", Long.class)
                    .setParameter("r", roomId)
                    .uniqueResult();
            return count != null ? count.intValue() : 0;
        }
    }

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