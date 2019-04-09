package no.neic.tryggve;

import static org.junit.jupiter.api.Assertions.fail;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    private String userName = "";
    private String password = "";
    private String hostName = "";
    private int port = 1;
    private Optional<String> otc = Optional.of("x");


    @Test
    public void testCreateSftpSession() throws Exception {
        try {
            Utils.createSftpSession(userName, password, hostName, port, otc);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    @Disabled

    public void testOpenSftpChannel() {
        fail("Not yet implemented");
    }

    @Test
    @Disabled

    public void testAssembleFolderInfo() {
        fail("Not yet implemented");
    }

    @Test
    @Disabled

    public void testDeleteFolder() {
        fail("Not yet implemented");
    }

    @Test
    @Disabled

    public void testAssembleFolderContent() {
        fail("Not yet implemented");
    }

    @Test
    @Disabled

    public void testGetSizeOfFolder() {
        fail("Not yet implemented");
    }

}
