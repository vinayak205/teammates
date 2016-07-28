package teammates.test.cases.storage;

import static teammates.common.util.FieldValidator.COURSE_ID_ERROR_MESSAGE;
import static teammates.common.util.FieldValidator.REASON_INCORRECT_FORMAT;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import teammates.common.datatransfer.StudentAttributes;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Const;
import teammates.common.util.FieldValidator;
import teammates.common.util.StringHelper;
import teammates.storage.api.StudentsDb;
import teammates.storage.entity.Student;
import teammates.test.cases.BaseComponentTestCase;
import teammates.test.driver.AssertHelper;

public class StudentsDbTest extends BaseComponentTestCase {
    
    private StudentsDb studentsDb = new StudentsDb();
    
    /**
     * Allows saving of students as the old entity type.
     * While persisting to database,
     * the student is saved as the old {@code Student} entity type instead of
     * {@code CourseStudent}.
     * 
     */
    private class OldStudentEntityPersistanceAttributes extends StudentAttributes {
        @Override
        public Student toEntity() {
            return new Student(email, name, googleId, comments, course, team, section);
        }

    }
    
    @BeforeClass
    public static void setupClass() {
        printTestClassHeader();
    }
    
    @Test
    public void testTimestamp() throws InvalidParametersException, EntityDoesNotExistException {
        ______TS("success : created");
        
        StudentAttributes s = createNewStudent();
        
        StudentAttributes student = studentsDb.getStudentForEmail(s.course, s.email);
        assertNotNull(student);
        
        // Assert dates are now.
        AssertHelper.assertDateIsNow(student.getCreatedAt());
        AssertHelper.assertDateIsNow(student.getUpdatedAt());
        
        
        ______TS("success : update lastUpdated");
        
        s.name = "new-name";
        studentsDb.updateStudentWithoutSearchability(s.course, s.email, s.name, s.team,
                                                     s.section, s.email, s.googleId, s.comments);
        StudentAttributes updatedStudent = studentsDb.getStudentForGoogleId(s.course, s.googleId);
        
        // Assert lastUpdate has changed, and is now.
        assertFalse(student.getUpdatedAt().equals(updatedStudent.getUpdatedAt()));
        AssertHelper.assertDateIsNow(updatedStudent.getUpdatedAt());
        
        ______TS("success : keep lastUpdated");
        
        s.name = "new-name-2";
        studentsDb.updateStudentWithoutSearchability(s.course, s.email, s.name, s.team,
                                                     s.section, s.email, s.googleId, s.comments, true);
        StudentAttributes updatedStudent2 = studentsDb.getStudentForGoogleId(s.course, s.googleId);
        
        // Assert lastUpdate has NOT changed.
        assertTrue(updatedStudent.getUpdatedAt().equals(updatedStudent2.getUpdatedAt()));
    }
    
