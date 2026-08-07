package com.auca.library;

import com.auca.library.dao.GenericDao;
import com.auca.library.domain.*;
import com.auca.library.exception.BorrowLimitExceededException;
import com.auca.library.service.LibraryService;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

public class LibraryServiceTest {

    private LibraryService service;
    private final GenericDao<User> userDao = new GenericDao<>(User.class);
    private final GenericDao<Membership> membershipDao = new GenericDao<>(Membership.class);
    private final GenericDao<Book> bookDao = new GenericDao<>(Book.class);
    private final GenericDao<Borrower> borrowerDao = new GenericDao<>(Borrower.class);
    private final GenericDao<Shelf> shelfDao = new GenericDao<>(Shelf.class);
    private final GenericDao<Room> roomDao = new GenericDao<>(Room.class);

    @Before
    public void setUp() {
        service = new LibraryService();
        service.seedMembershipTypes(); // make sure Gold/Silver/Striver exist before every test
    }

    // ===================== Requirement 1: Location hierarchy =====================

    @Test
    public void createProvince_withNoParent_succeeds() {
        // A Province is the top level, so it should not need a parent.
        Location province = new Location(unique(), "TestProvince", ELocationType.PROVINCE, null);
        Location saved = service.createLocation(province, null);

        assertNotNull(saved.getId());
        assertNull(saved.getParent());
    }

