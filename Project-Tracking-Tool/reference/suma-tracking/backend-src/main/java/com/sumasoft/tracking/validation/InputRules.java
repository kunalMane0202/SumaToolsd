package com.sumasoft.tracking.validation;

import java.util.regex.Pattern;

/**
 * Step 36: the input rules used by every form (the website checks the same rules first,
 * see frontend/src/validation.js). One place, so the rules are the same everywhere.
 */
public final class InputRules {

    private InputRules() {
    }

    /** name@company.com : no spaces, no double dots, a real domain ending such as .com or .in */
    public static final String EMAIL =
            "^(?!\\.)(?!.*\\.\\.)[A-Za-z0-9._%+-]{1,64}(?<!\\.)@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}$";

    /** Exactly 10 digits, numbers only, not starting with 0. */
    public static final String PHONE = "^[1-9][0-9]{9}$";

    /** Person names: letters, spaces, dot, apostrophe, hyphen. */
    public static final String PERSON_NAME = "^[A-Za-z][A-Za-z .'-]*[A-Za-z.]$";

    /** Company and project names: must contain a letter; letters, digits, spaces and & . , ' ( ) / - */
    public static final String ORG_NAME = "^(?=.*[A-Za-z])[A-Za-z0-9][A-Za-z0-9 &.,'()/-]*$";

    /** Designation, department, role name: starts with a letter. */
    public static final String TITLE = "^[A-Za-z][A-Za-z0-9 &.,'()/-]*$";

    /** Employee code such as 1001 or EMP-1001. */
    public static final String EMP_CODE = "^[A-Za-z0-9-]{1,30}$";

    public static final String EMAIL_MESSAGE = "Enter a valid email address in the format name@company.com";
    public static final String PHONE_MESSAGE = "Contact number must be exactly 10 digits (numbers only, not starting with 0)";
    public static final String PERSON_NAME_MESSAGE = "Name can contain letters, spaces, dot, apostrophe and hyphen only (at least 2 letters)";
    public static final String ORG_NAME_MESSAGE = "Name can contain letters, numbers, spaces and & . , ' ( ) / - only, and must contain a letter";
    public static final String TITLE_MESSAGE = "Must start with a letter and can contain letters, numbers, spaces and & . , ' ( ) / - only";
    public static final String EMP_CODE_MESSAGE = "Employee code can contain letters, numbers and hyphen only (up to 30 characters)";

    private static final Pattern EMAIL_P = Pattern.compile(EMAIL);
    private static final Pattern PHONE_P = Pattern.compile(PHONE);
    private static final Pattern PERSON_P = Pattern.compile(PERSON_NAME);
    private static final Pattern CODE_P = Pattern.compile(EMP_CODE);

    public static boolean isEmail(String v) {
        return v != null && v.length() <= 254 && EMAIL_P.matcher(v.trim()).matches();
    }

    public static boolean isPhone(String v) {
        return v != null && PHONE_P.matcher(v.trim()).matches();
    }

    public static boolean isPersonName(String v) {
        return v != null && v.trim().length() >= 2 && PERSON_P.matcher(v.trim()).matches();
    }

    public static boolean isEmployeeCode(String v) {
        return v != null && CODE_P.matcher(v.trim()).matches();
    }
}