    @Test
    public void testCreateStudent() throws Exception {
        
        StudentAttributes s = new StudentAttributes();
        s.name = "valid student";
        s.lastName = "student";
        s.email = "valid-fresh@email.com";
        s.team = "validTeamName";
        s.section = "validSectionName";
        s.comments = "";
        s.googleId = "validGoogleId";

        ______TS("fail : invalid params");
        s.course = "invalid id space";
        try {
            studentsDb.createEntity(s);
            signalFailureToDetectException();
        } catch (InvalidParametersException e) {
            AssertHelper.assertContains(
                    getPopulatedErrorMessage(
                        COURSE_ID_ERROR_MESSAGE, s.course,
                        FieldValidator.COURSE_ID_FIELD_NAME, REASON_INCORRECT_FORMAT,
                        FieldValidator.COURSE_ID_MAX_LENGTH),
                    e.getMessage());
        }
        verifyAbsentInDatastore(s);

        ______TS("success : valid params");
        s.course = "valid-course";
        
        // remove possibly conflicting entity from the database
        studentsDb.deleteStudent(s.course, s.email);
        
        studentsDb.createEntity(s);
        verifyPresentInDatastore(s);
        StudentAttributes retrievedStudent = studentsDb.getStudentForGoogleId(s.course, s.googleId);
        assertTrue(retrievedStudent.isEnrollInfoSameAs(s));
        assertEquals(null, studentsDb.getStudentForGoogleId(s.course + "not existing", s.googleId));
        assertEquals(null, studentsDb.getStudentForGoogleId(s.course, s.googleId + "not existing"));
        assertEquals(null, studentsDb.getStudentForGoogleId(s.course + "not existing", s.googleId + "not existing"));
        
        ______TS("fail : duplicate");
        try {
            studentsDb.createEntity(s);
            signalFailureToDetectException();
        } catch (EntityAlreadyExistsException e) {
            AssertHelper.assertContains(
                    String.format(
                            StudentsDb.ERROR_CREATE_ENTITY_ALREADY_EXISTS,
                            s.getEntityTypeAsString())
                            + s.getIdentificationString(), e.getMessage());
        }

        ______TS("null params check");
        try {
            studentsDb.createEntity(null);
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
    }

    @Test
    public void testGetStudent() throws InvalidParametersException, EntityDoesNotExistException {
        
        StudentAttributes s = createNewStudent();
        s.googleId = "validGoogleId";
        s.team = "validTeam";
        studentsDb.updateStudentWithoutSearchability(s.course, s.email, s.name, s.team, s.section,
                                                     s.email, s.googleId, s.comments);
        
        ______TS("typical success case for getStudentForRegistrationKey: existing student");
        StudentAttributes retrieved = studentsDb.getStudentForEmail(s.course, s.email);
        assertNotNull(retrieved);
        assertNotNull(studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(retrieved.key)));

        assertNull(studentsDb.getStudentForRegistrationKey(StringHelper.encrypt("notExistingKey")));
        
        ______TS("getStudentForRegistrationKey: key matches old student entity without a CourseStudent copy");
        
        StudentAttributes oldStudent = createOldStudentAttributes("getStudent");
        assertTrue("Old student entity should be created", isOldStudentExists(oldStudent));
        assertFalse("New student entity should not be created", isNewStudentExists(oldStudent));
        assertNotNull(studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(oldStudent.key)));
        
        ______TS("getStudentForRegistrationKey works for a student having both Student and CourseStudent");
        
