package no.ion.utils.io;

import org.junit.jupiter.api.Test;

import static no.ion.utils.io.FilePermissions.Bit.S_IWUSR;
import static no.ion.utils.io.FilePermissions.Bit.S_IXGRP;
import static no.ion.utils.io.FilePermissions.Bit.S_IXOTH;
import static no.ion.utils.io.FilePermissions.Bit.S_IXUSR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilePermissionsTest {
    @Test
    void test() {
        assertEquals("0777", FilePermissions.fromMode(0777).toString());
        assertEquals("0000", FilePermissions.fromMode(0).toString());
        assertEquals("0555", FilePermissions.fromMode(0644)
                                            .clearBits(S_IWUSR)
                                            .setBits(S_IXUSR, S_IXGRP, S_IXOTH)
                                            .toString());
    }
}