    @Test
    public void createDistrict_withValidProvinceParent_succeeds() {
        // A District must be linked to a real Province.
        Location province = service.createLocation(new Location(unique(), "P", ELocationType.PROVINCE, null), null);
        Location district = service.createLocation(new Location(unique(), "D", ELocationType.DISTRICT, null), province.getId());

        assertNotNull(district.getId());
        assertEquals(province.getId(), district.getParent().getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createDistrict_withMissingParent_throwsException() {
        // A District without a parent Province should be rejected.
        service.createLocation(new Location(unique(), "D", ELocationType.DISTRICT, null), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createLocation_duplicateLocationCode_throwsException() {
        // Two locations cannot share the same code.
        String code = unique();
        service.createLocation(new Location(code, "P1", ELocationType.PROVINCE, null), null);
        service.createLocation(new Location(code, "P2", ELocationType.PROVINCE, null), null);
    }

    // ===================== Requirement 2 =====================

    @Test
    public void validVillageId_returnsCorrectProvinceName() {
        // Build a full chain: Province -> District -> Sector -> Cell -> Village
        Location province = service.createLocation(new Location(unique(), "Kigali", ELocationType.PROVINCE, null), null);
        Location district = service.createLocation(new Location(unique(), "Gasabo", ELocationType.DISTRICT, null), province.getId());
        Location sector = service.createLocation(new Location(unique(), "Kimironko", ELocationType.SECTOR, null), district.getId());
        Location cell = service.createLocation(new Location(unique(), "Cell1", ELocationType.CELL, null), sector.getId());
        Location village = service.createLocation(new Location(unique(), "Village1", ELocationType.VILLAGE, null), cell.getId());

        // Looking up the Village should walk back up the chain and return the Province name.
        assertEquals("Kigali", service.getProvinceNameByVillageId(village.getId()));
    }

    // ===================== Requirement 3 =====================

    @Test
    public void validPersonId_returnsCorrectProvinceName() {
        Location province = service.createLocation(new Location(unique(), "Northern", ELocationType.PROVINCE, null), null);
        Location district = service.createLocation(new Location(unique(), "D", ELocationType.DISTRICT, null), province.getId());
        Location sector = service.createLocation(new Location(unique(), "S", ELocationType.SECTOR, null), district.getId());
        Location cell = service.createLocation(new Location(unique(), "C", ELocationType.CELL, null), sector.getId());
        Location village = service.createLocation(new Location(unique(), "V", ELocationType.VILLAGE, null), cell.getId());

        User user = createTestUser("pass");
        user.setLocation(village);
        userDao.update(user);

        // Given a user's ID, we should be able to trace their Province through their village.
        assertEquals("Northern", service.getProvinceNameByPersonId(user.getId()));
    }

    // ===================== Requirement 5: authenticate =====================

    @Test
    public void authenticate_correctCredentials_returnsTrue() {
        User user = createTestUser("pass123");
        assertTrue(service.authenticate(user.getUsername(), "pass123"));
    }

    @Test
    public void authenticate_wrongPassword_returnsFalse() {
        User user = createTestUser("pass123");
        assertFalse(service.authenticate(user.getUsername(), "wrongpass"));
    }

    @Test
    public void authenticate_unknownUsername_returnsFalse() {
        assertFalse(service.authenticate("nonexistent_" + unique(), "any"));
    }

    @Test
    public void authenticate_nullOrBlankInput_returnsFalse() {
        // Empty or missing input should never crash — just fail safely.
        assertFalse(service.authenticate(null, null));
        assertFalse(service.authenticate("", ""));
        assertFalse(service.authenticate("   ", "   "));
    }

    // ===================== Requirement 6: registerMembership =====================

    @Test
    public void registerMembership_gold_createsPendingMembershipLinkedToGoldType() {
        User user = createTestUser("pass");
        MembershipType gold = service.findMembershipTypeByName(MembershipTierConstants.GOLD);

        Membership membership = service.registerMembership(user.getId(), gold.getId());

        // A brand-new membership should start as PENDING, not ACTIVE.
        assertNotNull(membership.getId());
        assertEquals(EMembershipStatus.PENDING, membership.getStatus());
        assertEquals(gold.getId(), membership.getMembershipType().getId());
    }

    @Test(expected = IllegalStateException.class)
    public void registerMembership_userAlreadyHasActiveMembership_throwsException() {
        User user = createTestUser("pass");
        MembershipType gold = service.findMembershipTypeByName(MembershipTierConstants.GOLD);

        // Create a membership and manually mark it ACTIVE.
        Membership membership = service.registerMembership(user.getId(), gold.getId());
        membership.setStatus(EMembershipStatus.ACTIVE);
        membershipDao.update(membership);

        // Trying to register again should now be blocked.
        service.registerMembership(user.getId(), gold.getId());
    }

    // ===================== Requirement 7: borrowBook =====================

    @Test
    public void borrowBook_availableBook_createsBorrowerRecordWithZeroFine() {
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Book book = createAvailableBook();

        Borrower borrower = service.borrowBook(user.getId(), book.getId());

        assertNotNull(borrower.getId());
        assertEquals(0, borrower.getFine());
    }

    @Test
    public void borrowBook_setsBookStatusToBorrowed() {
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Book book = createAvailableBook();

        service.borrowBook(user.getId(), book.getId());

        Book updatedBook = bookDao.findById(book.getId());
        assertEquals(EBookStatus.BORROWED, updatedBook.getStatus());
    }

    @Test
    public void borrowBook_dueDateIsPickupDatePlusLoanPeriod() {
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Book book = createAvailableBook();

        Borrower borrower = service.borrowBook(user.getId(), book.getId());

        // Due date should simply be 14 days after pickup date.
        long millisBetween = borrower.getDueDate().getTime() - borrower.getPickupDate().getTime();
        long daysBetween = millisBetween / (1000 * 60 * 60 * 24);

        assertEquals(14, daysBetween);
    }

    // ===================== Requirement 8: validateBorrowLimit =====================

    @Test
    public void goldMember_withFourActiveBorrows_canBorrowAFifth() {
        // Gold allows up to 5 books, so after 4 borrows a 5th should still be allowed.
        User user = createActiveMember(MembershipTierConstants.GOLD);
        borrowBooksForUser(user, 4);

        service.validateBorrowLimit(user.getId()); // should NOT throw
    }

    @Test(expected = BorrowLimitExceededException.class)
    public void goldMember_withFiveActiveBorrows_cannotBorrowASixth() {
        User user = createActiveMember(MembershipTierConstants.GOLD);
        borrowBooksForUser(user, 5);

        service.validateBorrowLimit(user.getId()); // should throw
    }

    @Test(expected = BorrowLimitExceededException.class)
    public void silverMember_withThreeActiveBorrows_isBlocked() {
        User user = createActiveMember(MembershipTierConstants.SILVER);
        borrowBooksForUser(user, 3);

        service.validateBorrowLimit(user.getId());
    }

    @Test(expected = BorrowLimitExceededException.class)
    public void striverMember_withTwoActiveBorrows_isBlocked() {
        User user = createActiveMember(MembershipTierConstants.STRIVER);
        borrowBooksForUser(user, 2);

        service.validateBorrowLimit(user.getId());
    }

    @Test(expected = BorrowLimitExceededException.class)
    public void userWithoutApprovedMembership_isBlocked() {
        // No membership at all should block borrowing.
        User user = createTestUser("pass");
        service.validateBorrowLimit(user.getId());
    }

    // ===================== Requirement 9: assignBookToShelf =====================

    @Test
    public void assignBookToShelf_updatesBookShelfId() {
        Shelf shelf = createShelf();
        Book book = createAvailableBook();

        service.assignBookToShelf(book.getId(), shelf.getId());

        Book updatedBook = bookDao.findById(book.getId());
        assertEquals(shelf.getId(), updatedBook.getShelf().getId());
    }

    @Test
    public void assignBookToShelf_incrementsShelfAvailableStock() {
        Shelf shelf = createShelf();
        Book book = createAvailableBook();

        service.assignBookToShelf(book.getId(), shelf.getId());

        Shelf updatedShelf = shelfDao.findById(shelf.getId());
        assertEquals(1, updatedShelf.getAvailableStock());
    }

    // ===================== Requirement 10: assignShelfToRoom =====================

    @Test
    public void assignShelfToRoom_updatesShelfRoomId() {
        Room room = createRoom();
        Shelf shelf = new Shelf("Fiction", 0, null);
        shelfDao.save(shelf);

        service.assignShelfToRoom(shelf.getId(), room.getId());

        Shelf updatedShelf = shelfDao.findById(shelf.getId());
        assertEquals(room.getId(), updatedShelf.getRoom().getId());
    }

    // ===================== Requirement 11: countBooksInRoom =====================

    @Test
    public void roomWithMultipleShelves_sumsBookCountsAcrossShelves() {
        Room room = createRoom();
        Shelf shelf1 = new Shelf("Fiction", 0, room);
        shelfDao.save(shelf1);
        Shelf shelf2 = new Shelf("NonFiction", 0, room);
        shelfDao.save(shelf2);

        bookDao.save(new Book("B1", "A1", EBookStatus.AVAILABLE, shelf1));
        bookDao.save(new Book("B2", "A2", EBookStatus.AVAILABLE, shelf1));
        bookDao.save(new Book("B3", "A3", EBookStatus.AVAILABLE, shelf2));

        // 2 books on shelf1 + 1 book on shelf2 = 3 total in the room.
        assertEquals(3, service.countBooksInRoom(room.getId()));
    }

    @Test
    public void roomWithNoShelves_returnsZero() {
        Room room = createRoom();
        assertEquals(0, service.countBooksInRoom(room.getId()));
    }

    // ===================== Requirement 12: findRoomWithFewestBooks =====================

    @Test
    public void multipleRooms_returnsRoomWithLowestBookCount() {
        Room roomWithFewerBooks = createRoom();
        Room roomWithMoreBooks = createRoom();

        Shelf shelfA = new Shelf("Fiction", 0, roomWithFewerBooks);
        shelfDao.save(shelfA);
        Shelf shelfB = new Shelf("Fiction", 0, roomWithMoreBooks);
        shelfDao.save(shelfB);

        bookDao.save(new Book("B1", "A1", EBookStatus.AVAILABLE, shelfB));
        bookDao.save(new Book("B2", "A2", EBookStatus.AVAILABLE, shelfB));
        // roomWithFewerBooks stays empty on purpose

        Room result = service.findRoomWithFewestBooks();

        int resultCount = service.countBooksInRoom(result.getId());
        int otherRoomCount = service.countBooksInRoom(roomWithMoreBooks.getId());
        assertTrue(resultCount <= otherRoomCount);
    }

    // ===================== Requirement 13: calculateLateFee =====================

    @Test
    public void returnedOnDueDate_feeIsZero() {
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Borrower borrower = service.borrowBook(user.getId(), createAvailableBook().getId());

        borrower.setReturnDate(borrower.getDueDate()); // returned exactly on time
        borrowerDao.update(borrower);

        assertEquals(0, service.calculateLateFee(borrower.getId()));
    }

    @Test
    public void goldMember_returnedThreeDaysLate_feeIs150() {
        // Gold rate is 50 RWF/day, so 3 days late = 150 RWF.
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Borrower borrower = service.borrowBook(user.getId(), createAvailableBook().getId());

        borrower.setReturnDate(addDays(borrower.getDueDate(), 3));
        borrowerDao.update(borrower);

        assertEquals(150, service.calculateLateFee(borrower.getId()));
    }

    @Test
    public void silverMember_returnedFiveDaysLate_feeIs150() {
        // Silver rate is 30 RWF/day, so 5 days late = 150 RWF.
        User user = createActiveMember(MembershipTierConstants.SILVER);
        Borrower borrower = service.borrowBook(user.getId(), createAvailableBook().getId());

        borrower.setReturnDate(addDays(borrower.getDueDate(), 5));
        borrowerDao.update(borrower);

        assertEquals(150, service.calculateLateFee(borrower.getId()));
    }

    @Test
    public void striverMember_returnedOneDayLate_feeIs10() {
        // Striver rate is 10 RWF/day, so 1 day late = 10 RWF.
        User user = createActiveMember(MembershipTierConstants.STRIVER);
        Borrower borrower = service.borrowBook(user.getId(), createAvailableBook().getId());

        borrower.setReturnDate(addDays(borrower.getDueDate(), 1));
        borrowerDao.update(borrower);

        assertEquals(10, service.calculateLateFee(borrower.getId()));
    }

    @Test
    public void notYetReturned_feeIsComputedAgainstToday() {
        // If the book hasn't been returned yet, the fee should be based on today's date.
        User user = createActiveMember(MembershipTierConstants.GOLD);
        Borrower borrower = service.borrowBook(user.getId(), createAvailableBook().getId());

        // Push the due date into the past so the book is already "late" as of today.
        borrower.setDueDate(addDays(borrower.getDueDate(), -20));
        borrowerDao.update(borrower);

        assertTrue(service.calculateLateFee(borrower.getId()) > 0);
    }

    // ===================== Helper methods =====================
    // These just remove repetitive setup code from the tests above.

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    // Creates a plain user with no membership.
    private User createTestUser(String password) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setUsername("user_" + unique());
        user.setPassword(password);
        user.setRole(ERole.STUDENT);
        userDao.save(user);
        return user;
    }

    // Creates a user and gives them an ACTIVE membership at the given tier.
    private User createActiveMember(String tierName) {
        User user = createTestUser("pass");
        MembershipType type = service.findMembershipTypeByName(tierName);

        Membership membership = service.registerMembership(user.getId(), type.getId());
        membership.setStatus(EMembershipStatus.ACTIVE);
        membershipDao.update(membership);

        return user;
    }

    // Borrows "count" separate available books for the given user.
    private void borrowBooksForUser(User user, int count) {
        for (int i = 0; i < count; i++) {
            Book book = createAvailableBook();
            service.borrowBook(user.getId(), book.getId());
        }
    }

    private Book createAvailableBook() {
        Book book = new Book("Book-" + unique(), "Author", EBookStatus.AVAILABLE, null);
        bookDao.save(book);
        return book;
    }

    private Room createRoom() {
        Room room = new Room("ROOM-" + unique());
        roomDao.save(room);
        return room;
    }

    private Shelf createShelf() {
        Shelf shelf = new Shelf("Fiction", 0, createRoom());
        shelfDao.save(shelf);
        return shelf;
    }
}