        StudentAttributes updatedStudent =
                copyOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        assertTrue(isOldStudentExists(oldStudent));
        assertTrue(isNewStudentExists(oldStudent));
    
        
        assertNotNull("Old registration key can be used to retrieve student",
                      studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(oldStudent.key)));
        assertNotNull("New registration key can be used to retrieve student",
                      studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(updatedStudent.key)));
        
        ______TS("getStudentForRegistrationKey works for a student only with CourseStudent");
        StudentAttributes movedStudent =
                moveOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        
        assertFalse(isOldStudentExists(movedStudent));
        assertTrue(isNewStudentExists(movedStudent));
        
        assertNotNull("Old registration key can be used to retrieve student",
                studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(oldStudent.key)));
        assertNotNull("New registration key can be used to retrieve student",
                studentsDb.getStudentForRegistrationKey(StringHelper.encrypt(updatedStudent.key)));
        
        ______TS("non existant student case");

        retrieved = studentsDb.getStudentForEmail("any-course-id", "non-existent@email.com");
        assertNull(retrieved);
        
        StudentAttributes s2 = createNewStudent("one.new@gmail.com");
        s2.googleId = "validGoogleId2";
        studentsDb.updateStudentWithoutSearchability(s2.course, s2.email, s2.name, s2.team, s2.section,
                                                     s2.email, s2.googleId, s2.comments);
        studentsDb.deleteStudentsForGoogleIdWithoutDocument(s2.googleId);
        assertNull(studentsDb.getStudentForGoogleId(s2.course, s2.googleId));
        
        s2 = createNewStudent("one.new@gmail.com");
        assertTrue(studentsDb.getUnregisteredStudentsForCourse(s2.course).get(0).isEnrollInfoSameAs(s2));
        
        assertTrue(s.isEnrollInfoSameAs(studentsDb.getStudentsForGoogleId(s.googleId).get(0)));
        assertTrue(studentsDb.getStudentsForCourse(s.course).get(0).isEnrollInfoSameAs(s)
                || studentsDb.getStudentsForCourse(s.course).get(0).isEnrollInfoSameAs(s2));
        assertTrue(studentsDb.getStudentsForTeam(s.team, s.course).get(0).isEnrollInfoSameAs(s));
        
        
        ______TS("null params case");
        try {
            studentsDb.getStudentForEmail(null, "valid@email.com");
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        try {
            studentsDb.getStudentForEmail("any-course-id", null);
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
        studentsDb.deleteStudent(s.course, s.email);
        studentsDb.deleteStudent(s2.course, s2.email);
    }
    
    @Test
    public void testUpdateStudentWithoutDocument() throws InvalidParametersException, EntityDoesNotExistException {
        
        // Create a new student with valid attributes
        StudentAttributes s = createNewStudent();
        studentsDb.updateStudentWithoutSearchability(s.course, s.email, "new-name", "new-team", "new-section",
                                                     "new@email.com", "new.google.id", "lorem ipsum dolor si amet");
        
        ______TS("non-existent case");
        try {
            studentsDb.updateStudentWithoutSearchability("non-existent-course", "non@existent.email", "no-name",
                                                         "non-existent-team", "non-existent-section", "non.existent.ID",
                                                         "blah", "blah");
            signalFailureToDetectException();
        } catch (EntityDoesNotExistException e) {
            assertEquals(StudentsDb.ERROR_UPDATE_NON_EXISTENT_STUDENT + "non-existent-course/non@existent.email",
                         e.getMessage());
        }
        
        // Only check first 2 params (course & email) which are used to identify the student entry.
        // The rest are actually allowed to be null.
        ______TS("null course case");
        try {
            studentsDb.updateStudentWithoutSearchability(null, s.email, "new-name", "new-team", "new-section",
                                                         "new@email.com", "new.google.id", "lorem ipsum dolor si amet");
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
        ______TS("null email case");
        try {
            studentsDb.updateStudentWithoutSearchability(s.course, null, "new-name", "new-team", "new-section",
                                                         "new@email.com", "new.google.id", "lorem ipsum dolor si amet");
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
        ______TS("duplicate email case");
        s = createNewStudent();
        // Create a second student with different email address
        StudentAttributes s2 = createNewStudent("valid2@email.com");
        try {
            studentsDb.updateStudentWithoutSearchability(s.course, s.email, "new-name", "new-team", "new-section",
                                                         s2.email, "new.google.id", "lorem ipsum dolor si amet");
            signalFailureToDetectException();
        } catch (InvalidParametersException e) {
            assertEquals(StudentsDb.ERROR_UPDATE_EMAIL_ALREADY_USED + s2.name + "/" + s2.email,
                         e.getMessage());
        }

        ______TS("typical success case");
        String originalEmail = s.email;
        s.name = "new-name-2";
        s.team = "new-team-2";
        s.email = "new-email-2";
        s.googleId = "new-id-2";
        s.comments = "this are new comments";
        studentsDb.updateStudentWithoutSearchability(s.course, originalEmail, s.name, s.team, s.section,
                                                     s.email, s.googleId, s.comments);
        
        StudentAttributes updatedStudent = studentsDb.getStudentForEmail(s.course, s.email);
        assertTrue(updatedStudent.isEnrollInfoSameAs(s));
        
        
        ______TS("Can update old student entity without a CourseStudent copy");
        
        StudentAttributes oldStudent = createOldStudentAttributes("updateStudent");
        assertTrue("Old student entity should be created", isOldStudentExists(oldStudent));
        assertFalse("New student entity should not be created", isNewStudentExists(oldStudent));
        
        oldStudent.section = "new section";
        studentsDb.updateStudentWithoutSearchability(
                oldStudent.course, oldStudent.email, oldStudent.name, oldStudent.team, oldStudent.section,
                oldStudent.email, oldStudent.googleId, oldStudent.comments);
        StudentAttributes updatedOldStudent = studentsDb.getStudentForEmail(
                                                        oldStudent.course, oldStudent.email);
        assertTrue(updatedOldStudent.isEnrollInfoSameAs(oldStudent));
        
        ______TS("update works for a student having both Student and CourseStudent");
        
        StudentAttributes copiedStudent =
                copyOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        assertTrue(isOldStudentExists(oldStudent));
        assertTrue(isNewStudentExists(oldStudent));
        copiedStudent.name = "new name";
        studentsDb.updateStudentWithoutSearchability(
                    copiedStudent.course, copiedStudent.email, copiedStudent.name, copiedStudent.team,
                    copiedStudent.section, copiedStudent.email, copiedStudent.googleId,
                    copiedStudent.comments);
        StudentAttributes updatedCopiedStudent = studentsDb.getStudentForEmail(
                copiedStudent.course, copiedStudent.email);
        assertTrue(updatedCopiedStudent.isEnrollInfoSameAs(copiedStudent));
        
        
        ______TS("update works for a student only with CourseStudent");
        StudentAttributes movedStudent =
                moveOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        
        assertFalse(isOldStudentExists(movedStudent));
        assertTrue(isNewStudentExists(movedStudent));
        movedStudent.name = "new name 2";
        studentsDb.updateStudentWithoutSearchability(
                movedStudent.course, movedStudent.email, movedStudent.name, movedStudent.team,
                movedStudent.section, movedStudent.email, movedStudent.googleId,
                    movedStudent.comments);
        StudentAttributes updatedMovedStudent = studentsDb.getStudentForEmail(
                movedStudent.course, movedStudent.email);
        assertTrue(updatedMovedStudent.isEnrollInfoSameAs(movedStudent));
        
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDeleteStudent() throws InvalidParametersException, EntityDoesNotExistException {
        StudentAttributes s = createNewStudent();
        s.googleId = "validGoogleId";
        studentsDb.updateStudentWithoutSearchability(s.course, s.email, s.name, s.team, s.section,
                                                     s.email, s.googleId, s.comments);
        // Delete
        studentsDb.deleteStudentWithoutDocument(s.course, s.email);
        
        StudentAttributes deleted = studentsDb.getStudentForEmail(s.course, s.email);
        
        assertNull(deleted);
        studentsDb.deleteStudentsForGoogleIdWithoutDocument(s.googleId);
        assertEquals(null, studentsDb.getStudentForGoogleId(s.course, s.googleId));
        int currentStudentNum = studentsDb.getAllStudents().size();
        s = createNewStudent();
        createNewStudent("secondStudent@mail.com");
        assertEquals(2 + currentStudentNum, studentsDb.getAllStudents().size());
        studentsDb.deleteStudentsForCourseWithoutDocument(s.course);
        assertEquals(currentStudentNum, studentsDb.getAllStudents().size());
        // delete again - should fail silently
        studentsDb.deleteStudentWithoutDocument(s.course, s.email);
        
        // Null params check:
        try {
            studentsDb.deleteStudentWithoutDocument(null, s.email);
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
        try {
            studentsDb.deleteStudentWithoutDocument(s.course, null);
            signalFailureToDetectException();
        } catch (AssertionError ae) {
            assertEquals(Const.StatusCodes.DBLEVEL_NULL_INPUT, ae.getMessage());
        }
        
        studentsDb.deleteStudent(s.course, s.email);

        ______TS("Can delete old student entity without a CourseStudent copy");
        
        StudentAttributes oldStudent = createOldStudentAttributes("deleteStudent");
        assertTrue("Old student entity should be created", isOldStudentExists(oldStudent));
        assertFalse("New student entity should not be created", isNewStudentExists(oldStudent));
        
        studentsDb.deleteStudent(oldStudent.course, oldStudent.email);
        assertNull(studentsDb.getStudentForEmail(oldStudent.course, oldStudent.email));
        assertFalse(isOldStudentExists(oldStudent));
        
        ______TS("delete works for a student having both Student and CourseStudent");
        oldStudent = createOldStudentAttributes("deleteStudent");
        copyOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        
        assertTrue(isOldStudentExists(oldStudent));
        assertTrue(isNewStudentExists(oldStudent));
        
        studentsDb.deleteStudent(oldStudent.course, oldStudent.email);
        assertNull(studentsDb.getStudentForEmail(oldStudent.course, oldStudent.email));
        
        assertFalse(isOldStudentExists(oldStudent));
        assertFalse(isNewStudentExists(oldStudent));
        
        ______TS("delete works for a student only with CourseStudent");
        oldStudent = createOldStudentAttributes("deleteStudent");
        StudentAttributes movedStudent =
                moveOldStudentEntityToCourseStudent(oldStudent.email, oldStudent.course);
        
        assertFalse(isOldStudentExists(movedStudent));
        assertTrue(isNewStudentExists(movedStudent));
        studentsDb.deleteStudent(oldStudent.course, oldStudent.email);
        assertNull(studentsDb.getStudentForEmail(oldStudent.course, oldStudent.email));
        
        assertFalse(isNewStudentExists(oldStudent));

    }
    
    private StudentAttributes createOldStudentAttributes(String testName)
            throws InvalidParametersException {
        StudentAttributes s = new OldStudentEntityPersistanceAttributes();
        s.name = "valid student";
        s.course = "valid-course" + testName;
        s.email = "validOldStudent" + testName + "@email.com";
        s.team = "validTeamName";
        s.section = "validSectionName";
        s.comments = "";
        s.googleId = "";
        
        studentsDb.createEntityWithoutExistenceCheck(s);
        
        return studentsDb.getStudentForEmail(s.course, s.email);
    }
    
    private StudentAttributes copyOldStudentEntityToCourseStudent(String email, String course)
            throws InvalidParametersException {
        StudentAttributes s = studentsDb.getStudentForEmail(course, email);
        
        studentsDb.createEntityWithoutExistenceCheck(s);
        
        return studentsDb.getStudentForEmail(course, email);
    }
    
    private StudentAttributes moveOldStudentEntityToCourseStudent(String email, String course)
            throws InvalidParametersException {
        StudentAttributes s = studentsDb.getStudentForEmail(course, email);
        
        studentsDb.deleteStudent(course, email);
        studentsDb.createEntityWithoutExistenceCheck(s);
        
        return studentsDb.getStudentForEmail(course, email);
    }
    
    @SuppressWarnings("deprecation")
    private boolean isOldStudentExists(StudentAttributes s) {
        boolean isOldStudentExist = false;
        for (StudentAttributes studentsInDb : studentsDb.getAllOldStudents()) {
            if (studentsInDb.getId().equals(s.getId())) {
                isOldStudentExist = true;
            }
        }
        return isOldStudentExist;
    }
    
    @SuppressWarnings("deprecation")
    private boolean isNewStudentExists(StudentAttributes s) {
        boolean isNewStudentExist = false;
        for (StudentAttributes studentsInDb : studentsDb.getAllCourseStudents()) {
            if (studentsInDb.getId().equals(s.getId())) {
                isNewStudentExist = true;
            }
        }
        return isNewStudentExist;
    }
    
    private StudentAttributes createNewStudent() throws InvalidParametersException {
        StudentAttributes s = new StudentAttributes();
        s.name = "valid student";
        s.course = "valid-course";
        s.email = "valid@email.com";
        s.team = "validTeamName";
        s.section = "validSectionName";
        s.comments = "";
        s.googleId = "";
        try {
            studentsDb.createEntity(s);
        } catch (EntityAlreadyExistsException e) {
            // Okay if it's already inside
            ignorePossibleException();
        }
        
        return s;
    }
    
    private StudentAttributes createNewStudent(String email) throws InvalidParametersException {
        StudentAttributes s = new StudentAttributes();
        s.name = "valid student 2";
        s.course = "valid-course";
        s.email = email;
        s.team = "valid team name";
        s.section = "valid section name";
        s.comments = "";
        s.googleId = "";
        try {
            studentsDb.createEntity(s);
        } catch (EntityAlreadyExistsException e) {
            // Okay if it's already inside
            ignorePossibleException();
        }
        
        return s;
    }
}
